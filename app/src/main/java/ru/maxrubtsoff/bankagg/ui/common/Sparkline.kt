package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.ChartScale
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * A little line of the month for the strip under a sum (F-36): no axes, no
 * numbers, only the shape. 72×24 dp unless the caller sizes it. The scale is at
 * least [minSpanFraction] of the average wide, so a wiggle of kopecks stays a
 * nearly flat line (F-43).
 */
@Composable
fun Sparkline(
    points: List<Long>,
    color: Color = Slitok.Brass2,
    modifier: Modifier = Modifier.size(width = 72.dp, height = 24.dp),
    minSpanFraction: Float = 0.02f
) {
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val pad = 2.dp.toPx()
        val (lo, hi) = ChartScale.range(points, Math.round(minSpanFraction * 10_000))
        val span = hi - lo
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        val path = Path()
        points.forEachIndexed { i, v ->
            val x = pad + w * i / (points.size - 1)
            val y = pad + h * (1f - (v - lo).toFloat() / span)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val last = Offset(pad + w, pad + h * (1f - (points.last() - lo).toFloat() / span))
        drawCircle(color, 2.dp.toPx(), last)
    }
}
