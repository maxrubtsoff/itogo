package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.SummaryLine
import ru.maxrubtsoff.bankagg.ui.theme.Amounts
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * A row of the Overview summary, one look for a part and for a bank: a 40 dp
 * tile, then two lines — the name and the sum, under them what it holds and how
 * it moves. The second line is always there (blank when there is nothing to
 * say), so every row is of one height and the sums stand in one column.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SummaryRow(
    tile: @Composable () -> Unit,
    title: String,
    valueMinor: Long,
    line: SummaryLine,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tile()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title, style = MaterialTheme.typography.bodyLarge,
                    color = if (dimmed) Slitok.Muted else Slitok.Fg,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(0.42f)
                )
                Spacer(Modifier.width(10.dp))
                AmountText(
                    Money.ofMinor(valueMinor).format(),
                    style = Amounts.Row,
                    color = if (dimmed) Slitok.Faint else Slitok.Fg,
                    minSize = 11.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.58f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoneyText(
                    line.detail.ifEmpty { " " },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (line.warn) Slitok.Brass2 else Slitok.Faint,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                line.moveMinor?.let { m ->
                    Spacer(Modifier.width(8.dp))
                    MoneyText(
                        signed(m) + if (line.perMonth) " в месяц" else "",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        // As DeltaLine: growth is mint, a fall is the text colour.
                        color = if (m > 0) Slitok.Mint else Slitok.Fg,
                        maxLines = 1
                    )
                }
            }
        }
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Slitok.Faint)
        else Spacer(Modifier.width(24.dp))
    }
}

/** The tile of a part (or the rest): its icon on a soft square of its colour. */
@Composable
fun SummaryTile(icon: ImageVector, color: Color, dimmed: Boolean = false) {
    val c = if (dimmed) Slitok.Faint else color
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(c.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = c, modifier = Modifier.size(22.dp))
    }
}

private fun signed(minor: Long): String = when {
    minor > 0 -> "+" + Money.ofMinor(minor).format()
    else -> "−" + Money.ofMinor(-minor).format()
}
