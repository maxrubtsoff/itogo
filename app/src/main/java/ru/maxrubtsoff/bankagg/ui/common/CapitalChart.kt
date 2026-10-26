package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.ChartScale
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * The line of the capital over a period (F-36): 2 dp in [color] with a soft glow
 * and a fill below, a dot on «today», the axis labels under it. A finger on it
 * (a touch or a drag) reports the index of the day under it through [onScrub];
 * letting go reports the last one through [onRelease]. The mark stays where
 * [selected] says: the caller keeps it after the finger is gone (F-41). The Y scale is
 * at least [minSpanFraction] of the average wide, so a wiggle of kopecks is not a
 * cliff (F-43). The line grows
 * in on [Motion.SLOW] (at once with «Убрать анимации»). A fall is the same line
 * in the same colour — it is not an alarm.
 */
@Composable
fun CapitalChart(
    points: List<Long>,
    axis: List<String>,
    selected: Int?,
    onScrub: (Int) -> Unit,
    onRelease: (Int) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Slitok.Brass2,
    minSpanFraction: Float = 0.02f
) {
    val scrubbed by rememberUpdatedState(onScrub)
    val released by rememberUpdatedState(onRelease)
    var lastTouched by remember { mutableStateOf<Int?>(null) }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(points) {
        grow.snapTo(0f)
        grow.animateTo(1f, Motion.tween(Motion.SLOW))
    }
    fun indexAt(x: Float, width: Float): Int? =
        if (points.size < 2 || width <= 0f) null else Math.round((x / width).coerceIn(0f, 1f) * (points.size - 1))
    fun touch(i: Int?) { if (i != null && i != lastTouched) { lastTouched = i; scrubbed(i) } }

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .pointerInput(points) {
                    detectTapGestures(onPress = { p ->
                        lastTouched = null
                        touch(indexAt(p.x, size.width.toFloat()))
                        // A drag takes the touch over: it reports the release itself.
                        if (tryAwaitRelease()) lastTouched?.let { released(it) }
                    })
                }
                .pointerInput(points) {
                    detectHorizontalDragGestures(
                        onDragStart = { p -> touch(indexAt(p.x, size.width.toFloat())) },
                        onDragEnd = { lastTouched?.let { released(it) } },
                        onDragCancel = { lastTouched?.let { released(it) } },
                        onHorizontalDrag = { change, _ -> change.consume(); touch(indexAt(change.position.x, size.width.toFloat())) }
                    )
                }
        ) {
            if (points.size < 2) return@Canvas
            val padX = 6.dp.toPx()
            val padY = 12.dp.toPx()
            val w = size.width - padX * 2
            val h = size.height - padY * 2
            val (lo, hi) = ChartScale.range(points, Math.round(minSpanFraction * 10_000))
            val span = hi - lo
            fun x(i: Int) = padX + w * i / (points.size - 1)
            fun y(v: Long) = padY + h * (1f - (v - lo).toFloat() / span)

            val line = Path()
            points.forEachIndexed { i, v -> if (i == 0) line.moveTo(x(i), y(v)) else line.lineTo(x(i), y(v)) }
            val fill = Path().apply {
                addPath(line)
                lineTo(x(points.size - 1), size.height)
                lineTo(x(0), size.height)
                close()
            }
            clipRect(right = size.width * grow.value) {
                drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.18f), Color.Transparent)))
                drawPath(line, color.copy(alpha = 0.22f), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            val last = points.size - 1
            if (grow.value >= 1f) {
                drawCircle(color.copy(alpha = 0.25f), 8.dp.toPx(), Offset(x(last), y(points[last])))
                drawCircle(color, 4.dp.toPx(), Offset(x(last), y(points[last])))
            }
            selected?.let { i ->
                val s = i.coerceIn(0, last)
                drawLine(Slitok.Faint, Offset(x(s), 0f), Offset(x(s), size.height), strokeWidth = 1.dp.toPx())
                drawCircle(Slitok.Bg, 6.dp.toPx(), Offset(x(s), y(points[s])))
                drawCircle(color, 4.dp.toPx(), Offset(x(s), y(points[s])))
            }
        }
        if (axis.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                axis.forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Slitok.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
