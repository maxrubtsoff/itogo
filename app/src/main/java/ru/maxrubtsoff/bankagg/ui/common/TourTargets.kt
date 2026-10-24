package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import ru.maxrubtsoff.bankagg.domain.model.TourTarget

/**
 * Where the elements a tour points at stand on the screen (U-54): the real
 * screens mark their parts with [tourTarget]; the tour reads the bounds and
 * lights the part itself, not coordinates. Outside a tour nothing is marked.
 */
class TourTargets {
    val bounds = mutableStateMapOf<TourTarget, Rect>()
}

/** Set only by the tour's sandbox layer: the real screens underneath don't register. */
val LocalTourTargets = compositionLocalOf<TourTargets?> { null }

/** Marks a part a tour step can point at. [active] false leaves it unmarked (one of many rows). */
@Composable
fun Modifier.tourTarget(target: TourTarget, active: Boolean = true): Modifier {
    val targets = LocalTourTargets.current
    return if (targets == null || !active) this else onGloballyPositioned { targets.bounds[target] = it.boundsInRoot() }
}

/**
 * What a tour does *to* a real screen from outside, where the screen keeps the
 * state itself: the percent the example screenshot left unread is typed in
 * («Такое бывает»). Null outside a tour.
 */
class TourDrive {
    /** The text to put into the first row that has a category and no percent; null — leave it. */
    var fillPercent: String? by mutableStateOf(null)
}

val LocalTourDrive = compositionLocalOf<TourDrive?> { null }
