package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Lists appear one row after another (U-17) — only the first time a screen is
 * shown in this launch; coming back, re-sorting or new data shows them at once.
 */
object Cascade {
    private val shown = mutableSetOf<String>()

    /** True only for the first call with [screen] in this process. */
    fun firstTime(screen: String): Boolean = synchronized(shown) { shown.add(screen) }

    /** System "remove animations": no cascade at all. */
    val enabled: Boolean get() = Motion.enabled

    const val STEP_MS = Motion.STAGGER_MS
    const val MAX_STAGGERED = 8
}

/** Row [index] of a list that [play]s its cascade: up 12 dp and in, [Motion.STAGGER_MS] after the previous one. */
fun Modifier.cascade(index: Int, play: Boolean): Modifier = if (!play || !Cascade.enabled) this else composed {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(Cascade.STEP_MS * index.coerceAtMost(Cascade.MAX_STAGGERED))
        t.animateTo(1f, Motion.tween(Motion.NORMAL))
    }
    graphicsLayer {
        alpha = t.value
        translationY = (1f - t.value) * 12.dp.toPx()
    }
}

/** Remembers once per composition whether [screen] plays its cascade now. */
@Composable
fun rememberCascade(screen: String): Boolean = remember { Cascade.firstTime(screen) }

/** A sum that rolls in from below when it grows, from above when it falls (U-19), instead of jumping. */
@Composable
fun RollingAmount(value: Long, content: @Composable (Long) -> Unit) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(Motion.tween(Motion.NORMAL)) { if (up) it / 2 else -it / 2 } + fadeIn(Motion.tween(Motion.NORMAL))) togetherWith
                (slideOutVertically(Motion.tween(Motion.FAST)) { if (up) -it / 2 else it / 2 } + fadeOut(Motion.tween(Motion.FAST)))
        },
        label = "amount"
    ) { v -> content(v) }
}
