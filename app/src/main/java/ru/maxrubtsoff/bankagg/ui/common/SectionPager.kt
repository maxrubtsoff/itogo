package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Sections that swipe sideways (Capital, Cashback modes). The swipe is ours, not
 * the pager's: the finger moves the pager only as far as its pages go, so past
 * the first or the last section nothing moves at all — no stretch, no shake
 * (B-17, B-24). A single section doesn't swipe. Each page keeps its vertical scroll.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SectionPager(
    state: PagerState,
    modifier: Modifier = Modifier,
    gutter: Dp = 16.dp,
    swipe: Boolean = true,
    content: @Composable (page: Int) -> Unit
) {
    HorizontalPager(
        state = state,
        modifier = modifier.bleed(gutter).then(if (swipe) Modifier.sectionSwipe(state) else Modifier),
        contentPadding = PaddingValues(0.dp),
        pageSpacing = 0.dp,
        userScrollEnabled = false,
        verticalAlignment = Alignment.Top
    ) { page -> Box(Modifier.padding(horizontal = gutter)) { content(page) } }
}

/** The sideways swipe of [SectionPager], for any area: the graph takes its own drag first, so it never turns the page. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.sectionSwipe(state: PagerState): Modifier = composed {
    val scope = rememberCoroutineScope()
    val flingPx = with(LocalDensity.current) { FLING_VELOCITY.toPx() }
    if (state.pageCount < 2) Modifier else Modifier.pointerInput(state) {
        val tracker = VelocityTracker()
        detectHorizontalDragGestures(
            onDragStart = { tracker.resetTracking() },
            onDragEnd = {
                val v = tracker.calculateVelocity().x
                val from = state.currentPage
                val offset = state.currentPageOffsetFraction
                val target = when {
                    v < -flingPx -> from + if (offset >= 0f) 1 else 0
                    v > flingPx -> from - if (offset <= 0f) 1 else 0
                    abs(offset) > 0.5f -> from + if (offset > 0f) 1 else -1
                    else -> from
                }.coerceIn(0, state.pageCount - 1)
                scope.launch { state.animateScrollToPage(target) }
            },
            onDragCancel = { scope.launch { state.animateScrollToPage(state.currentPage) } }
        ) { change, dx ->
            tracker.addPosition(change.uptimeMillis, change.position)
            change.consume()
            // The pager clamps at its first and last page: beyond them the content stays put.
            state.dispatchRawDelta(-dx)
        }
    }
}

private val FLING_VELOCITY = 400.dp

/**
 * B-43: a pager sits inside a screen with side margins [gutter]; it reaches out
 * over them to the screen's edges, and each page takes the margins inside. So
 * the pages never clip at the margin, and between two pages there is a gap of
 * two margins — they read as separate screens while you swipe.
 */
fun Modifier.bleed(gutter: Dp): Modifier = layout { measurable, constraints ->
    val extra = (gutter * 2).roundToPx()
    val wide = if (constraints.hasBoundedWidth) constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra) else constraints
    val placeable = measurable.measure(wide)
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
}
