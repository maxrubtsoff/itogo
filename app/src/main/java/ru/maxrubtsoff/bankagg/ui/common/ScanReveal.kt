package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween as rawTween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * Recognition of a screenshot, drawn once for every place it happens (F-34,
 * and the tours, F-32): a scan line runs down the screenshot's thumbnail with
 * «Читаем скрин» under it; when reading ends the line goes out, and the fields
 * rise into their places one after another ([ScanReveal]). Values stand where
 * they will stay — nothing flies in from the side. With «Remove animations» on
 * there is no line and no rise: the result is simply there.
 */

/** Height of the glow trailing the scan line. */
private val SCAN_TRAIL = 64.dp

/**
 * The thumbnail of a screenshot with the scan line over it while [status] is
 * [ShotStatus.PROCESSING] (one pass per [Motion.SLOW], round and round), and the
 * status line under it. [content] draws the picture itself; the caller sizes
 * the thumbnail with [modifier]. [foundText] ("Нашли банк и 2 категории") takes
 * the line's place when reading is done; null — the status line just goes.
 */
@Composable
fun ScanningThumb(
    status: ShotStatus,
    modifier: Modifier = Modifier,
    readingText: String = "Читаем скрин",
    foundText: String? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val reading = status == ShotStatus.PROCESSING
    // The line fades out when reading ends instead of vanishing.
    val lineAlpha by animateFloatAsState(if (reading) 1f else 0f, Motion.tween(Motion.FAST), label = "scanAlpha")
    // The pass only runs while there is a line to draw; at rest nothing ticks.
    val pass: State<Float> =
        if (Motion.enabled && (reading || lineAlpha > 0f)) {
            rememberInfiniteTransition(label = "scan").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = InfiniteRepeatableSpec(rawTween(Motion.SLOW, easing = LinearEasing), RepeatMode.Restart),
                label = "scanPass"
            )
        } else remember { mutableStateOf(0f) }
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier
                .clip(shape)
                .border(1.dp, Slitok.Line, shape)
                .drawWithContent {
                    drawContent()
                    if (Motion.enabled && lineAlpha > 0f) {
                        val trail = SCAN_TRAIL.toPx()
                        val y = pass.value * (size.height + trail) - trail
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Slitok.Brass2.copy(alpha = 0f),
                                0.85f to Slitok.Brass2.copy(alpha = 0.22f * lineAlpha),
                                1f to Slitok.Brass2.copy(alpha = 0.9f * lineAlpha),
                                startY = y,
                                endY = y + trail
                            ),
                            topLeft = Offset(0f, y),
                            size = Size(size.width, trail)
                        )
                        drawRect(
                            color = Slitok.Brass2.copy(alpha = lineAlpha),
                            topLeft = Offset(0f, y + trail - 1.dp.toPx()),
                            size = Size(size.width, 2.dp.toPx())
                        )
                    }
                },
            content = content
        )
        Crossfade(
            targetState = if (reading) readingText to Slitok.Brass2 else foundText?.let { it to Slitok.Mint },
            animationSpec = Motion.tween(Motion.FAST),
            label = "scanStatus"
        ) { line -> if (line != null) StatusLine(line.first, line.second) }
    }
}

/**
 * The fields under a screenshot: a skeleton while [status] is
 * [ShotStatus.PROCESSING] (or null — not loaded yet), then [content] with `play`
 * true — give each field `Modifier.cascade(index, play)` and they rise one after
 * another ([Motion.STAGGER_MS] apart). The skeleton goes out as the fields come
 * in, in one place, so the screen never swaps its whole tree (B-51). A shot that
 * is already read when the screen opens gets `play` false: the result is at
 * once, never held back by an animation.
 */
@Composable
fun ScanReveal(
    status: ShotStatus?,
    modifier: Modifier = Modifier,
    skeletonHeight: Dp = 168.dp,
    content: @Composable (play: Boolean) -> Unit
) {
    var sawReading by remember { mutableStateOf(false) }
    LaunchedEffect(status) { if (status == ShotStatus.PROCESSING) sawReading = true }
    Crossfade(
        targetState = status == null || status == ShotStatus.PROCESSING,
        modifier = modifier,
        animationSpec = Motion.tween(Motion.NORMAL),
        label = "scanReveal"
    ) { waiting ->
        if (waiting) {
            val shape = RoundedCornerShape(20.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(skeletonHeight)
                    .clip(shape)
                    .background(Slitok.Inset)
                    .border(1.dp, Slitok.Line, shape)
            )
        } else {
            content(sawReading)
        }
    }
}
