package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.SortOrder
import ru.maxrubtsoff.bankagg.ui.common.SlitokSheet
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * One control for every sorted list (U-12): a small arrow button that flips the
 * direction, and the sort's name that opens [SortSheet].
 */
@Composable
fun SortControl(label: String, reversed: Boolean, directionLabel: String, onFlip: () -> Unit, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val flip by animateFloatAsState(if (reversed) -1f else 1f, label = "sortArrow")
    Row(
        Modifier.height(40.dp).clip(shape).background(Slitok.Inset).border(1.dp, Slitok.Line, shape),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clickable(onClick = onFlip)
                .semantics { contentDescription = directionLabel },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                contentDescription = null,
                tint = Slitok.Brass2,
                modifier = Modifier.size(20.dp).graphicsLayer { scaleY = flip }
            )
        }
        Box(Modifier.width(1.dp).height(40.dp).background(Slitok.Line))
        Row(
            Modifier.clickable(onClick = onOpen).padding(start = 12.dp, end = 8.dp).height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = Slitok.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = Slitok.Muted, modifier = Modifier.size(18.dp))
        }
    }
}

/**
 * Picks the sort only — the direction is the arrow button's job. Each row shows
 * where the list would start; the chosen one has a tick.
 */
@Composable
fun <S> SortSheet(
    options: List<S>,
    order: SortOrder<S>,
    title: (S) -> String,
    direction: (SortOrder<S>) -> String,
    onPick: (S) -> Unit,
    onDismiss: () -> Unit
) {
    SlitokSheet(onDismiss) {
        Text("Сортировать", style = MaterialTheme.typography.titleLarge)
        Column {
            options.forEach { sort ->
                val active = sort == order.sort
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPick(sort) }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(30.dp)) {
                        if (active) Icon(Icons.Filled.Check, contentDescription = null, tint = Slitok.Brass2)
                    }
                    Text(
                        title(sort),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (active) Slitok.Brass2 else Slitok.Fg,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        direction(if (active) order else SortOrder(sort)),
                        style = MaterialTheme.typography.bodySmall,
                        color = Slitok.Muted
                    )
                }
            }
        }
    }
}
