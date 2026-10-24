package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.TourTarget

/**
 * Where the elements a tour points at stand on the screen (U-54): the real
 * screens mark their parts with [tourTarget]; the tour reads the bounds and
 * lights the part itself, not coordinates. Outside a tour nothing is marked.
 */
class TourTargets {
    val bounds = mutableStateMapOf<TourTarget, Rect>()

    /** The corner of each marked part, so the light round it repeats the part's own rounding (B-48). */
    val radius = mutableStateMapOf<TourTarget, Dp>()

    /** Which node wrote each part last: a node that leaves erases only its own mark. */
    private val owners = HashMap<TourTarget, Any>()

    fun mark(target: TourTarget, owner: Any, at: Rect, corner: Dp) {
        owners[target] = owner
        bounds[target] = at
        radius[target] = corner
    }

    /** The part left the screen (a sheet closed, a header shrank): the light must not stay where it was. */
    fun release(target: TourTarget, owner: Any) {
        if (owners[target] !== owner) return
        owners.remove(target)
        bounds.remove(target)
        radius.remove(target)
    }
}

/** Set only by the tour's sandbox layer: the real screens underneath don't register. */
val LocalTourTargets = compositionLocalOf<TourTargets?> { null }

/**
 * Marks a part a tour step can point at. [active] false leaves it unmarked (one
 * of many rows); [radius] is the part's own corner.
 */
@Composable
fun Modifier.tourTarget(target: TourTarget, active: Boolean = true, radius: Dp = 18.dp): Modifier {
    val targets = LocalTourTargets.current
    if (targets == null || !active) return this
    val owner = remember { Any() }
    DisposableEffect(targets, target, owner) { onDispose { targets.release(target, owner) } }
    return onGloballyPositioned { targets.mark(target, owner, it.boundsInRoot(), radius) }
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
