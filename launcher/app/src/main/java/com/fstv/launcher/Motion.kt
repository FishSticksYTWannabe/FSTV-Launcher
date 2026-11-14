package com.fstv.launcher

import android.view.Choreographer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min

/** Layout values, authored against a 1920x1080 screen and scaled to the real one. */
class Ui(val screenW: Int, val screenH: Int, cols: Int = 5) {
    private val scale = screenW / 1920f
    fun px(value: Float): Float = value * scale

    // The grid's width fills the same horizontal band regardless of column count; tileW shrinks
    // to fit. 5 columns are the roomy default; 6 or 7 pack more without a scroll.
    val columns = cols.coerceIn(4, 8)
    val gapX = px(40f)
    private val sideMargin = px(100f)
    val tileW = (screenW - 2 * sideMargin - gapX * (columns - 1)) / columns
    // Keep the ~16:9 banner aspect the renderer expects.
    val tileH = tileW * (176f / 312f)
    val rowPitch = tileH + px(106f)
    val left = (screenW - (tileW * columns + gapX * (columns - 1))) / 2f
    // Where the grid sits, measured from the top. With the big hero image removed, the grid is
    // now the only content - tvOS leaves generous empty space above it for the backdrop to breathe.
    val firstRowY = px(420f)
    val focusedRowY = px(300f)
    val pad = px(72f)
    val radius = px(20f)
    val labelH = px(56f)
    val focusGrow = 0.16f

    val cellW = (tileW + 2 * pad).toInt()
    val cardH = (tileH + 2 * pad).toInt()
    val cellH = cardH + labelH.toInt()
    val artW = ceil(tileW * (1 + focusGrow)).toInt()
    val artH = ceil(tileH * (1 + focusGrow)).toInt()
    val heroW = px(720f).toInt()
    val heroH = px(405f).toInt()
    // On-screen size and position of the big hero image at the top of the home screen.
    val heroArtW = px(960f)
    val heroArtH = px(372f)
    val heroArtTop = px(150f)

    fun slotX(index: Int): Float = left + (index % columns) * (tileW + gapX) - pad
    fun slotY(index: Int): Float = firstRowY + (index / columns) * rowPitch - pad
    fun scrollForRow(row: Int): Float =
        if (row == 0) 0f else firstRowY + row * rowPitch - focusedRowY
}

/** A damped spring. Underdamped values give the small overshoot that makes motion feel physical. */
class Spring(
    private val stiffness: Float,
    private val damping: Float,
    initial: Float = 0f,
    private val epsilon: Float = 0.002f,
) {
    var value = initial
        private set
    var velocity = 0f
    var target = initial

    private val atRest: Boolean
        get() = abs(velocity) < epsilon * 4f && abs(value - target) < epsilon

    fun snap(to: Float) {
        value = to
        target = to
        velocity = 0f
    }

    /** Advances the spring and returns true while it is still moving. */
    fun step(dt: Float): Boolean {
        if (atRest) {
            value = target
            velocity = 0f
            return false
        }
        var left = dt
        while (left > 0f) {
            val h = min(left, MAX_STEP)
            velocity += (-stiffness * (value - target) - damping * velocity) * h
            value += velocity * h
            left -= h
        }
        return true
    }

    private companion object {
        const val MAX_STEP = 1f / 240f
    }
}

/** Runs one callback per display frame, and only while that callback reports work to do. */
class Ticker(private val onFrame: (dt: Float, time: Float) -> Boolean) : Choreographer.FrameCallback {
    private val choreographer = Choreographer.getInstance()
    private var enabled = false
    private var scheduled = false
    private var lastNanos = 0L
    private var time = 0f

    fun start() {
        enabled = true
        wake()
    }

    fun stop() {
        enabled = false
        if (scheduled) {
            choreographer.removeFrameCallback(this)
            scheduled = false
        }
        lastNanos = 0L
    }

    fun wake() {
        if (enabled && !scheduled) {
            scheduled = true
            choreographer.postFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        scheduled = false
        if (!enabled) return
        val dt = if (lastNanos == 0L) FIRST_FRAME else min((frameTimeNanos - lastNanos) / 1e9f, MAX_FRAME)
        lastNanos = frameTimeNanos
        time += dt
        if (onFrame(dt, time)) wake() else lastNanos = 0L
    }

    private companion object {
        const val FIRST_FRAME = 1f / 60f
        const val MAX_FRAME = 0.05f
    }
}
