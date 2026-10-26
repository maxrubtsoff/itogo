package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import ru.maxrubtsoff.bankagg.domain.model.KopeckLook
import ru.maxrubtsoff.bankagg.domain.model.MoneyMask
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** Amounts are hidden behind dust (F-11); set at the app root from the settings. */
val LocalAmountsHidden = compositionLocalOf { false }

/** Shows the amounts again (a tap on any dust). */
val LocalRevealAmounts = compositionLocalOf<() -> Unit> { {} }

/** One clock for every speck of dust on screen (seconds), read only while drawing (B-18). */
val LocalDustTime = compositionLocalOf<State<Float>> { mutableFloatStateOf(0f) }

/**
 * Ticks [LocalDustTime] at ~30 fps while amounts are hidden; idle otherwise.
 * With «Remove animations» the dust stands still (U-55, P5).
 */
@Composable
fun ProvideDustClock(running: Boolean, content: @Composable () -> Unit) {
    val time = remember { mutableFloatStateOf(0f) }
    if (running && Motion.enabled) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            var last = 0L
            while (true) withFrameNanos { now ->
                if (now - last >= FRAME_NS) {
                    last = now
                    time.floatValue = (now - start) / 1_000_000_000f
                }
            }
        }
    }
    CompositionLocalProvider(LocalDustTime provides time, content = content)
}

private const val FRAME_NS = 33_000_000L

/**
 * Kopecks quieter (U-35, U-56): the comma and the digits after it in every
 * amount, in the amount's own colour at [look]'s strength — on large steps
 * also smaller. [color] is the text's colour where no span gives one.
 */
fun dimKopecks(text: AnnotatedString, color: Color, look: KopeckLook): AnnotatedString {
    val ranges = MoneyMask.fractions(text.text)
    if (ranges.isEmpty()) return text
    val size = if (look.scale < 1f) look.scale.em else TextUnit.Unspecified
    return buildAnnotatedString {
        append(text)
        ranges.forEach { r ->
            val own = text.spanStyles.lastOrNull { it.start <= r.first && it.end > r.first && it.item.color != Color.Unspecified }?.item?.color
            val base = own ?: color
            addStyle(SpanStyle(fontSize = size, color = base.copy(alpha = base.alpha * look.alpha)), r.first, r.last + 1)
        }
    }
}

/** The kopeck look of an amount drawn at [size] — its step on the scale, not a shrunk size. */
internal fun kopeckLook(size: TextUnit): KopeckLook = KopeckLook.forSize(if (size.isSp) size.value else Float.NaN)

/** The colour an amount is drawn in: its own, its style's, or the surrounding content colour. */
@Composable
internal fun amountInk(color: Color, style: TextStyle): Color =
    if (color != Color.Unspecified) color else style.color.takeIf { it != Color.Unspecified } ?: LocalContentColor.current

/**
 * A plain line of text that may hold an amount: hidden amounts turn into dust,
 * the rest reads as usual. For auto-shrinking figures use [AmountText].
 */
@Composable
fun MoneyText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
    textAlign: TextAlign? = null,
    softWrap: Boolean = maxLines > 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    /** The short form of the sum: shown instead of the full text when that does not fit (F-40). */
    compact: String? = null
) {
    if (compact != null) {
        AmountText(text, modifier, style, color, fontWeight, minSize = 12.sp, textAlign = textAlign, compact = compact)
        return
    }
    // Plain labels have nothing to hide (F-18).
    if (!MoneyMask.hasMoney(text)) {
        Text(AnnotatedString(text), modifier = modifier, style = style, color = color, fontWeight = fontWeight, maxLines = maxLines,
            softWrap = softWrap, overflow = overflow, textAlign = textAlign)
        return
    }
    val ink = amountInk(color, style)
    val look = kopeckLook(style.fontSize)
    val shown = remember(text, ink, look) { dimKopecks(AnnotatedString(text), ink, look) }
    val ranges = remember(text) { MoneyMask.ranges(text) }
    val layout = remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        shown,
        modifier = modifier.amountDust(layout, ranges, ink),
        style = style,
        color = color,
        fontWeight = fontWeight,
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = overflow,
        textAlign = textAlign,
        onTextLayout = { layout.value = it }
    )
}

/** Figures come out of the dust gently and go back a step quicker. */
fun dustFade(hidden: Boolean): FiniteAnimationSpec<Float> = Motion.tween(if (hidden) Motion.FAST else Motion.NORMAL)

/**
 * Hides the [ranges] of the text drawn under it (its [layout]) behind dust
 * while [LocalAmountsHidden] (B-44): the figures stay laid out as they are and
 * the dust covers exactly them, so nothing changes size or moves; figures and
 * dust cross-fade in place. A tap on the dust shows the amounts.
 */
@Composable
internal fun Modifier.amountDust(layout: State<TextLayoutResult?>, ranges: List<IntRange>, color: Color): Modifier {
    if (ranges.isEmpty()) return this
    val hidden = LocalAmountsHidden.current
    val reveal by rememberUpdatedState(LocalRevealAmounts.current)
    val dust = animateFloatAsState(if (hidden) 1f else 0f, dustFade(hidden), label = "dust")
    val clock = LocalDustTime.current
    val tap = if (!hidden) Modifier else Modifier.pointerInput(ranges) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val boxes = layout.value?.let { l -> ranges.mapNotNull { l.boxOf(it) } }.orEmpty()
            if (boxes.none { it.contains(down.position) }) return@awaitEachGesture
            down.consume()
            val up = waitForUpOrCancellation() ?: return@awaitEachGesture
            up.consume()
            reveal()
        }
    }
    return this.then(tap).drawWithCache {
        val boxes = layout.value?.let { l -> ranges.mapNotNull { l.boxOf(it) } }.orEmpty()
        val clip = Path().apply { boxes.forEach { addRect(it) } }
        val fade = Paint()
        onDrawWithContent {
            val d = dust.value
            if (d <= 0f || boxes.isEmpty()) {
                drawContent()
                return@onDrawWithContent
            }
            clipPath(clip, ClipOp.Difference) { this@onDrawWithContent.drawContent() }
            if (d < 1f) {
                fade.alpha = 1f - d
                drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, size), fade) }
                clipPath(clip) { this@onDrawWithContent.drawContent() }
                drawIntoCanvas { it.restore() }
            }
            val time = clock.value
            boxes.forEachIndexed { k, box -> drawDust(box, color, time, d, k) }
        }
    }
}

/** Where the characters of [range] are drawn within the laid-out text. */
private fun TextLayoutResult.boxOf(range: IntRange): Rect? {
    val end = minOf(range.last + 1, layoutInput.text.length)
    if (range.first >= end) return null
    val b = getPathForRange(range.first, end).getBounds()
    val box = Rect(b.left.coerceAtLeast(0f), b.top, b.right.coerceAtMost(size.width.toFloat()), b.bottom)
    return box.takeIf { it.width > 0f && it.height > 0f }
}

/**
 * Twinkling dust over [box]: every particle lives its own random time in its
 * own spot, then a new one appears elsewhere — so there is no loop to notice.
 * [strength] fades all of it; [salt] keeps neighbouring boxes different.
 */
private fun DrawScope.drawDust(box: Rect, color: Color, time: Float, strength: Float, salt: Int) {
    val base = if (color == Color.Unspecified) Color.White else color
    val cell = 6.dp.toPx()
    val n = (box.width * box.height / (cell * cell)).toInt().coerceIn(8, 300)
    for (i in 0 until n) {
        val j = i + salt * 1009
        val period = 1.2f + 2.2f * rnd(j * 3 + 1)
        val phase = period * rnd(j * 3 + 2)
        val life = (time + phase) / period
        val cycle = floor(life).toInt()
        val f = life - cycle
        val alpha = sin(PI * f).toFloat() * (0.45f + 0.55f * rnd(j * 31 + cycle))
        val seed = j * 977 + cycle * 7919
        val x = box.left + rnd(seed) * box.width
        val y = box.top + rnd(seed + 1) * box.height
        val r = (0.5f + 0.7f * rnd(seed + 2)).dp.toPx()
        drawCircle(base.copy(alpha = (alpha * base.alpha * strength).coerceIn(0f, 1f)), r, Offset(x, y))
    }
}

/** Deterministic 0..1 noise from an int. */
private fun rnd(seed: Int): Float {
    val v = sin(seed * 12.9898) * 43758.5453
    return (v - floor(v)).toFloat()
}

/**
 * Two fingers swiped down across the screen → [onSwipe]. Watches without
 * consuming, so scrolling and taps under it work as usual.
 */
fun Modifier.twoFingerSwipeDown(enabled: Boolean, minDistancePx: Float, onSwipe: () -> Unit): Modifier =
    if (!enabled) this else pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                var startY: Float? = null
                var fired = false
                do {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    val down = e.changes.filter { it.pressed }
                    if (down.size >= 2) {
                        val y = down.map { it.position.y }.average().toFloat()
                        val s = startY ?: y.also { startY = it }
                        if (!fired && y - s >= minDistancePx) { fired = true; onSwipe() }
                    } else if (down.isEmpty()) startY = null
                } while (e.changes.any { it.pressed })
            }
        }
    }
