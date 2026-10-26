package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.VectorConverter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.approachLayout
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.round
import kotlinx.coroutines.launch

/**
 * A row of a sorted list moves to its new place when the order changes (U-27),
 * instead of jumping there. Rows sit in a [LookaheadScope] and are keyed by id;
 * with animations off they just jump.
 */
fun Modifier.reorderable(scope: LookaheadScope, active: () -> Boolean = { true }): Modifier = if (!Cascade.enabled) this else composed {
    val coroutines = rememberCoroutineScope()
    var offset: Animatable<IntOffset, AnimationVector2D>? by remember { mutableStateOf(null) }
    approachLayout(
        isMeasurementApproachInProgress = { false },
        isPlacementApproachInProgress = { offset?.isRunning == true }
    ) { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            val coords = coordinates
            if (coords == null) {
                placeable.place(0, 0)
                return@layout
            }
            val (target, current) = with(scope) {
                lookaheadScopeCoordinates.localLookaheadPositionOf(coords).round() to
                    lookaheadScopeCoordinates.localPositionOf(coords, Offset.Zero).round()
            }
            val anim = offset ?: Animatable(target, IntOffset.VectorConverter).also { offset = it }
            if (!active()) {
                // Shifts not caused by a new order (a group opening) are driven by the layout itself.
                if (anim.targetValue != target) coroutines.launch { anim.snapTo(target) }
                placeable.place(0, 0)
                return@layout
            }
            if (anim.targetValue != target) {
                coroutines.launch { anim.animateTo(target, Motion.tween(CLOSE_MS)) }
            }
            val shift = anim.value - current
            placeable.place(shift.x, shift.y)
        }
    }
}
