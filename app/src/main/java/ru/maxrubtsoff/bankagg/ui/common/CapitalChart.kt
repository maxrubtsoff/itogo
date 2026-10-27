package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.ChartMark
import ru.maxrubtsoff.bankagg.domain.model.ChartMarks
import ru.maxrubtsoff.bankagg.domain.model.ChartScale
import ru.maxrubtsoff.bankagg.domain.model.MarkShape
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
 *
 * [range] (indexes of points) is a band under the line for the days the picked point
 * stands for (F-46). [marks] are dots of the significant actions in the colour of their
 * part ([markColor]); all dots are one size (B-113). At rest there is no text; the picked dot shows its
 * sum on a plate, set where it touches neither the line, another dot nor the edge —
 * nowhere to put it, no sum (F-49). A touch within
 * 12 dp of a dot picks the dot's day.
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
    minSpanFraction: Float = 0.02f,
    range: IntRange? = null,
    marks: List<ChartMark> = emptyList(),
    markColor: (CapitalPart) -> Color = { color },
    /** Height of the line; the hero's strip draws it lower (U-67). */
    height: Dp = 150.dp,
    /** The sums on the dots; off in the strip, where they would not fit. */
    labels: Boolean = true,
    /** How much of the axis row is shown (1 — all), for the shrinking hero (U-67). */
    axisShown: () -> Float = { 1f }
) {
    // The band of days a point stands for (F-46) comes in on Motion.NORMAL and goes out the same way.
    val band by animateFloatAsState(if (range != null && range.first < range.last) 1f else 0f, Motion.tween(Motion.NORMAL), label = "band")
    val lastRange = remember { arrayOfNulls<IntRange>(1) }
    if (range != null && range.first < range.last) lastRange[0] = range
    val scrubbed by rememberUpdatedState(onScrub)
    val released by rememberUpdatedState(onRelease)
    var lastTouched by remember { mutableStateOf<Int?>(null) }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(points) {
        grow.snapTo(0f)
        grow.animateTo(1f, Motion.tween(Motion.SLOW))
    }
    val markAt = remember(marks) { marks.map { it.index } }
    fun indexAt(x: Float, width: Float, snapPx: Float): Int? {
        if (points.size < 2 || width <= 0f) return null
        val raw = Math.round((x / width).coerceIn(0f, 1f) * (points.size - 1))
        val step = width / (points.size - 1)
        return ChartMarks.snap(raw, markAt, Math.ceil((snapPx / step).toDouble()).toInt())
    }
    fun touch(i: Int?) { if (i != null && i != lastTouched) { lastTouched = i; scrubbed(i) } }

    val density = LocalDensity.current.density
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val hidden = LocalAmountsHidden.current
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    // The plate of the picked point's sum is worked out once per pick, data and size, not in every frame.
    val spot = remember(points, marks, selected, canvasSize, hidden, density, minSpanFraction, labels) {
        val mark = selected?.let { s -> marks.firstOrNull { it.index == s && it.label != null } }
        if (mark == null || hidden || !labels || points.size < 2 || canvasSize.width <= 0) null else {
            val layout = measurer.measure(mark.label!!, labelStyle, maxLines = 1, softWrap = false)
            val plot = plotOf(points, canvasSize.width.toFloat(), canvasSize.height.toFloat(), density, minSpanFraction)
            val size = Size(layout.size.width + 2 * LABEL_PAD_DP * density, LABEL_HEIGHT_DP * density)
            val dots = marks.filter { it.index in points.indices }.map { Offset(plot.xs[it.index], plot.ys[it.index]) }
            placeLabel(plot, mark.index, size, dots, canvasSize.width.toFloat(), canvasSize.height.toFloat(), density)
                ?.let { LabelSpot(layout, it, mark.amountMinor < 0) }
        }
    }

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .onSizeChanged { canvasSize = it }
                .pointerInput(points, markAt) {
                    detectTapGestures(onPress = { p ->
                        lastTouched = null
                        touch(indexAt(p.x, size.width.toFloat(), 12.dp.toPx()))
                        // A drag takes the touch over: it reports the release itself.
                        if (tryAwaitRelease()) lastTouched?.let { released(it) }
                    })
                }
                .pointerInput(points, markAt) {
                    detectHorizontalDragGestures(
                        onDragStart = { p -> touch(indexAt(p.x, size.width.toFloat(), 12.dp.toPx())) },
                        onDragEnd = { lastTouched?.let { released(it) } },
                        onDragCancel = { lastTouched?.let { released(it) } },
                        onHorizontalDrag = { change, _ -> change.consume(); touch(indexAt(change.position.x, size.width.toFloat(), 12.dp.toPx())) }
                    )
                }
        ) {
            if (points.size < 2) return@Canvas
            val plot = plotOf(points, size.width, size.height, density, minSpanFraction)
            fun x(i: Int) = plot.xs[i]
            fun y(i: Int) = plot.ys[i]

            val line = Path()
            points.indices.forEach { i -> if (i == 0) line.moveTo(x(i), y(i)) else line.lineTo(x(i), y(i)) }
            val fill = Path().apply {
                addPath(line)
                lineTo(x(points.size - 1), size.height)
                lineTo(x(0), size.height)
                close()
            }
            lastRange[0]?.takeIf { band > 0f }?.let { r ->
                val a = x(r.first.coerceIn(0, points.size - 1))
                val b = x(r.last.coerceIn(0, points.size - 1))
                drawRect(Slitok.BrassTint.copy(alpha = Slitok.BrassTint.alpha * band), Offset(a, 0f), Size(b - a, size.height))
            }
            clipRect(right = size.width * grow.value) {
                drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.18f), Color.Transparent)))
                drawPath(line, color.copy(alpha = 0.22f), style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                // Dots of the actions first, their sums over them.
                marks.forEach { m ->
                    if (m.index !in points.indices) return@forEach
                    val c = Offset(x(m.index), y(m.index))
                    val tint = markColor(m.part)
                    // One size for all: the kind is told by the fill (B-113).
                    drawCircle(Slitok.Bg, 6.dp.toPx(), c)
                    when (m.shape) {
                        MarkShape.BIG -> drawCircle(tint, 4.dp.toPx(), c)
                        MarkShape.SMALL -> drawCircle(tint.copy(alpha = 0.55f), 4.dp.toPx(), c)
                        MarkShape.RING -> { drawCircle(tint, 4.dp.toPx(), c); drawCircle(Slitok.Bg, 2.dp.toPx(), c) }
                    }
                }
            }
            val last = points.size - 1
            if (grow.value >= 1f) {
                drawCircle(color.copy(alpha = 0.25f), 8.dp.toPx(), Offset(x(last), y(last)))
                drawCircle(color, 4.dp.toPx(), Offset(x(last), y(last)))
            }
            selected?.let { i ->
                val s = i.coerceIn(0, last)
                drawLine(Slitok.Faint, Offset(x(s), 0f), Offset(x(s), size.height), strokeWidth = 1.dp.toPx())
                drawCircle(Slitok.Bg, 7.dp.toPx(), Offset(x(s), y(s)))
                drawCircle(color, 5.dp.toPx(), Offset(x(s), y(s)))
            }
            // The sum of the picked point on a plate; Fill is explicit, or the glyphs come out as outlines.
            if (grow.value >= 1f) spot?.let { sp ->
                val r = sp.plate
                drawRoundRect(Slitok.PanelTop, r.topLeft, r.size, CornerRadius(8.dp.toPx()))
                drawRoundRect(Slitok.Line, r.topLeft, r.size, CornerRadius(8.dp.toPx()), style = Stroke(1.dp.toPx()))
                drawText(
                    sp.layout, color = if (sp.negative) Slitok.Fg else Slitok.Mint,
                    topLeft = Offset(r.left + LABEL_PAD_DP.dp.toPx(), r.top + (r.height - sp.layout.size.height) / 2),
                    drawStyle = Fill
                )
            }
        }
        if (axis.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().collapseHeight(axisShown).padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                axis.forEach {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Slitok.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Where the points of the line fall on a canvas of [width] × [height] pixels. */
internal class Plot(val xs: FloatArray, val ys: FloatArray)

private fun plotOf(points: List<Long>, width: Float, height: Float, dp: Float, minSpanFraction: Float): Plot {
    val padX = 6 * dp
    val padY = 12 * dp
    val w = width - padX * 2
    val h = height - padY * 2
    val (lo, hi) = ChartScale.range(points, Math.round(minSpanFraction * 10_000))
    val span = hi - lo
    val n = points.size
    return Plot(
        FloatArray(n) { padX + w * it / (n - 1) },
        FloatArray(n) { padY + h * (1f - (points[it] - lo).toFloat() / span) }
    )
}

private const val LABEL_PAD_DP = 6
private const val LABEL_HEIGHT_DP = 20

/** The plate of a sum set on the chart: [plate] is its rectangle in pixels. */
private class LabelSpot(val layout: TextLayoutResult, val plate: Rect, val negative: Boolean)

/**
 * Where the plate of [size] for the dot at [index] goes: above, below, left, right, then the four corners;
 * the first place where it crosses neither the line (sampled every ~2 dp), another of [dots] (~7 dp round)
 * nor the edge. None free — the first place squeezed into the canvas (the plate is alone, the sum must show);
 * null only when the canvas is smaller than the plate.
 */
internal fun placeLabel(plot: Plot, index: Int, size: Size, dots: List<Offset>, width: Float, height: Float, dp: Float): Rect? {
    val n = plot.xs.size
    if (n < 2 || index !in 0 until n) return null
    val segW = plot.xs[1] - plot.xs[0]
    val dotR = 7 * dp
    val pad = 2 * dp
    val gap = 10 * dp
    val diag = 6 * dp
    val cx = plot.xs[index]
    val cy = plot.ys[index]
    val own = Offset(cx, cy)
    val others = dots.filter { it != own }

    fun lineY(x: Float): Float {
        val seg = ((x - plot.xs[0]) / segW).toInt().coerceIn(0, n - 2)
        val t = ((x - plot.xs[seg]) / segW).coerceIn(0f, 1f)
        return plot.ys[seg] + (plot.ys[seg + 1] - plot.ys[seg]) * t
    }

    fun hitsLine(r: Rect): Boolean {
        var x = maxOf(r.left, plot.xs[0])
        val end = minOf(r.right, plot.xs[n - 1])
        if (x > end) return false
        var prev = lineY(x)
        while (x < end) {
            x = minOf(x + 2 * dp, end)
            val cur = lineY(x)
            if (maxOf(prev, cur) >= r.top - pad && minOf(prev, cur) <= r.bottom + pad) return true
            prev = cur
        }
        return false
    }

    fun hitsDot(r: Rect, d: Offset): Boolean {
        val nx = d.x.coerceIn(r.left, r.right)
        val ny = d.y.coerceIn(r.top, r.bottom)
        return (nx - d.x) * (nx - d.x) + (ny - d.y) * (ny - d.y) < dotR * dotR
    }

    val w = size.width
    val h = size.height
    val candidates = listOf(
        Offset(cx - w / 2, cy - gap - h), Offset(cx - w / 2, cy + gap),
        Offset(cx - gap - w, cy - h / 2), Offset(cx + gap, cy - h / 2),
        Offset(cx - diag - w, cy - diag - h), Offset(cx + diag, cy + diag),
        Offset(cx - diag - w, cy + diag), Offset(cx + diag, cy - diag - h)
    )
    val strict = candidates.map { Rect(it, size) }.firstOrNull { r ->
        r.left >= 0f && r.top >= 0f && r.right <= width && r.bottom <= height &&
            !hitsLine(r) && !hitsDot(r, own) && others.none { hitsDot(r, it) }
    }
    if (strict != null) return strict
    // The plate is alone on the chart and the finger waits for the sum: no free place — squeeze
    // it into the edges anyway, off the picked dot if it can be. Only a canvas too small for it gives null.
    if (w > width || h > height) return null
    val squeezed = candidates.map { Rect(Offset(it.x.coerceIn(0f, width - w), it.y.coerceIn(0f, height - h)), size) }
    return squeezed.firstOrNull { !hitsDot(it, own) } ?: squeezed.first()
}
