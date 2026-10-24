package ru.maxrubtsoff.bankagg.data.notify

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.graphics.ColorUtils
import ru.maxrubtsoff.bankagg.domain.model.BrandDecor

/**
 * A subscription's look as the big icon of its reminder (F-33): the user's
 * picture, or the letters on the colour, in a rounded square. Drawn once per
 * notification, off the main thread (the reminder worker).
 */
internal object DecorBitmap {

    private const val SIZE = 128

    fun of(d: BrandDecor): Bitmap {
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val rect = RectF(0f, 0f, SIZE.toFloat(), SIZE.toFloat())
        val radius = SIZE * 0.28f
        val picture = d.logoFile?.let { path -> runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
        if (picture != null) {
            // The picture clipped to the same rounded square.
            val clip = android.graphics.Path().apply { addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW) }
            canvas.clipPath(clip)
            canvas.drawBitmap(picture, null, Rect(0, 0, SIZE, SIZE), Paint(Paint.FILTER_BITMAP_FLAG))
            return out
        }
        val fill = d.colorArgb.toInt()
        canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill })
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = d.textColorArgb?.toInt() ?: if (ColorUtils.calculateLuminance(fill) > 0.6) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = SIZE * when (d.letters.length) { 0, 1 -> 0.5f; 2 -> 0.4f; else -> 0.32f }
        }
        val baseline = SIZE / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(d.letters, SIZE / 2f, baseline, text)
        return out
    }
}
