package com.fstv.launcher

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlin.math.max
import kotlin.math.min

/**
 * Everything expensive is drawn here once, on a worker thread. The animation code then only moves
 * finished images, which is what the TV's Mali-450 GPU can do at full frame rate.
 */
object Art {
    /** Transparent border around tile art, so tilted edges are smoothed by texture filtering. */
    const val INSET = 2

    private const val BACKDROP_W = 192
    private const val BACKDROP_H = 108
    private const val BLUR_RADIUS = 11
    private const val BLUR_PASSES = 3
    private const val SATURATION = 1.45f
    private const val TOP_BRIGHTNESS = 0.82f
    private const val BOTTOM_BRIGHTNESS = 0.36f
    private const val TOP_LUMA_CAP = 84f
    private const val BOTTOM_LUMA_CAP = 34f
    private const val DITHER = 1
    private const val FALLBACK_COLOR = 0xFF2A3142.toInt()

    /** Paints an app's artwork, or a generated tile when it has none, into a square-cornered bitmap. */
    fun base(source: Drawable?, isBanner: Boolean, w: Int, h: Int, glyph: String?): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        when {
            source != null && isBanner -> {
                canvas.drawColor(0xFF15181F.toInt())
                drawCentered(canvas, source, w, h)
            }
            source != null -> {
                fillGradient(canvas, dominantColor(source), w, h)
                val size = (h * 0.62f).toInt()
                source.setBounds((w - size) / 2, (h - size) / 2, (w + size) / 2, (h + size) / 2)
                smooth(source)
                source.draw(canvas)
            }
            else -> {
                fillGradient(canvas, FALLBACK_COLOR, w, h)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE
                    textSize = h * 0.42f
                    textAlign = Paint.Align.CENTER
                }
                val y = h / 2f - (paint.descent() + paint.ascent()) / 2f
                canvas.drawText(glyph ?: "", w / 2f, y, paint)
            }
        }
        return out
    }

    /** Cuts rounded corners into [base] and adds the transparent [INSET] border. */
    fun rounded(base: Bitmap, radius: Float): Bitmap {
        val w = base.width + INSET * 2
        val h = base.height + INSET * 2
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val shader = BitmapShader(base, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val matrix = android.graphics.Matrix().apply { setTranslate(INSET.toFloat(), INSET.toFloat()) }
        shader.setLocalMatrix(matrix)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
        val rect = RectF(INSET.toFloat(), INSET.toFloat(), (w - INSET).toFloat(), (h - INSET).toFloat())
        Canvas(out).drawRoundRect(rect, radius, radius, paint)
        return out
    }

    /** A very blurred, graded copy of the artwork. It is tiny, and scaling it up looks smooth. */
    fun backdrop(base: Bitmap): Bitmap {
        val small = Bitmap.createScaledBitmap(base, BACKDROP_W, BACKDROP_H, true)
        var pixels = IntArray(BACKDROP_W * BACKDROP_H)
        small.getPixels(pixels, 0, BACKDROP_W, 0, 0, BACKDROP_W, BACKDROP_H)
        if (small !== base) small.recycle()

        var scratch = IntArray(pixels.size)
        repeat(BLUR_PASSES) {
            boxPass(pixels, scratch, BACKDROP_W, BACKDROP_H, BLUR_RADIUS, horizontal = true)
            boxPass(scratch, pixels, BACKDROP_W, BACKDROP_H, BLUR_RADIUS, horizontal = false)
        }
        scratch = IntArray(0)
        grade(pixels, BACKDROP_W, BACKDROP_H)

        val out = Bitmap.createBitmap(pixels, BACKDROP_W, BACKDROP_H, Bitmap.Config.ARGB_8888)
        pixels = IntArray(0)
        out.setHasAlpha(false) // Lets the GPU draw it without blending.
        return out
    }

    /** One soft rounded-rectangle shadow shared by every tile. */
    fun shadow(ui: Ui): Bitmap {
        val blur = shadowBlur(ui)
        val w = (ui.tileW + blur * 2).toInt()
        val h = (ui.tileH + blur * 2).toInt()
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            maskFilter = BlurMaskFilter(blur * 0.9f, BlurMaskFilter.Blur.NORMAL)
        }
        Canvas(out).drawRoundRect(RectF(blur, blur, w - blur, h - blur), ui.radius, ui.radius, paint)
        return out
    }

    fun shadowBlur(ui: Ui): Float = ui.px(40f)

    private fun drawCentered(canvas: Canvas, drawable: Drawable, w: Int, h: Int) {
        val iw = drawable.intrinsicWidth
        val ih = drawable.intrinsicHeight
        if (iw <= 0 || ih <= 0) {
            drawable.setBounds(0, 0, w, h)
        } else {
            val scale = max(w.toFloat() / iw, h.toFloat() / ih)
            val dw = (iw * scale).toInt()
            val dh = (ih * scale).toInt()
            val l = (w - dw) / 2
            val t = (h - dh) / 2
            drawable.setBounds(l, t, l + dw, t + dh)
        }
        smooth(drawable)
        drawable.draw(canvas)
    }

    private fun smooth(drawable: Drawable) {
        (drawable as? BitmapDrawable)?.paint?.isFilterBitmap = true
    }

    private fun fillGradient(canvas: Canvas, color: Int, w: Int, h: Int) {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val light = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], min(1f, hsv[2] * 1.25f)))
        val dark = Color.HSVToColor(floatArrayOf(hsv[0], hsv[1], hsv[2] * 0.6f))
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, h.toFloat(), light, dark, Shader.TileMode.CLAMP)
            isDither = true
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    private fun dominantColor(drawable: Drawable): Int {
        val size = 16
        val sample = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(sample))
        val pixels = IntArray(size * size)
        sample.getPixels(pixels, 0, size, 0, 0, size, size)
        sample.recycle()

        var red = 0L
        var green = 0L
        var blue = 0L
        var weight = 0L
        for (p in pixels) {
            val a = Color.alpha(p)
            red += Color.red(p) * a.toLong()
            green += Color.green(p) * a.toLong()
            blue += Color.blue(p) * a.toLong()
            weight += a
        }
        if (weight == 0L) return FALLBACK_COLOR
        val hsv = FloatArray(3)
        Color.RGBToHSV((red / weight).toInt(), (green / weight).toInt(), (blue / weight).toInt(), hsv)
        hsv[1] = min(1f, hsv[1] * 1.2f)
        hsv[2] = hsv[2].coerceIn(0.28f, 0.58f)
        return Color.HSVToColor(hsv)
    }

    private fun boxPass(src: IntArray, dst: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val length = if (horizontal) w else h
        val along = if (horizontal) 1 else w
        val across = if (horizontal) w else 1
        val span = 2 * r + 1
        for (line in 0 until lines) {
            val start = line * across
            var sr = 0
            var sg = 0
            var sb = 0
            for (i in -r..r) {
                val p = src[start + i.coerceIn(0, length - 1) * along]
                sr += (p shr 16) and 255
                sg += (p shr 8) and 255
                sb += p and 255
            }
            for (i in 0 until length) {
                dst[start + i * along] = (255 shl 24) or ((sr / span) shl 16) or ((sg / span) shl 8) or (sb / span)
                val add = src[start + min(i + r + 1, length - 1) * along]
                val sub = src[start + max(i - r, 0) * along]
                sr += ((add shr 16) and 255) - ((sub shr 16) and 255)
                sg += ((add shr 8) and 255) - ((sub shr 8) and 255)
                sb += (add and 255) - (sub and 255)
            }
        }
    }

    /**
     * Boosts color and darkens toward the bottom, so tiles and text stand out. Brightness is capped
     * so a white banner still gives a dark screen, and a little noise hides gradient banding.
     */
    private fun grade(pixels: IntArray, w: Int, h: Int) {
        val noise = java.util.Random(7L)
        for (y in 0 until h) {
            val t = y.toFloat() / (h - 1)
            val brightness = TOP_BRIGHTNESS + (BOTTOM_BRIGHTNESS - TOP_BRIGHTNESS) * t
            val cap = TOP_LUMA_CAP + (BOTTOM_LUMA_CAP - TOP_LUMA_CAP) * t
            for (x in 0 until w) {
                val i = y * w + x
                val p = pixels[i]
                val r = (p shr 16) and 255
                val g = (p shr 8) and 255
                val b = p and 255
                val luma = (r * 54 + g * 183 + b * 19) shr 8
                val dimmed = luma * brightness
                val scale = brightness * (if (dimmed > cap) cap / dimmed else 1f)
                val grain = noise.nextInt(DITHER * 2 + 1) - DITHER
                val nr = ((luma + (r - luma) * SATURATION) * scale).toInt().plus(grain).coerceIn(10, 255)
                val ng = ((luma + (g - luma) * SATURATION) * scale).toInt().plus(grain).coerceIn(12, 255)
                val nb = ((luma + (b - luma) * SATURATION) * scale).toInt().plus(grain).coerceIn(20, 255)
                pixels[i] = (255 shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }
    }
}
