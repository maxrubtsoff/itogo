package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/** The app-wide shared-transition scope around the NavHost (U-16). */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The current screen's enter/exit scope, set in each route. */
val LocalNavScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * A list row and the screen it opens share [key]: the row grows into the
 * screen's card and shrinks back into its place on return — predictive back
 * follows the finger. Without a scope (previews, widgets) or with animations
 * off, it does nothing.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedCard(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val nav = LocalNavScope.current ?: return this
    if (!Motion.enabled) return this
    return with(shared) {
        this@sharedCard.sharedBounds(
            rememberSharedContentState(key),
            animatedVisibilityScope = nav,
            // Calm (U-22): one decelerating move, no spring and no overshoot.
            boundsTransform = { _, _ -> Motion.tween(OPEN_MS) },
            resizeMode = SharedTransitionScope.ResizeMode.RemeasureToBounds
        )
    }
}

/** How long a row takes to open into its screen (U-22); closing is a step shorter. */
const val OPEN_MS = Motion.NORMAL
const val CLOSE_MS = Motion.FAST

/** Fast start, long soft landing: no bounce (U-22). */
val CalmEasing = Motion.Calm
