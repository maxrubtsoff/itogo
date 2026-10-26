package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A hero that shrinks to a strip while the list under it is scrolled (U-67): the first [range] of the scroll goes
 * into the shrinking, the rest into the list; scrolled back to the top, the hero grows again. [fraction] is 0 for
 * the full hero and 1 for the strip. The hero stays where it is - only the list moves under it. Put [connection]
 * on the parent of the hero and the list with `Modifier.nestedScroll`.
 */
@Stable
class HeroCollapse internal constructor(private val rangePx: Float) {
    private var collapsed by mutableFloatStateOf(0f)

    val fraction: Float get() = if (rangePx <= 0f) 0f else (collapsed / rangePx).coerceIn(0f, 1f)

    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy >= 0f || collapsed >= rangePx) return Offset.Zero
            // The finger goes up: the hero shrinks before the list moves.
            val take = max(dy, collapsed - rangePx)
            collapsed -= take
            return Offset(0f, take)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy <= 0f || collapsed <= 0f) return Offset.Zero
            // The list is at its top and the finger still goes down: the hero grows back.
            val give = min(dy, collapsed)
            collapsed -= give
            return Offset(0f, give)
        }
    }
}

@Composable
fun rememberHeroCollapse(range: Dp = 140.dp): HeroCollapse {
    val px = with(LocalDensity.current) { range.toPx() }
    return remember(px) { HeroCollapse(px) }
}

/** Takes [visible] (0..1) of the element's height and fades it with it: a block that goes away as the hero shrinks. */
fun Modifier.collapseHeight(visible: () -> Float): Modifier = this
    .layout { measurable, constraints ->
        val p = measurable.measure(constraints.copy(minHeight = 0))
        val h = (p.height * visible().coerceIn(0f, 1f)).roundToInt()
        layout(p.width, h) { p.placeRelative(0, 0) }
    }
    .clipToBounds()
    .graphicsLayer { alpha = visible().coerceIn(0f, 1f) }
