package ru.maxrubtsoff.bankagg.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.layout.size
import ru.maxrubtsoff.bankagg.R

/**
 * A figure drawn as a picture (U-73): Glance cannot load res/font, and a figure
 * must be Unbounded with figures of one width, like everywhere in the app.
 */
fun figureBitmap(context: Context, text: String, sizeSp: Float, color: Int, widthDp: Float, alignEnd: Boolean): Bitmap {
    val dm = context.resources.displayMetrics
    val base = ResourcesCompat.getFont(context, R.font.unbounded_wght) ?: Typeface.DEFAULT
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, 600, false) else base
        fontFeatureSettings = "tnum"
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, dm)
        this.color = color
    }
    val w = (widthDp * dm.density).toInt().coerceAtLeast(1)
    // Never cut: a wide figure shrinks to the box.
    val natural = paint.measureText(text)
    if (natural > w) paint.textSize *= w / natural
    val fm = paint.fontMetrics
    val h = (fm.descent - fm.ascent).toInt() + 2
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val textW = paint.measureText(text)
    val x = if (alignEnd) (w - textW).coerceAtLeast(0f) else 0f
    Canvas(bmp).drawText(text, x, -fm.ascent + 1, paint)
    return bmp
}

/** A figure in the widget: [figureBitmap] shown at its own size. */
@Composable
fun FigureImage(text: String, sizeSp: Float, color: Color, widthDp: Float, modifier: GlanceModifier = GlanceModifier, alignEnd: Boolean = true) {
    if (text.isEmpty()) return
    val context = LocalContext.current
    val bmp = figureBitmap(context, text, sizeSp, color.toArgb(), widthDp, alignEnd)
    val d = context.resources.displayMetrics.density
    Image(
        provider = ImageProvider(bmp),
        contentDescription = text,
        modifier = modifier.size(widthDp.dp, (bmp.height / d).dp)
    )
}
