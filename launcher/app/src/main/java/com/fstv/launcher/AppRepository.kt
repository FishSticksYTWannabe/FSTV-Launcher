package com.fstv.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.util.DisplayMetrics

enum class Kind { APP, SETTINGS, MORE }

class AppEntry(
    val key: String,
    val label: String,
    val intent: Intent?,
    val kind: Kind,
    val info: ActivityInfo?,
    val glyph: String? = null,
    val iconRes: Int = 0,
    val designed: Boolean = false,
) {
    lateinit var tile: Bitmap
    lateinit var backdrop: Bitmap
}

/** Finds launchable apps and renders their artwork. Call [load] and [heroArt] off the main thread. */
class AppRepository(private val context: Context, private val ui: Ui) {
    private val pm = context.packageManager
    private val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)

    private class Found(val info: ActivityInfo, val category: String, val isTvApp: Boolean)

    fun load(showAll: Boolean): List<AppEntry> {
        val found = LinkedHashMap<String, Found>()
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER, isTvApp = true, found)
        collect(Intent.CATEGORY_LAUNCHER, isTvApp = false, found)

        val apps = found.values
            .filter { showAll || isVisibleByDefault(it) }
            .map(::toEntry)
            .sortedWith(compareBy<AppEntry>({ priorityOf(it) }, { it.label.lowercase() }))

        val ordered = applySavedOrder(apps + specialTiles(showAll))
        ordered.forEach(::render)
        return ordered
    }

    fun saveOrder(keys: List<String>) {
        prefs.edit().putString(KEY_ORDER, keys.joinToString(",")).apply()
    }

    fun heroArt(entry: AppEntry): Bitmap {
        val art = artFor(entry, DisplayMetrics.DENSITY_XXHIGH, DisplayMetrics.DENSITY_XXXHIGH)
        val base = Art.base(art?.drawable, art?.isBanner == true, ui.heroW, ui.heroH, entry.glyph)
        val rounded = Art.rounded(base, ui.px(28f))
        base.recycle()
        return rounded
    }

    private fun collect(category: String, isTvApp: Boolean, into: MutableMap<String, Found>) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
        for (resolved in pm.queryIntentActivities(intent, 0)) {
            val info = resolved.activityInfo ?: continue
            val pkg = info.packageName
            if (pkg == context.packageName || pkg == STOCK_LAUNCHER) continue
            if (pkg !in into) into[pkg] = Found(info, category, isTvApp)
        }
    }

    private fun isVisibleByDefault(found: Found): Boolean {
        val pkg = found.info.packageName
        if (pkg in HIDDEN_BY_DEFAULT) return false
        if (found.isTvApp || pkg in EXTRA_VISIBLE) return true
        return found.info.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0
    }

    private fun toEntry(found: Found): AppEntry {
        val info = found.info
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(found.category)
            .setComponent(ComponentName(info.packageName, info.name))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val label = info.loadLabel(pm).toString()
        // Give the Amazon Appstore our own clean, Apple-style tile instead of its stock icon.
        if (info.packageName == APPSTORE_PKG) {
            return AppEntry(info.packageName, label, intent, Kind.APP, null,
                iconRes = R.drawable.ic_appstore, designed = true)
        }
        return AppEntry(info.packageName, label, intent, Kind.APP, info)
    }

    private fun specialTiles(showAll: Boolean): List<AppEntry> {
        val out = ArrayList<AppEntry>()

        val settings = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (pm.resolveActivity(settings, 0) != null) {
            out += AppEntry(KEY_SETTINGS, "Settings", settings, Kind.SETTINGS, null, iconRes = R.drawable.ic_settings, designed = true)
        }

        // A way back to Amazon's home screen, so this launcher can never lock anyone out.
        val probe = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(STOCK_LAUNCHER)
        pm.resolveActivity(probe, 0)?.activityInfo?.let {
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(it.packageName, it.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            out += AppEntry(KEY_STOCK, "Fire TV Home", intent, Kind.APP, null, iconRes = R.drawable.ic_stock_home)
        }

        liveTvEntry()?.let { out += it }

        out += AppEntry(KEY_MORE, if (showAll) "Fewer Apps" else "More Apps", null, Kind.MORE, null, glyph = "...")
        return out
    }

    /**
     * A "Live TV" tile that opens the device's TV-input / source app, so inputs can be switched
     * from the home screen without the physical button. Tries several brands, so it is not
     * Fire-TV-only; absent on devices with no such app (e.g. a plain streaming stick).
     */
    private fun liveTvEntry(): AppEntry? {
        for (pkg in LIVE_TV_PACKAGES) {
            val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return AppEntry(KEY_LIVETV, "Inputs", intent, Kind.APP, null, iconRes = R.drawable.ic_livetv)
            }
        }
        return null
    }

    private fun applySavedOrder(list: List<AppEntry>): List<AppEntry> {
        val saved = prefs.getString(KEY_ORDER, null)?.split(',') ?: return list
        val rank = saved.withIndex().associate { it.value to it.index }
        return list.withIndex().sortedBy { rank[it.value.key] ?: (saved.size + it.index) }.map { it.value }
    }

    private fun render(entry: AppEntry) {
        val art = artFor(entry, DisplayMetrics.DENSITY_XHIGH, DisplayMetrics.DENSITY_XXHIGH)
        val base = Art.base(art?.drawable, art?.isBanner == true, ui.artW, ui.artH, entry.glyph ?: entry.label.take(1))
        entry.tile = Art.rounded(base, ui.radius * ui.artW / ui.tileW)
        entry.backdrop = Art.backdrop(base)
        base.recycle()
    }

    private class Source(val drawable: Drawable?, val isBanner: Boolean)

    private fun artFor(entry: AppEntry, bannerDensity: Int, iconDensity: Int): Source? = when {
        // Designed tiles are full-bleed (their own gradient + glyph), so composite them like a banner.
        entry.iconRes != 0 -> Source(context.getDrawable(entry.iconRes), entry.designed)
        entry.info != null -> source(entry.info, bannerDensity, iconDensity)
        else -> null
    }

    /** Prefers the wide TV banner and falls back to the icon. */
    private fun source(info: ActivityInfo, bannerDensity: Int, iconDensity: Int): Source {
        val app = info.applicationInfo
        val resources = try {
            pm.getResourcesForApplication(app)
        } catch (_: Exception) {
            return Source(fallbackIcon(info), false)
        }
        val bannerId = if (info.banner != 0) info.banner else app.banner
        if (bannerId != 0) {
            try {
                return Source(resources.getDrawableForDensity(bannerId, bannerDensity, null), true)
            } catch (_: Exception) {
                // Some apps declare a banner they do not ship. Use the icon.
            }
        }
        val iconId = if (info.icon != 0) info.icon else app.icon
        if (iconId != 0) {
            try {
                return Source(resources.getDrawableForDensity(iconId, iconDensity, null), false)
            } catch (_: Exception) {
                // Fall through to the system's own lookup.
            }
        }
        return Source(fallbackIcon(info), false)
    }

    private fun fallbackIcon(info: ActivityInfo): Drawable? = try {
        info.loadIcon(pm)
    } catch (_: Exception) {
        null
    }

    private fun priorityOf(entry: AppEntry): Int {
        val index = PRIORITY.indexOf(entry.key)
        return if (index >= 0) index else PRIORITY.size
    }

    companion object {
        const val STOCK_LAUNCHER = "com.amazon.tv.launcher"
        private const val APPSTORE_PKG = "com.amazon.venezia"
        private const val KEY_ORDER = "order"
        private const val KEY_SETTINGS = "@settings"
        private const val KEY_STOCK = "@stock"
        private const val KEY_LIVETV = "@livetv"
        private const val KEY_MORE = "@more"

        // TV-input / source apps across brands, in order of preference.
        private val LIVE_TV_PACKAGES = listOf(
            "com.droidlogic.tvsource",       // Amlogic (this Fire TV Edition)
            "com.mediatek.wwtv.tvcenter",    // MediaTek TVs
            "com.mediatek.tvinput",
            "com.google.android.tv",         // Android TV Live Channels
            "com.android.tv",
        )

        private val PRIORITY = listOf(
            "com.netflix.ninja",
            "com.amazon.firetv.youtube",
            "com.amazon.firebat",
            "com.disney.disneyplus",
            "in.startv.hotstar",
        )
        private val EXTRA_VISIBLE = setOf("com.amazon.cloud9", "com.amazon.venezia")
        private val HIDDEN_BY_DEFAULT = setOf(
            "com.amazon.shoptv.firetv.client",
            "com.droidlogic.tvsource",   // shown via the dedicated "Live TV" tile instead
        )
    }
}
