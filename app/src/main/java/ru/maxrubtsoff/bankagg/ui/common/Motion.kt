package ru.maxrubtsoff.bankagg.ui.common

import android.animation.ValueAnimator
import androidx.compose.ui.zIndex
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween as rawTween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * The motion scale of «Слиток» (U-55, P8–P9): every animation takes its
 * duration and curve from here, so the same action moves the same way
 * everywhere, and the system «Remove animations» switch is honoured in one
 * place. Coming in takes a step longer than going away.
 */
object Motion {
    /** Small things: a menu, a tag, amounts hiding, a fade out. */
    const val FAST = 200

    /** Screens, cards and rows coming in, tab content, amounts showing. */
    const val NORMAL = 300

    /** Big, rare moves: a chart growing, a scan over a screenshot. */
    const val SLOW = 450

    /** Between rows that come in one after another. */
    const val STAGGER_MS = 30L

    /** Fast start, long soft landing, no bounce (U-22): things that arrive. */
    val Calm: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Even in and out: things that move from place to place. */
    val Standard: Easing = FastOutSlowInEasing

    /** False when the system «Remove animations» is on: everything snaps. */
    val enabled: Boolean get() = ValueAnimator.areAnimatorsEnabled()

    /** [ms] when animations are on, 0 when they are off. */
    fun duration(ms: Int, enabled: Boolean): Int = if (enabled) ms else 0

    /** The one way to build a timed animation: [ms] from the scale, instant with animations off. */
    fun <T> tween(ms: Int, easing: Easing = Calm): FiniteAnimationSpec<T> =
        if (enabled) rawTween(ms, easing = easing) else snap()
}

/**
 * A slight press-in (×0.97) while a finger is down. Only watches the touch —
 * the element's own click still gets it.
 */
fun Modifier.pressScale(): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = 900f), label = "press")
    // With «Remove animations» on there is no press-in at all.
    if (!Motion.enabled) return@composed this
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    pressed = e.changes.any { it.pressed }
                }
            }
        }
}

/** One step of the shake, in ms. */
private const val SHAKE_STEP_MS = 45

/** Offsets (px) the shake passes through, ending at rest. */
private val SHAKE_STEPS = listOf(10f, -8f, 6f, -4f, 0f)

/** The same shake, wider — for the PIN dots, where a mistake must be unmissable. */
private val SHAKE_STEPS_STRONG = listOf(18f, -14f, 10f, -6f, 0f)

/**
 * The «no» shake (a wrong PIN, a section that cannot be dropped): the one
 * implementation, so it follows the motion scale and stays still with
 * «Remove animations» on. Read [offset] in a graphicsLayer — see [errorShake].
 */
class ErrorShake internal constructor(private val steps: List<Float>) {
    private val x = Animatable(0f)

    /** Current horizontal offset in px. */
    val offset: Float get() = x.value

    /** Plays the shake; returns when it has settled. No movement when animations are off. */
    suspend fun play() {
        if (!Motion.enabled) return
        for (s in steps) x.animateTo(s, Motion.tween(SHAKE_STEP_MS, Motion.Standard))
    }
}

/** Remembers an [ErrorShake]; [strong] is the wider one. */
@Composable
fun rememberErrorShake(strong: Boolean = false): ErrorShake =
    remember(strong) { ErrorShake(if (strong) SHAKE_STEPS_STRONG else SHAKE_STEPS) }

/** Applies [shake] to the element: put it before the clipping/background modifiers. */
fun Modifier.errorShake(shake: ErrorShake): Modifier = graphicsLayer { translationX = shake.offset }

/** Draws the content without colour (the "off" state of a picture). */
fun Modifier.grayscale(): Modifier = drawWithCache {
    val paint = Paint().apply { colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) }
    onDrawWithContent {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), paint)
            drawContent()
            canvas.restore()
        }
    }
}

