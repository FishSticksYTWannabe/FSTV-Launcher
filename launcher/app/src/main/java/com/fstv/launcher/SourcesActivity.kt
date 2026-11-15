package com.fstv.launcher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Full-screen source picker that stands in for the stock Fire TV "Inputs" app. Enumerates every
 * switchable input the TV framework exposes (HDMI 1/2/3, composite, component, etc.) and lets the
 * user pick one with the D-pad. On Select it fires the standard Android TV passthrough URI; if
 * the TV doesn't resolve that (older Fire OS builds route HDMI switching through their own
 * droidlogic.tvsource app), it falls back to launching that app's picker.
 */
class SourcesActivity : Activity() {

    private lateinit var ui: Ui
    private lateinit var root: FrameLayout
    private lateinit var grid: FrameLayout
    private val tiles = ArrayList<SourceTile>()
    private var focusIndex = 0
    private var cols = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val size = Point().also { windowManager.defaultDisplay.getRealSize(it) }
        ui = Ui(size.x, size.y, 3)

        root = FrameLayout(this).apply {
            setBackgroundColor(0xFF08090C.toInt())
            clipChildren = false
        }

        val title = TextView(this).apply {
            text = "Sources"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, ui.px(72f))
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            letterSpacing = 0.04f
            setShadowLayer(ui.px(20f), 0f, ui.px(3f), 0x80000000.toInt())
        }
        val titleParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        titleParams.topMargin = ui.px(80f).toInt()
        titleParams.leftMargin = ui.left.toInt()
        root.addView(title, titleParams)

        val subtitle = TextView(this).apply {
            text = "Pick an input to switch the TV to."
            setTextColor(0x99FFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_PX, ui.px(30f))
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        val subParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        subParams.topMargin = ui.px(176f).toInt()
        subParams.leftMargin = ui.left.toInt()
        root.addView(subtitle, subParams)

        grid = FrameLayout(this).apply { clipChildren = false }
        root.addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val sources = enumerateSources()
        if (sources.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No TV inputs are available on this device."
                setTextColor(0xCCFFFFFF.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_PX, ui.px(34f))
                gravity = Gravity.CENTER
            }
            val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.topMargin = ui.px(430f).toInt()
            root.addView(empty, params)
        } else {
            layoutTiles(sources)
        }

        setContentView(root)
    }

    private fun enumerateSources(): List<Source> {
        val tvm = getSystemService(Context.TV_INPUT_SERVICE) as? TvInputManager ?: return emptyList()
        val raw = try {
            tvm.tvInputList
        } catch (_: Exception) {
            return emptyList()
        }
        return raw.asSequence()
            .filter { isSwitchable(it.type) }
            .sortedWith(compareBy({ typeOrder(it.type) }, { it.loadLabel(this).toString() }))
            .map { info ->
                Source(
                    label = info.loadLabel(this).toString(),
                    type = info.type,
                    inputId = info.id,
                )
            }
            .toList()
    }

    private fun layoutTiles(sources: List<Source>) {
        tiles.clear()
        grid.removeAllViews()
        val tileW = ui.px(380f)
        val tileH = ui.px(214f)
        val gap = ui.px(32f)
        cols = minOf(MAX_COLS, sources.size)
        val totalW = tileW * cols + gap * (cols - 1)
        val startX = (ui.screenW - totalW) / 2f
        val startY = ui.px(300f)
        sources.forEachIndexed { i, src ->
            val row = i / cols
            val col = i % cols
            val tile = SourceTile(this, ui, src)
            val x = startX + col * (tileW + gap)
            val y = startY + row * (tileH + gap)
            val params = FrameLayout.LayoutParams(tileW.toInt(), tileH.toInt())
            params.leftMargin = x.toInt()
            params.topMargin = y.toInt()
            grid.addView(tile, params)
            tiles.add(tile)
        }
        tiles.firstOrNull()?.setFocused(true)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (tiles.isEmpty()) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                finish()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> { if (event.action == KeyEvent.ACTION_DOWN) move(-1, 0); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { if (event.action == KeyEvent.ACTION_DOWN) move(1, 0); return true }
            KeyEvent.KEYCODE_DPAD_UP -> { if (event.action == KeyEvent.ACTION_DOWN) move(0, -1); return true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { if (event.action == KeyEvent.ACTION_DOWN) move(0, 1); return true }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A -> {
                if (event.action == KeyEvent.ACTION_UP) switchToFocused()
                return true
            }
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_DEL -> {
                if (event.action == KeyEvent.ACTION_UP) finish()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun move(dx: Int, dy: Int) {
        val count = tiles.size
        val row = focusIndex / cols
        val col = focusIndex % cols
        val newCol = col + dx
        val newRow = row + dy
        if (newCol < 0 || newCol >= cols) return
        if (newRow < 0) return
        val target = newRow * cols + newCol
        if (target < 0 || target >= count) return
        tiles[focusIndex].setFocused(false)
        focusIndex = target
        tiles[focusIndex].setFocused(true)
    }

    private fun switchToFocused() {
        val src = tiles.getOrNull(focusIndex)?.src ?: return
        switchTo(src)
    }

    private fun switchTo(src: Source) {
        val attempts = ArrayList<Intent>(2)
        try {
            val uri = TvContract.buildChannelUriForPassthroughInput(src.inputId)
            attempts += Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } catch (_: Exception) {
            // Build URI failed — skip to the fallback.
        }
        // Fallback: older Fire TV builds route HDMI switching through the droidlogic Inputs app.
        // It opens its own picker, which is a worse UX, but at least the switch is reachable.
        val pm = packageManager
        for (pkg in STOCK_PICKERS) {
            val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                attempts += intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                break
            }
        }
        for (intent in attempts) {
            try {
                startActivity(intent)
                finish()
                return
            } catch (_: Exception) {
                // Try the next strategy.
            }
        }
    }

    data class Source(val label: String, val type: Int, val inputId: String)

    companion object {
        private const val MAX_COLS = 3
        private val STOCK_PICKERS = listOf(
            "com.droidlogic.tvsource",       // Amlogic (Toshiba Fire TV Edition)
            "com.mediatek.wwtv.tvcenter",
            "com.mediatek.tvinput",
            "com.google.android.tv",
            "com.android.tv",
        )

        /** True for TvInputInfo types that correspond to a physical source we can switch to. */
        fun isSwitchable(type: Int): Boolean = when (type) {
            TvInputInfo.TYPE_HDMI,
            TvInputInfo.TYPE_COMPOSITE,
            TvInputInfo.TYPE_COMPONENT,
            TvInputInfo.TYPE_SVIDEO,
            TvInputInfo.TYPE_VGA,
            TvInputInfo.TYPE_SCART,
            TvInputInfo.TYPE_DVI,
            TvInputInfo.TYPE_DISPLAY_PORT -> true
            else -> false
        }

        /** HDMI first, then the analog inputs; keeps the common cases at the top of the grid. */
        private fun typeOrder(type: Int): Int = when (type) {
            TvInputInfo.TYPE_HDMI -> 0
            TvInputInfo.TYPE_DISPLAY_PORT, TvInputInfo.TYPE_DVI -> 1
            TvInputInfo.TYPE_COMPONENT, TvInputInfo.TYPE_VGA -> 2
            TvInputInfo.TYPE_COMPOSITE, TvInputInfo.TYPE_SVIDEO, TvInputInfo.TYPE_SCART -> 3
            else -> 4
        }

        /** A tvOS-ish palette: HDMI is cool blue; the analog inputs get warm/green accents. */
        fun colorsFor(type: Int): IntArray = when (type) {
            TvInputInfo.TYPE_HDMI -> intArrayOf(0xFF3A5BAE.toInt(), 0xFF162454.toInt())
            TvInputInfo.TYPE_DISPLAY_PORT,
            TvInputInfo.TYPE_DVI -> intArrayOf(0xFF4FA3B5.toInt(), 0xFF1E4E57.toInt())
            TvInputInfo.TYPE_COMPONENT -> intArrayOf(0xFF4E8C4A.toInt(), 0xFF1F3E1C.toInt())
            TvInputInfo.TYPE_VGA -> intArrayOf(0xFF5E6CA0.toInt(), 0xFF24294A.toInt())
            TvInputInfo.TYPE_COMPOSITE,
            TvInputInfo.TYPE_SVIDEO,
            TvInputInfo.TYPE_SCART -> intArrayOf(0xFFB06433.toInt(), 0xFF4E2812.toInt())
            else -> intArrayOf(0xFF6A6A72.toInt(), 0xFF2A2A2E.toInt())
        }

        /** Short glyph in the tile: "HDMI", "AV", etc. The full label sits under it. */
        fun glyphFor(type: Int): String = when (type) {
            TvInputInfo.TYPE_HDMI -> "HDMI"
            TvInputInfo.TYPE_DISPLAY_PORT -> "DP"
            TvInputInfo.TYPE_DVI -> "DVI"
            TvInputInfo.TYPE_COMPONENT -> "YPbPr"
            TvInputInfo.TYPE_VGA -> "VGA"
            TvInputInfo.TYPE_COMPOSITE -> "AV"
            TvInputInfo.TYPE_SVIDEO -> "S-Video"
            TvInputInfo.TYPE_SCART -> "SCART"
            else -> "IN"
        }
    }
}

/** One tile in the Sources grid: a gradient card with a glyph and the input's label. */
private class SourceTile(context: Context, private val ui: Ui, val src: SourcesActivity.Source) : FrameLayout(context) {
    private val glyph = TextView(context).apply {
        text = SourcesActivity.glyphFor(src.type)
        setTextColor(0xE6FFFFFF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_PX, ui.px(30f))
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        letterSpacing = 0.12f
    }
    private val label = TextView(context).apply {
        text = src.label
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, ui.px(44f))
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        setShadowLayer(ui.px(14f), 0f, ui.px(2f), 0x99000000.toInt())
    }
    private val focusRing: View = View(context).apply {
        val ring = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = ui.px(20f)
            setStroke(ui.px(3f).toInt(), 0xE6FFFFFF.toInt())
        }
        background = ring
        alpha = 0f
    }

    init {
        clipChildren = false
        val colors = SourcesActivity.colorsFor(src.type)
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
            cornerRadius = ui.px(20f)
        }
        elevation = ui.px(6f)

        val glyphParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        glyphParams.leftMargin = ui.px(24f).toInt()
        glyphParams.topMargin = ui.px(22f).toInt()
        addView(glyph, glyphParams)

        val labelParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        labelParams.leftMargin = ui.px(24f).toInt()
        labelParams.bottomMargin = ui.px(22f).toInt()
        labelParams.gravity = Gravity.BOTTOM or Gravity.START
        addView(label, labelParams)

        addView(focusRing, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    fun setFocused(on: Boolean) {
        animate().cancel()
        animate()
            .scaleX(if (on) 1.08f else 1f)
            .scaleY(if (on) 1.08f else 1f)
            .translationZ(if (on) ui.px(24f) else 0f)
            .setDuration(if (on) 260L else 220L)
            .setInterpolator(DecelerateInterpolator(2.2f))
            .start()
        focusRing.animate().cancel()
        focusRing.animate().alpha(if (on) 1f else 0f).setDuration(220L).start()
    }
}
