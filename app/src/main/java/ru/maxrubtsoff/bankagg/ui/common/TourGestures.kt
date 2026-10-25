package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/** Size of the finger pointer. */
private val FINGER_SIZE = 44.dp

/**
 * The finger of a tour (F-32): a round cursor that moves to [target] and
 * presses there. [target] is the point to touch in px, in the coordinates of the
 * box it is placed in; null — the finger is away (it fades out where it was).
 * It travels in [Motion.NORMAL] and presses in [Motion.FAST]; with «Remove
 * animations» on it just stands where it is told.
 */
@Composable
fun FingerPointer(target: Offset?, modifier: Modifier = Modifier, pressed: Boolean = false) {
    val density = LocalDensity.current
    val half = with(density) { FINGER_SIZE.toPx() / 2 }
    // Where the finger was last told to be, so it fades out in place instead of flying off.
    val at by animateOffsetAsState(target ?: Offset.Zero, Motion.tween(Motion.NORMAL, Motion.Standard), label = "fingerAt")
    val shown by animateFloatAsState(if (target != null) 1f else 0f, Motion.tween(Motion.FAST), label = "fingerShown")
    val press by animateFloatAsState(if (pressed) 1f else 0f, Motion.tween(Motion.FAST), label = "fingerPress")
    Box(
        modifier
            .offset { IntOffset((at.x - half).roundToInt(), (at.y - half).roundToInt()) }
            .size(FINGER_SIZE)
            .graphicsLayer {
                alpha = shown
                // A press sinks the finger a little, like a real touch.
                val s = 1f - 0.18f * press
                scaleX = s
                scaleY = s
            }
            .background(Slitok.Fg.copy(alpha = 0.22f + 0.2f * press), CircleShape)
            .border(2.dp, Slitok.Brass2, CircleShape)
    )
}
