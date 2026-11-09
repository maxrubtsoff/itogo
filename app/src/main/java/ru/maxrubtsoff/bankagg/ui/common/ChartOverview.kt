package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/** The edge of the window a handle of [ChartOverview] pulls. */
enum class Edge { START, END }

/**
 * The little map of the whole period under the history chart (U-77): the line of everything on `Inset` and the
 * window in a brass frame with a handle at each edge. A finger on the window moves it ([onPan], the share of the
 * whole map per move), on a handle pulls that edge ([onResize], where across the map the finger is), outside the
 * window carries it there ([onJump], where the middle should be) and goes on moving it. [window] is where the frame
 * stands across the map (0..1, by days, not by the thinned points); null — all of it. The bar is 28 dp, the touch 44 dp; the pager does not see the touch.
 */
@Composable
fun ChartOverview(
    points: List<Long>,
    window: ClosedFloatingPointRange<Float>?,
    onPan: (shiftFraction: Float) -> Unit,
    onResize: (edge: Edge, fraction: Float) -> Unit,
    onJump: (centerFraction: Float) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Slitok.Faint,
    description: String = "Вся история"
) {
    val pan by rememberUpdatedState(onPan)
    val resize by rememberUpdatedState(onResize)
    val jump by rememberUpdatedState(onJump)
    val n = points.size
    val frame by rememberUpdatedState(window ?: 0f..1f)
    Canvas(
        modifier.fillMaxWidth().height(44.dp).padding(vertical = 8.dp)
            .semantics { contentDescription = description }
            .pointerInput(n) {
                if (n < 2) return@pointerInput
                val slop = 22.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat()
                    val left = frame.start * w
                    val right = frame.endInclusive * w
                    val dx = down.position.x
                    // 0 — the left handle, 1 — the right one, 2 — the window itself.
                    val mode = when {
                        Math.abs(dx - left) <= slop && Math.abs(dx - left) <= Math.abs(dx - right) -> 0
                        Math.abs(dx - right) <= slop -> 1
                        dx in left..right -> 2
                        else -> { jump((dx / w).coerceIn(0f, 1f)); 2 }
                    }
                    down.consume()
                    var last = dx
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.positionChanged()) {
                            val x = change.position.x
                            when (mode) {
                                0 -> resize(Edge.START, (x / w).coerceIn(0f, 1f))
                                1 -> resize(Edge.END, (x / w).coerceIn(0f, 1f))
                                else -> pan((x - last) / w)
                            }
                            last = x
                            change.consume()
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        val r = 8.dp.toPx()
        drawRoundRect(Slitok.Inset, size = size, cornerRadius = CornerRadius(r))
        if (n < 2) return@Canvas
        val lo = points.min()
        val span = (points.max() - lo).coerceAtLeast(1L)
        val pad = 4.dp.toPx()
        fun x(i: Int) = i.toFloat() / (n - 1) * size.width
        fun y(i: Int) = pad + (size.height - 2 * pad) * (1f - (points[i] - lo).toFloat() / span)
        val line = Path()
        points.indices.forEach { i -> if (i == 0) line.moveTo(x(i), y(i)) else line.lineTo(x(i), y(i)) }
        drawPath(line, color, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val a = frame.start * size.width
        val b = frame.endInclusive * size.width
        val corner = CornerRadius(6.dp.toPx())
        drawRoundRect(Slitok.BrassTint, Offset(a, 0f), Size(b - a, size.height), corner)
        drawRoundRect(Slitok.BrassEdge, Offset(a, 0f), Size(b - a, size.height), corner, style = Stroke(1.5.dp.toPx()))
        val hw = 3.dp.toPx()
        val hh = 12.dp.toPx()
        listOf(a, b).forEach { cx ->
            drawRoundRect(Slitok.Brass2, Offset(cx - hw / 2, (size.height - hh) / 2), Size(hw, hh), CornerRadius(hw / 2))
        }
    }
}
