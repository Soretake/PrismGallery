package com.example.amoledgallery.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.roundToInt

/** One-tap looks, applied before the sliders. */
enum class PhotoFilter { NONE, VIVID, MONO, SEPIA, COOL, WARM, FADE, NOIR }

/**
 * Colour adjustments of an edit, each -1..1 (0 = unchanged); [vignette] 0..1. Shared by the preview (as a
 * Compose colour filter) and the export (as an android.graphics one), so both look the same.
 */
data class Looks(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val saturation: Float = 0f,
    val warmth: Float = 0f,
    val vignette: Float = 0f,
    val filter: PhotoFilter = PhotoFilter.NONE
) {
    val isNeutral: Boolean
        get() = brightness == 0f && contrast == 0f && saturation == 0f && warmth == 0f && vignette == 0f && filter == PhotoFilter.NONE

    /** The filter's own settings, combined with the sliders. */
    private fun effective(): Looks = when (filter) {
        PhotoFilter.NONE, PhotoFilter.SEPIA -> this
        PhotoFilter.VIVID -> copy(saturation = saturation + 0.45f, contrast = contrast + 0.15f)
        PhotoFilter.MONO -> copy(saturation = -1f)
        PhotoFilter.COOL -> copy(warmth = warmth - 0.6f, saturation = saturation + 0.05f)
        PhotoFilter.WARM -> copy(warmth = warmth + 0.6f, saturation = saturation + 0.05f)
        PhotoFilter.FADE -> copy(contrast = contrast - 0.3f, brightness = brightness + 0.08f, saturation = saturation - 0.25f)
        PhotoFilter.NOIR -> copy(saturation = -1f, contrast = contrast + 0.45f, brightness = brightness - 0.05f)
    }

    /** A 4×5 colour matrix (android.graphics layout). */
    fun matrix(): ColorMatrix {
        val e = effective()
        val m = ColorMatrix()
        // Saturation
        m.setSaturation((1f + e.saturation).coerceIn(0f, 2.5f))
        // Sepia tone on top of the grey version.
        if (filter == PhotoFilter.SEPIA) {
            m.postConcat(ColorMatrix().apply { setSaturation(0f) })
            m.postConcat(ColorMatrix(floatArrayOf(
                1.07f, 0f, 0f, 0f, 18f,
                0f, 0.94f, 0f, 0f, 8f,
                0f, 0f, 0.74f, 0f, -12f,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        // Warmth: more red, less blue (or the other way round).
        val w = e.warmth.coerceIn(-1f, 1f) * 0.14f
        m.postConcat(ColorMatrix(floatArrayOf(
            1f + w, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f - w, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )))
        // Contrast around the middle grey, then brightness as an offset.
        val c = (1f + e.contrast.coerceIn(-1f, 1f) * 0.8f)
        val offset = 128f * (1f - c) + e.brightness.coerceIn(-1f, 1f) * 90f
        m.postConcat(ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, offset,
            0f, c, 0f, 0f, offset,
            0f, 0f, c, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        )))
        return m
    }
}

/** Something drawn on the photo, in 0..1 coordinates of the rotated, uncropped image. */
sealed interface Mark {
    data class Stroke(val points: List<Pair<Float, Float>>, val color: Int, val width: Float) : Mark
    data class Label(val text: String, val x: Float, val y: Float, val size: Float, val color: Int) : Mark
}

/** Turns marks along with the image (rotation clockwise, mirroring). */
fun Mark.rotateClockwise(): Mark = when (this) {
    is Mark.Stroke -> copy(points = points.map { (x, y) -> (1f - y) to x })
    is Mark.Label -> copy(x = 1f - y, y = x)
}

fun Mark.flipHorizontal(): Mark = when (this) {
    is Mark.Stroke -> copy(points = points.map { (x, y) -> (1f - x) to y })
    is Mark.Label -> copy(x = 1f - x)
}

fun Mark.flipVertical(): Mark = when (this) {
    is Mark.Stroke -> copy(points = points.map { (x, y) -> x to (1f - y) })
    is Mark.Label -> copy(y = 1f - y)
}

/** Paints looks, marks, the watermark and the vignette onto (a copy of) the rotated image, then crops it. */
object EditRenderer {
    fun render(transformed: Bitmap, spec: EditSpec): Bitmap {
        val decorated = if (spec.looks.isNeutral && spec.marks.isEmpty() && spec.watermark.isNullOrBlank()) {
            transformed
        } else {
            val out = Bitmap.createBitmap(transformed.width, transformed.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            if (!spec.looks.isNeutral) paint.colorFilter = ColorMatrixColorFilter(spec.looks.matrix())
            canvas.drawBitmap(transformed, 0f, 0f, paint)
            drawVignette(canvas, out.width, out.height, spec.looks.vignette)
            drawMarks(canvas, out.width, out.height, spec.marks)
            spec.watermark?.takeIf { it.isNotBlank() }?.let { drawWatermark(canvas, out.width, out.height, it) }
            out
        }
        val crop = spec.crop
        if (crop.isFull) return decorated
        val x = (crop.left * decorated.width).roundToInt().coerceIn(0, decorated.width - 1)
        val y = (crop.top * decorated.height).roundToInt().coerceIn(0, decorated.height - 1)
        val width = ((crop.right - crop.left) * decorated.width).roundToInt().coerceIn(1, decorated.width - x)
        val height = ((crop.bottom - crop.top) * decorated.height).roundToInt().coerceIn(1, decorated.height - y)
        return Bitmap.createBitmap(decorated, x, y, width, height)
    }

    fun drawVignette(canvas: Canvas, w: Int, h: Int, strength: Float) {
        if (strength <= 0f) return
        val radius = max(w, h) * 0.75f
        val paint = Paint().apply {
            shader = RadialGradient(
                w / 2f, h / 2f, radius,
                intArrayOf(0x00000000, 0x00000000, android.graphics.Color.argb((strength * 200).roundToInt().coerceIn(0, 255), 0, 0, 0)),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    }

    fun drawMarks(canvas: Canvas, w: Int, h: Int, marks: List<Mark>) {
        val unit = max(w, h).toFloat()
        marks.forEach { mark ->
            when (mark) {
                is Mark.Stroke -> {
                    if (mark.points.isEmpty()) return@forEach
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = mark.color
                        style = Paint.Style.STROKE
                        strokeWidth = mark.width * unit
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                    val path = Path()
                    mark.points.forEachIndexed { i, (x, y) -> if (i == 0) path.moveTo(x * w, y * h) else path.lineTo(x * w, y * h) }
                    if (mark.points.size == 1) path.lineTo(mark.points[0].first * w + 0.1f, mark.points[0].second * h)
                    canvas.drawPath(path, paint)
                }
                is Mark.Label -> {
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = mark.color
                        textSize = mark.size * unit
                        typeface = Typeface.DEFAULT_BOLD
                        textAlign = Paint.Align.CENTER
                        setShadowLayer(textSize * 0.08f, 0f, textSize * 0.04f, 0x66000000)
                    }
                    val lines = mark.text.lines()
                    val lineHeight = paint.textSize * 1.15f
                    val startY = mark.y * h - (lines.size - 1) * lineHeight / 2f - (paint.ascent() + paint.descent()) / 2f
                    lines.forEachIndexed { i, line -> canvas.drawText(line, mark.x * w, startY + i * lineHeight, paint) }
                }
            }
        }
    }

    /** The text repeated diagonally over the whole photo, half transparent - against misuse of ID copies. */
    fun drawWatermark(canvas: Canvas, w: Int, h: Int, text: String) {
        val unit = max(w, h).toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(95, 255, 255, 255)
            textSize = unit * 0.035f
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(textSize * 0.1f, 0f, 0f, android.graphics.Color.argb(90, 0, 0, 0))
        }
        val step = paint.measureText(text) + paint.textSize * 2.5f
        val lineGap = paint.textSize * 4f
        canvas.save()
        canvas.rotate(-30f, w / 2f, h / 2f)
        var y = -unit
        var row = 0
        while (y < unit * 2) {
            var x = -unit + (row % 2) * step / 2f
            while (x < unit * 2) {
                canvas.drawText(text, x, y, paint)
                x += step
            }
            y += lineGap
            row++
        }
        canvas.restore()
    }
}
