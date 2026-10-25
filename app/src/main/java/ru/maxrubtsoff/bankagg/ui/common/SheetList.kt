package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

private val GroupShape = RoundedCornerShape(16.dp)

/**
 * A list inside a sheet (U-49): the rows sit in one inset block with a hairline
 * between them. Fill it with [SheetRow]s; one block per kind of choice, a quiet
 * extra action («Из файла…») in a block of its own.
 */
@Composable
fun SheetList(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxWidth().clip(GroupShape).background(Slitok.Inset)) {
        Column(Modifier.fillMaxWidth(), content = content)
        // The frame is drawn over the rows, so the last row's hairline hides under it.
        Box(Modifier.matchParentSize().border(1.dp, Slitok.Line, GroupShape))
    }
}

/**
 * One row of a [SheetList]: [title] with a quiet [subtitle] under it, a [leading]
 * picture (a bank, or [SheetIconTile]), a [trailing] tag, and at the end either
 * the tick of the [selected] row (brass, with the title) or the arrow of a row
 * that opens something ([arrow]).
 */
@Composable
fun SheetRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    arrow: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val y = size.height - 0.5.dp.toPx()
                drawLine(Slitok.Line, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 52.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) Slitok.Brass2 else Slitok.Fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Slitok.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Выбрано", tint = Slitok.Brass2, modifier = Modifier.size(20.dp))
        } else if (arrow) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Slitok.Faint, modifier = Modifier.size(18.dp))
        }
    }
}

/** The small tile for a row's picture when it is not a bank: a quiet icon on a faint square, 32 dp. */
@Composable
fun SheetIconTile(icon: ImageVector) {
    Box(
        Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Slitok.Fg.copy(alpha = 0.04f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Slitok.Muted, modifier = Modifier.size(18.dp))
    }
}
