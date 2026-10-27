package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import kotlin.math.roundToInt

private val GAP = 4.dp

/**
 * The switch above a pager of sections (U-11): one for Capital's sections and
 * Cashback's modes. The brass pill follows the finger while the pages swipe;
 * a tap scrolls there through [onSelect].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PagerTabs(labels: List<String>, pager: PagerState, onSelect: (Int) -> Unit) =
    Tabs(labels, pager.currentPage + pager.currentPageOffsetFraction, onSelect)

/** The same switch without a pager: the pill slides to [selected]. */
@Composable
fun PagerTabsStatic(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val position by androidx.compose.animation.core.animateFloatAsState(selected.toFloat(), label = "tabs")
    Tabs(labels, position, onSelect)
}

/** Light text tabs without a pill: the chosen one is brass with an underline, the rest are muted. */
@Composable
fun ThinTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        labels.forEachIndexed { i, label ->
            val active = i == selected
            val tint by animateColorAsState(if (active) Slitok.Brass2 else Slitok.Muted, label = "thinTint")
            Column(
                // The underline spans its label only: a plain fillMaxWidth there took the whole row,
                // so the first tab pushed the others out of sight.
                Modifier.width(IntrinsicSize.Max).clip(RoundedCornerShape(6.dp)).clickable { onSelect(i) }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    label, style = MaterialTheme.typography.labelLarge, color = tint,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Box(
                    Modifier.padding(top = 4.dp).fillMaxWidth().height(2.dp)
                        .background(if (active) Slitok.Brass2 else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun Tabs(labels: List<String>, rawPosition: Float, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Slitok.Inset)
            .border(1.dp, Slitok.Line, shape)
            .padding(4.dp)
    ) {
        val n = labels.size
        val segment = (maxWidth - GAP * (n - 1)) / n
        val position = rawPosition.coerceIn(0f, (n - 1).toFloat())
        val step = with(androidx.compose.ui.platform.LocalDensity.current) { (segment + GAP).toPx() }
        // One size for every tab: the largest at which the longest label still fits its segment.
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val base = MaterialTheme.typography.labelLarge
        val room = with(androidx.compose.ui.platform.LocalDensity.current) { (segment - 8.dp).toPx() }
        val fit = remember(labels, room) {
            var sp = base.fontSize.value
            while (sp > 10f && labels.any {
                    measurer.measure(it, base.copy(fontSize = sp.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, softWrap = false).size.width > room
                }) sp -= 0.5f
            sp
        }
        Box(
            Modifier
                .matchParentSize()
        ) {
            Box(
                Modifier
                    .offset { IntOffset((position * step).roundToInt(), 0) }
                    .width(segment)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Slitok.BrassPill)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
            labels.forEachIndexed { i, label ->
                val active = position.roundToInt() == i
                val tint by animateColorAsState(if (active) Slitok.Brass2 else Slitok.Muted, label = "tabTint")
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Transparent)
                        .clickable { onSelect(i) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = base,
                        fontSize = fit.sp,
                        color = tint,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
