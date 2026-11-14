package com.fstv.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.media.MediaPlayer
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** The tile image, plus its shadow and the moving highlight that sells the glass look. */
@SuppressLint("ViewConstructor")
class TileCard(context: Context, private val ui: Ui, private val art: Bitmap, private val shadow: Bitmap) : View(context) {
    private val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shadowPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { color = Color.BLACK }
    private val glareMatrix = Matrix()
    private val glareShader = RadialGradient(
        0f, 0f, ui.tileW * 0.85f,
        intArrayOf(0x70FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP,
    )
    private val glarePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = glareShader
        isDither = true
    }

    private val cardRect = RectF(ui.pad, ui.pad, ui.pad + ui.tileW, ui.pad + ui.tileH)
    private val artRect: RectF
    private val shadowRect = RectF()

    // Diagonal specular highlight that traverses the tile when focus arrives (tvOS signature).
    // The gradient is authored once; we translate + rotate it on draw so there is nothing to
    // allocate per frame.
    private val shineBand = ui.tileW * 0.22f
    private val shineShader = LinearGradient(
        -shineBand, 0f, shineBand, 0f,
        intArrayOf(0x00FFFFFF, 0x66FFFFFF, 0xAAFFFFFF.toInt(), 0x66FFFFFF, 0x00FFFFFF),
        floatArrayOf(0f, 0.35f, 0.5f, 0.65f, 1f),
        Shader.TileMode.CLAMP,
    )
    private val shineMatrix = Matrix()
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = shineShader
        isDither = true
    }

    private var shadowAlpha = 0
    private var glareAlpha = 0
    private var reflectAlpha = 0
    private var lightX = 0f
    private var lightY = 0f
    private var shadowDrop = 0f
    private var gray = 0f
    private var shineProgress = -1f    // -1 = idle, 0..1 = sweeping, >1 = done

    // tvOS-style mirror reflection under the focused tile. The paint draws the flipped art into
    // a translucent layer; the mask paint then composites a vertical fade on top so the bottom of
    // the reflection softens away. Shaders are built once (lazy) in onDraw when the bounds exist.
    private val reflectPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val reflectMaskPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val reflectLayerPaint = Paint()
    private var reflectMaskShader: LinearGradient? = null
    private val reflectRect = RectF()

    init {
        val inset = Art.INSET * ui.tileW / ui.artW
        artRect = RectF(cardRect.left - inset, cardRect.top - inset, cardRect.right + inset, cardRect.bottom + inset)
    }

    /** Updates the shadow and highlight, and redraws only if they visibly changed. */
    fun setLook(focus: Float, tiltX: Float, tiltY: Float) {
        val f = focus.coerceIn(0f, 1f)
        val nextShadow = (f * SHADOW_MAX).toInt()
        val nextGlare = (f * 255f).toInt()
        val nextReflect = (f * REFLECT_MAX).toInt()
        val nextX = cardRect.centerX() - tiltX * ui.tileW * 0.7f
        val nextY = cardRect.top + ui.tileH * 0.1f + tiltY * ui.tileH * 0.9f
        val nextDrop = ui.px(10f) + f * ui.px(26f)
        val moved = abs(nextX - lightX) > 0.5f || abs(nextY - lightY) > 0.5f
        if (nextShadow == shadowAlpha && nextGlare == glareAlpha && nextReflect == reflectAlpha && !moved) return
        shadowAlpha = nextShadow
        glareAlpha = nextGlare
        reflectAlpha = nextReflect
        lightX = nextX
        lightY = nextY
        shadowDrop = nextDrop
        invalidate()
    }

    /**
     * Drives the shine sweep. Progress 0 is the band off-screen left, 1 is off-screen right.
     * Pass -1 to clear (no shine drawn). The owning TileCell advances this each frame.
     */
    fun setShine(progress: Float) {
        if (progress == shineProgress) return
        shineProgress = progress
        invalidate()
    }

    /** Drains color from the tile and darkens it, from 0 (normal) to 1 (fully idle). */
    fun setGray(amount: Float) {
        val next = amount.coerceIn(0f, 1f)
        if (next == gray || (abs(next - gray) < 0.005f && next != 0f && next != 1f)) return
        gray = next
        artPaint.colorFilter = if (next == 0f) null else grayFilter(next)
        invalidate()
    }

    private fun grayFilter(amount: Float): ColorMatrixColorFilter {
        val matrix = ColorMatrix().apply { setSaturation(1f - GRAY_DESATURATE * amount) }
        val light = 1f - GRAY_DARKEN * amount
        matrix.postConcat(ColorMatrix().apply { setScale(light, light, light, 1f) })
        return ColorMatrixColorFilter(matrix)
    }

    override fun onDraw(canvas: Canvas) {
        if (shadowAlpha > 0) {
            val blur = Art.shadowBlur(ui)
            shadowRect.set(
                cardRect.left - blur, cardRect.top - blur + shadowDrop,
                cardRect.right + blur, cardRect.bottom + blur + shadowDrop,
            )
            shadowPaint.alpha = shadowAlpha
            canvas.drawBitmap(shadow, null, shadowRect, shadowPaint)
        }
        // Reflection: drawn BEFORE the tile art so it sits beneath the card visually. The flipped
        // bitmap is rendered into an off-screen layer whose top is the card's bottom; a vertical
        // alpha gradient then softens the lower edge via DST_IN.
        if (reflectAlpha > 0) {
            val reflH = ui.pad * 0.8f
            reflectRect.set(cardRect.left, cardRect.bottom, cardRect.right, cardRect.bottom + reflH)
            if (reflectMaskShader == null) {
                reflectMaskShader = LinearGradient(
                    0f, reflectRect.top, 0f, reflectRect.bottom,
                    0xFFFFFFFF.toInt(), 0x00FFFFFF,
                    Shader.TileMode.CLAMP,
                )
                reflectMaskPaint.shader = reflectMaskShader
            }
            reflectLayerPaint.alpha = reflectAlpha
            val saved = canvas.saveLayer(reflectRect, reflectLayerPaint)
            canvas.clipRect(reflectRect)
            canvas.save()
            // Flip vertically across y = cardRect.bottom, so the bottom edge of the art stays put
            // and the rest of the bitmap extends downward in mirror form.
            canvas.translate(0f, 2f * cardRect.bottom)
            canvas.scale(1f, -1f)
            canvas.drawBitmap(art, null, artRect, reflectPaint)
            canvas.restore()
            canvas.drawRect(reflectRect, reflectMaskPaint)
            canvas.restoreToCount(saved)
        }
        canvas.drawBitmap(art, null, artRect, artPaint)
        if (glareAlpha > 0) {
            glareMatrix.setTranslate(lightX, lightY)
            glareShader.setLocalMatrix(glareMatrix)
            glarePaint.alpha = glareAlpha
            canvas.drawRoundRect(cardRect, ui.radius, ui.radius, glarePaint)
        }
        if (shineProgress in 0f..1f) {
            // Sweep a bright diagonal band from upper-left across to lower-right. Fade both ends
            // of the pass so it doesn't pop in or out at the edges.
            val travel = ui.tileW + shineBand * 2f
            val tx = cardRect.left - shineBand + travel * shineProgress
            val ty = cardRect.top + ui.tileH * shineProgress   // small vertical slide = diagonal
            val envelope = 1f - abs(shineProgress - 0.5f) * 2f
            shineMatrix.setRotate(22f)
            shineMatrix.postTranslate(tx, ty)
            shineShader.setLocalMatrix(shineMatrix)
            shinePaint.alpha = (255f * envelope).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(cardRect, ui.radius, ui.radius, shinePaint)
        }
    }

    override fun hasOverlappingRendering(): Boolean = false

    private companion object {
        const val SHADOW_MAX = 215f
        // Peak reflection alpha when the tile is fully focused. The mask gradient softens the
        // lower edge on top of this, so what actually hits the screen is well under this value.
        const val REFLECT_MAX = 120f
        // Light touch only: the real darkening of idle comes from a full-screen dim veil.
        const val GRAY_DESATURATE = 0.28f
        const val GRAY_DARKEN = 0.05f
    }
}

/** One app in the grid. It owns the springs for every part of the tile's motion. */
@SuppressLint("ViewConstructor")
class TileCell(context: Context, val entry: AppEntry, private val ui: Ui, shadow: Bitmap, private val phase: Float) : FrameLayout(context) {
    val card = TileCard(context, ui, entry.tile, shadow)
    private val label = TextView(context).apply {
        text = entry.label
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, ui.px(30f))
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        alpha = 0f
    }

    private val focus = Spring(260f, 20f)
    private val press = Spring(620f, 42f)
    private val tiltX = Spring(130f, 13f, epsilon = 0.003f)
    private val tiltY = Spring(130f, 13f, epsilon = 0.003f)
    private val nudgeX = Spring(380f, 18f, epsilon = 0.4f)
    private val nudgeY = Spring(380f, 18f, epsilon = 0.4f)
    private val posX = Spring(220f, 26f, epsilon = 0.4f)
    private val posY = Spring(220f, 26f, epsilon = 0.4f)
    private val appear = Spring(130f, 15f, initial = 1f)
    // Matches the launcher's idle spring so tiles dim in sync with everything else.
    private val dim = Spring(24f, 10f)
    private var appearDelay = 0f
    private var focusedTile = false
    private var shineElapsed = -1f    // seconds since shine began; -1 = not shining

    /** How focused this tile is, 0..1, for the shelf under it. */
    val focusLevel: Float get() = focus.value.coerceIn(0f, 1f)

    init {
        clipChildren = false
        clipToPadding = false
        outlineProvider = null
        addView(card, LayoutParams(ui.cellW, ui.cardH))
        card.pivotX = ui.cellW / 2f
        card.pivotY = ui.cardH / 2f
        card.cameraDistance = CAMERA_DISTANCE * resources.displayMetrics.density
        val labelParams = LayoutParams(ui.cellW, ui.labelH.toInt())
        labelParams.topMargin = (ui.pad + ui.tileH + ui.px(24f)).toInt()
        addView(label, labelParams)
    }

    fun place(toX: Float, toY: Float, snap: Boolean) {
        if (snap) {
            posX.snap(toX)
            posY.snap(toY)
        } else {
            posX.target = toX
            posY.target = toY
        }
    }

    fun revealAfter(delaySeconds: Float) {
        appear.snap(0f)
        appearDelay = delaySeconds
        if (delaySeconds <= 0f) appear.target = 1f
    }

    fun setFocusedTile(focused: Boolean, dx: Int, dy: Int) {
        val wasFocused = focusedTile
        focusedTile = focused
        focus.target = if (focused) 1f else 0f
        if (focused) {
            tiltX.velocity += dx * LEAN_IMPULSE
            tiltY.velocity += dy * LEAN_IMPULSE
            // Trigger the specular sweep on arrival, not on every tick while focused.
            if (!wasFocused) shineElapsed = 0f
        } else {
            tiltX.target = 0f
            tiltY.target = 0f
            press.target = 0f
        }
    }

    fun setHeld(held: Boolean) {
        press.target = if (held) 1f else 0f
    }

    /** Grays the tile out while the TV is idle. */
    fun setDimmed(dimmed: Boolean) {
        dim.target = if (dimmed) 1f else 0f
    }

    /** Rubber-bands against an edge when there is nowhere further to go. */
    fun bump(dx: Int, dy: Int) {
        nudgeX.velocity += dx * ui.px(1100f)
        nudgeY.velocity += dy * ui.px(1100f)
        tiltX.velocity += dx * LEAN_IMPULSE
        tiltY.velocity += dy * LEAN_IMPULSE
    }

    fun tick(dt: Float, time: Float, sway: Boolean, editing: Boolean): Boolean {
        var moving = editing
        if (appearDelay > 0f) {
            appearDelay -= dt
            if (appearDelay <= 0f) appear.target = 1f
            moving = true
        }
        if (focusedTile && sway && !editing) {
            tiltX.target = sin(time * 0.9f) * SWAY
            tiltY.target = cos(time * 0.7f + 1f) * SWAY
            moving = true
        }
        moving = focus.step(dt) || moving
        moving = press.step(dt) || moving
        moving = tiltX.step(dt) || moving
        moving = tiltY.step(dt) || moving
        moving = nudgeX.step(dt) || moving
        moving = nudgeY.step(dt) || moving
        moving = posX.step(dt) || moving
        moving = posY.step(dt) || moving
        moving = appear.step(dt) || moving
        moving = dim.step(dt) || moving

        val idle = dim.value.coerceIn(0f, 1f)
        val f = focus.value * (1f - idle)
        val shown = appear.value.coerceIn(0f, 1f)
        val faded = f.coerceIn(0f, 1f)
        val size = (1f + f * ui.focusGrow - press.value * PRESS_DEPTH) * (0.9f + 0.1f * shown)
        card.scaleX = size
        card.scaleY = size
        card.rotationY = tiltX.value * MAX_TILT
        card.rotationX = -tiltY.value * MAX_TILT
        card.rotation = if (editing) sin(time * 15f + phase) * WIGGLE else 0f
        card.translationX = nudgeX.value
        card.translationY = nudgeY.value
        card.setLook(f, tiltX.value, tiltY.value)
        card.setGray(idle)
        // Advance the shine sweep if one is running. Stays off once past 1.
        if (shineElapsed >= 0f) {
            shineElapsed += dt
            val progress = shineElapsed / SHINE_DURATION
            if (progress >= 1f) {
                shineElapsed = -1f
                card.setShine(-1f)
            } else {
                card.setShine(progress)
                moving = true
            }
        }
        label.alpha = faded
        label.translationY = (1f - faded) * ui.px(-12f)
        translationX = posX.value
        translationY = posY.value + (1f - appear.value) * ui.px(120f)
        alpha = shown * (1f - idle * IDLE_FADE)
        translationZ = f * ui.px(30f)
        return moving
    }

    override fun hasOverlappingRendering(): Boolean = false

    private companion object {
        const val CAMERA_DISTANCE = 9000f
        const val LEAN_IMPULSE = 4.2f
        const val SWAY = 0.22f
        const val PRESS_DEPTH = 0.07f
        const val MAX_TILT = 10f
        const val WIGGLE = 1.4f
        const val IDLE_FADE = 0.22f
        const val SHINE_DURATION = 0.55f    // seconds; the diagonal highlight's traversal time
    }
}

/**
 * A tvOS-style aerial wallpaper: a looping, muted MP4 that lives behind the whole UI. Fills
 * the screen via FIT_XY (orientation is landscape and videos are expected to be 16:9), fades
 * in once ready, and releases its MediaPlayer on stop so no resources are held while an app
 * is in the foreground.
 */
@SuppressLint("ViewConstructor")
class AerialView(context: Context) : TextureView(context) {
    private var player: MediaPlayer? = null
    private var pendingPath: String? = null
    private var surface: Surface? = null
    private val listener = object : SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
            surface = Surface(st)
            pendingPath?.let { start(it) }
        }
        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}
        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
            releasePlayer()
            surface?.release()
            surface = null
            return true
        }
        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
    }

    init {
        alpha = 0f
        isOpaque = false
        surfaceTextureListener = listener
    }

    /** Play [file] on loop. Safe to call before the texture is ready; it queues until it is. */
    fun play(file: File) {
        pendingPath = file.absolutePath
        if (surface != null) start(file.absolutePath)
    }

    fun pause() {
        try { player?.pause() } catch (_: Exception) {}
    }

    fun resume() {
        try { player?.start() } catch (_: Exception) {}
    }

    /** Called from the activity's onStop; the texture listener handles its own tear-down too. */
    fun release() {
        pendingPath = null
        releasePlayer()
    }

    private fun start(path: String) {
        releasePlayer()
        val surface = surface ?: return
        player = try {
            MediaPlayer().apply {
                setSurface(surface)
                setDataSource(path)
                isLooping = true
                setVolume(0f, 0f)
                setOnPreparedListener {
                    try { it.start() } catch (_: Exception) {}
                    animate().alpha(1f).setDuration(600L).start()
                }
                setOnErrorListener { _, _, _ -> true }
                prepareAsync()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun releasePlayer() {
        try {
            player?.stop()
        } catch (_: Exception) {}
        try {
            player?.release()
        } catch (_: Exception) {}
        player = null
        alpha = 0f
    }
}

/**
 * A screen-edge vignette: a radial gradient from transparent at the middle to a soft dark
 * band around the corners. Sits above the backdrop but below the grid/clock, so everything
 * else is unchanged while the picture quietly draws the eye to the center. Classic tvOS.
 */
@SuppressLint("ViewConstructor")
class Vignette(context: Context, private val ui: Ui) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w == 0 || h == 0) return
        val cx = w / 2f
        val cy = h / 2f
        // Reach to the furthest corner so the darkest stop is pinned to the corners.
        val radius = kotlin.math.hypot(cx, cy)
        paint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(0x00000000, 0x00000000, 0x33000000, 0x66000000),
            floatArrayOf(0f, 0.55f, 0.85f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    override fun hasOverlappingRendering(): Boolean = false
}

/**
 * A soft radial halo that follows the focused tile — tvOS's signature "this is selected" bloom.
 * Lives above the grid's backdrop and below the tile itself, so the glow pools around the card
 * without washing out the art on top. Full-screen so the gradient can extend well past the tile
 * bounds without any clipping.
 */
@SuppressLint("ViewConstructor")
class FocusGlow(context: Context, private val ui: Ui) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isDither = true }
    private var cx = 0f
    private var cy = 0f
    private var radius = 0f
    private var strength = 0f

    fun update(centerX: Float, centerY: Float, tileWidth: Float, focus: Float) {
        cx = centerX
        cy = centerY
        // A bit bigger than the tile so the halo visibly spills past the edges.
        radius = tileWidth * 0.95f
        strength = focus.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (strength <= 0.01f || radius <= 0f) return
        val peak = (0x4C * strength).toInt().coerceIn(0, 255) shl 24
        paint.shader = RadialGradient(
            cx, cy, radius,
            intArrayOf(peak or 0x00FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, radius, paint)
    }

    override fun hasOverlappingRendering(): Boolean = false
}

/** How the backdrop moves: a drifting blur, a still wallpaper, or a slow pan across photos. */
enum class BackdropMotion { BLURRED, STILL, SLIDESHOW }

/** The full-screen picture behind everything: the wallpaper, a photo, or a blur of the focused app. */
@SuppressLint("ViewConstructor")
class Backdrop(context: Context, private val ui: Ui) : FrameLayout(context) {
    private val under = layer(context)
    private val over = layer(context)
    private val quickFade = Spring(30f, 11f)
    private val slowFade = Spring(3.2f, 3.6f)
    private var fade = quickFade
    private val zoom = Spring(6f, 5f, initial = BLUR_ZOOM, epsilon = 0.0005f)
    private val panX = Spring(6f, 5f, epsilon = 0.3f)
    private val panY = Spring(6f, 5f, epsilon = 0.3f)
    private var current: Bitmap? = null
    private var settling = false

    var motion = BackdropMotion.BLURRED

    init {
        addView(under, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        addView(over, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        over.alpha = 0f
        scaleX = BLUR_ZOOM
        scaleY = BLUR_ZOOM
    }

    fun show(bitmap: Bitmap, slow: Boolean = false) {
        if (bitmap === current) return
        current?.let { if (fade.value > 0.5f) under.setImageBitmap(it) }
        current = bitmap
        over.setImageBitmap(bitmap)
        fade = if (slow) slowFade else quickFade
        fade.snap(0f)
        fade.target = 1f
        settling = true
    }

    /** Lets go of the current picture so its memory can go to the app being opened. */
    fun clear() {
        under.setImageDrawable(null)
        over.setImageDrawable(null)
        over.alpha = 0f
        current = null
        settling = false
    }

    fun tick(dt: Float, time: Float, ambient: Boolean): Boolean {
        val fading = fade.step(dt)
        if (settling) {
            over.alpha = fade.value.coerceIn(0f, 1f)
            if (!fading) {
                // Only one full-screen layer stays on screen once the fade is over.
                under.setImageBitmap(current)
                over.alpha = 0f
                settling = false
            }
        }
        when (motion) {
            BackdropMotion.BLURRED -> if (ambient) {
                zoom.target = BLUR_ZOOM + sin(time * 0.13f) * 0.035f
                panX.target = sin(time * 0.09f) * ui.px(34f)
                panY.target = cos(time * 0.11f) * ui.px(20f)
            }
            BackdropMotion.STILL -> {
                zoom.target = 1f
                panX.target = 0f
                panY.target = 0f
            }
            BackdropMotion.SLIDESHOW -> {
                zoom.target = SLIDE_ZOOM + sin(time * 0.07f) * 0.03f
                panX.target = sin(time * 0.05f) * ui.px(24f)
                panY.target = cos(time * 0.04f) * ui.px(12f)
            }
        }
        var moving = zoom.step(dt)
        moving = panX.step(dt) || moving
        moving = panY.step(dt) || moving
        scaleX = zoom.value
        scaleY = zoom.value
        translationX = panX.value
        translationY = panY.value
        return fading || settling || moving
    }

    private fun layer(context: Context) = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_XY }

    private companion object {
        const val BLUR_ZOOM = 1.12f
        const val SLIDE_ZOOM = 1.06f
    }
}

/**
 * The top area that previews the focused app: a large centered piece of the app's own artwork
 * instead of a big text title, with a small caption underneath.
 */
@SuppressLint("ViewConstructor")
class HeroView(context: Context, private val ui: Ui) : FrameLayout(context) {
    private val artHolder = FrameLayout(context)
    private val arts = Array(2) { ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER; alpha = 0f } }
    private val captions = Array(2) { makeCaption(context) }
    private var artFront = 0
    private var captionFront = 0

    private val artW = ui.heroArtW.toInt()
    private val artH = ui.heroArtH.toInt()
    private val artTop = ui.heroArtTop

    init {
        clipChildren = false
        val artParams = LayoutParams(artW, artH)
        artParams.leftMargin = ((ui.screenW - artW) / 2f).toInt()
        artParams.topMargin = artTop.toInt()
        addView(artHolder, artParams)
        for (art in arts) {
            artHolder.addView(art, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        for (caption in captions) {
            val params = LayoutParams(ui.screenW, ViewGroup.LayoutParams.WRAP_CONTENT)
            params.topMargin = ui.px(150f + 372f + 24f).toInt()
            addView(caption, params)
        }
    }

    fun showTitle(text: CharSequence, dir: Int) {
        if (captions[captionFront].text == text) return
        val outgoing = captions[captionFront]
        captionFront = 1 - captionFront
        val incoming = captions[captionFront]
        incoming.text = text
        crossSlide(outgoing, incoming, dir, ui.px(40f))
    }

    fun showArt(bitmap: Bitmap, dir: Int) {
        val outgoing = arts[artFront]
        artFront = 1 - artFront
        val incoming = arts[artFront]
        incoming.setImageBitmap(bitmap)
        crossSlide(outgoing, incoming, dir, ui.px(90f))
    }

    fun clearArt() {
        for (art in arts) {
            art.animate().cancel()
            art.setImageDrawable(null)
            art.alpha = 0f
        }
    }

    fun drift(time: Float) {
        artHolder.translationY = sin(time * 0.7f) * ui.px(7f)
    }

    /** True once a hero bitmap has been set, so it can be used for the open animation. */
    fun hasArt(): Boolean = arts[artFront].drawable != null

    /** Expands the hero art to fill the screen: the tvOS-style "open the app" animation. */
    fun expand(duration: Long) {
        artHolder.animate().cancel()
        artHolder.pivotX = artW / 2f
        artHolder.pivotY = artH / 2f
        val cover = maxOf(ui.screenW.toFloat() / artW, ui.screenH.toFloat() / artH)
        val centerY = artTop + artH / 2f
        artHolder.animate()
            .scaleX(cover).scaleY(cover)
            .translationY(ui.screenH / 2f - centerY)
            .setDuration(duration)
            .setInterpolator(DECELERATE)
            .start()
        val caption = captions[captionFront]
        caption.animate().cancel()
        caption.animate().alpha(0f).setDuration(duration / 2).start()
    }

    /** Restores the hero to its resting size after returning from an opened app. */
    fun resetExpand() {
        artHolder.animate().cancel()
        artHolder.scaleX = 1f
        artHolder.scaleY = 1f
        artHolder.translationY = 0f
        val caption = captions[captionFront]
        caption.animate().cancel()
        caption.alpha = 1f
    }

    private fun crossSlide(outgoing: View, incoming: View, dir: Int, travel: Float) {
        outgoing.animate().cancel()
        outgoing.animate()
            .alpha(0f)
            .translationX(-dir * travel * 0.8f)
            .setDuration(OUT_MS)
            .setInterpolator(DECELERATE)
            .start()
        incoming.animate().cancel()
        incoming.alpha = 0f
        incoming.translationX = dir * travel
        incoming.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(IN_MS)
            .setInterpolator(DECELERATE)
            .start()
    }

    private fun makeCaption(context: Context) = TextView(context).apply {
        setTextColor(0xF2FFFFFF.toInt())
        setTextSize(TypedValue.COMPLEX_UNIT_PX, ui.px(40f))
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER_HORIZONTAL
        letterSpacing = 0.02f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        setShadowLayer(ui.px(16f), 0f, ui.px(3f), 0x80000000.toInt())
        alpha = 0f
    }

    private companion object {
        const val IN_MS = 440L
        const val OUT_MS = 220L
        val DECELERATE = DecelerateInterpolator(2.4f)
    }
}

/**
 * A reflective silver shelf that sits under the focused app, tvOS-style. It follows the focused
 * tile and grows or shrinks with it, so the icon looks like it is resting on a metal bar.
 */
@SuppressLint("ViewConstructor")
class ShelfBar(context: Context, private val ui: Ui) : View(context) {
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gleamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val rect = RectF()
    private val barH = ui.px(6f)
    private val gleamH = ui.px(1.6f)
    private val glowH = ui.px(32f)

    private var cx = 0f
    private var cy = 0f
    private var halfW = 0f
    private var shown = 0f

    init {
        // A thin brushed-metal line: bright top edge, cool silver core, soft shadow at the base.
        barPaint.shader = LinearGradient(
            0f, -barH / 2f, 0f, barH / 2f,
            intArrayOf(0xFFFCFDFF.toInt(), 0xFFCED4DE.toInt(), 0xFF828A96.toInt()),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP,
        )
    }

    /** Positions the shelf under the focused tile. Width scales with how focused it is. */
    fun update(centerX: Float, baselineY: Float, tileWidth: Float, focus: Float) {
        cx = centerX
        cy = baselineY
        halfW = tileWidth * (0.40f + 0.16f * focus)
        shown = focus
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (shown <= 0.01f) return
        val a = shown.coerceIn(0f, 1f)
        // Cool reflective glow pooling under the tile.
        val glowAlpha = (0x4D * a).toInt() shl 24
        glowPaint.shader = RadialGradient(
            cx, cy, halfW * 1.5f,
            intArrayOf(glowAlpha or 0x00C2D6FF, 0x00C2D6FF),
            floatArrayOf(0f, 1f), Shader.TileMode.CLAMP,
        )
        rect.set(cx - halfW * 1.5f, cy - glowH, cx + halfW * 1.5f, cy + glowH)
        canvas.drawRect(rect, glowPaint)
        // The silver line itself, with rounded ends.
        barPaint.alpha = (255 * a).toInt()
        rect.set(cx - halfW, cy - barH / 2f, cx + halfW, cy + barH / 2f)
        canvas.drawRoundRect(rect, barH / 2f, barH / 2f, barPaint)
        // A crisp specular gleam along the very top edge.
        gleamPaint.alpha = (165 * a).toInt()
        rect.set(cx - halfW * 0.88f, cy - barH / 2f, cx + halfW * 0.88f, cy - barH / 2f + gleamH)
        canvas.drawRoundRect(rect, gleamH, gleamH, gleamPaint)
    }

    override fun hasOverlappingRendering(): Boolean = false
}

/** A clean analog clock face for the always-on display: ticks, three hands, a teal second hand. */
@SuppressLint("ViewConstructor")
class AnalogClockView(context: Context, private val ui: Ui) : View(context) {
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0xE6FFFFFF.toInt(); strokeWidth = ui.px(4f)
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt(); strokeCap = Paint.Cap.ROUND
    }
    private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); strokeCap = Paint.Cap.ROUND
    }
    private val secPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF37D6C3.toInt(); strokeCap = Paint.Cap.ROUND; strokeWidth = ui.px(3f)
    }
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF37D6C3.toInt() }

    private var hourDeg = -1f
    private var minDeg = 0f
    private var secDeg = 0f

    /** Sets the hands; only redraws when something actually moved. */
    fun setTime(hour: Int, minute: Int, second: Int) {
        val hd = ((hour % 12) + minute / 60f) / 12f * 360f
        val md = (minute + second / 60f) / 60f * 360f
        val sd = second / 60f * 360f
        if (hd == hourDeg && md == minDeg && sd == secDeg) return
        hourDeg = hd; minDeg = md; secDeg = sd
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(cx, cy) - ui.px(6f)
        canvas.drawCircle(cx, cy, r, ring)
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0)
            val sin = Math.sin(a).toFloat()
            val cos = -Math.cos(a).toFloat()
            val inner = if (i % 3 == 0) r * 0.82f else r * 0.90f
            tickPaint.strokeWidth = if (i % 3 == 0) ui.px(6f) else ui.px(3f)
            canvas.drawLine(cx + sin * inner, cy + cos * inner, cx + sin * r * 0.97f, cy + cos * r * 0.97f, tickPaint)
        }
        handPaint.strokeWidth = ui.px(9f)
        hand(canvas, cx, cy, hourDeg, r * 0.52f)
        handPaint.strokeWidth = ui.px(6f)
        hand(canvas, cx, cy, minDeg, r * 0.76f)
        canvas.drawLine(
            cx - Math.sin(Math.toRadians(secDeg.toDouble())).toFloat() * r * 0.2f,
            cy + Math.cos(Math.toRadians(secDeg.toDouble())).toFloat() * r * 0.2f,
            cx + Math.sin(Math.toRadians(secDeg.toDouble())).toFloat() * r * 0.84f,
            cy - Math.cos(Math.toRadians(secDeg.toDouble())).toFloat() * r * 0.84f,
            secPaint,
        )
        canvas.drawCircle(cx, cy, ui.px(8f), hubPaint)
    }

    private fun hand(canvas: Canvas, cx: Float, cy: Float, deg: Float, len: Float) {
        val a = Math.toRadians(deg.toDouble())
        val sin = Math.sin(a).toFloat()
        val cos = -Math.cos(a).toFloat()
        canvas.drawLine(cx - sin * len * 0.16f, cy - cos * len * 0.16f, cx + sin * len, cy + cos * len, handPaint)
    }

    override fun hasOverlappingRendering(): Boolean = false
}
