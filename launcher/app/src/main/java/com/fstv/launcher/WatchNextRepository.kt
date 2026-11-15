package com.fstv.launcher

import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.concurrent.TimeUnit

/**
 * Picks "Continue Watching"-style entries for the shelf at the top of the home screen. First
 * pass sources them from UsageStatsManager (the permission is granted at setup time), which is
 * always available on Fire OS 6; a later pass can layer TvContract.WatchNextPrograms on top for
 * apps that publish real resume points. Call [load] off the main thread.
 */
class WatchNextRepository(private val context: Context) {
    private val usage = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    fun load(entries: List<AppEntry>, limit: Int = SHELF_LIMIT): List<AppEntry> {
        if (entries.isEmpty()) return emptyList()
        val byPkg = HashMap<String, AppEntry>(entries.size)
        for (entry in entries) {
            if (entry.kind != Kind.APP) continue
            val pkg = entry.intent?.component?.packageName ?: continue
            byPkg.putIfAbsent(pkg, entry)
        }
        if (byPkg.isEmpty()) return emptyList()

        val ranking = recentPackages() ?: return emptyList()
        val own = context.packageName
        val seen = HashSet<String>()
        val out = ArrayList<AppEntry>(limit)
        for (pkg in ranking) {
            if (pkg == own) continue
            if (!seen.add(pkg)) continue
            val entry = byPkg[pkg] ?: continue
            out += entry
            if (out.size >= limit) break
        }
        return out
    }

    private fun recentPackages(): List<String>? {
        val manager = usage ?: return null
        val now = System.currentTimeMillis()
        val since = now - LOOKBACK_MS
        return try {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, since, now)
                ?.asSequence()
                ?.filter { it.lastTimeUsed > since && it.totalTimeInForeground > MIN_FOREGROUND_MS }
                ?.sortedByDescending { it.lastTimeUsed }
                ?.map { it.packageName }
                ?.toList()
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        /** Keep it short so the row stays a "shelf" rather than a second grid. */
        const val SHELF_LIMIT = 3

        private val LOOKBACK_MS = TimeUnit.DAYS.toMillis(7)
        // Filter out apps that just flashed foreground (notification trampolines, etc.) by
        // requiring at least a few seconds of actual use.
        private const val MIN_FOREGROUND_MS = 3_000L
    }
}
