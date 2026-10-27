package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.MoneyCompact
import ru.maxrubtsoff.bankagg.domain.model.RowKind
import ru.maxrubtsoff.bankagg.ui.theme.Amounts
import ru.maxrubtsoff.bankagg.ui.common.Motion
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * A line of the history list (F-50). The sum stands alone at the right edge; what the
 * line does is told by a small chevron at the end of the grey caption: [RowKind.GO] «›»
 * leads to an object, [RowKind.OPEN] «⌄»/«⌃» opens into parts, [RowKind.SUM] has none
 * and does not answer a touch. [highlighted] is a [Slitok.Brass2] bar at the left (F-46);
 * [nested] is a part of an opened line, on a thin guide.
 */
@Composable
fun HistoryLine(
    title: String,
    subtitle: String?,
    amountMinor: Long?,
    kind: RowKind,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    highlighted: Boolean = false,
    nested: Boolean = false,
    subtitleLines: Int = 1,
    lead: (@Composable () -> Unit)? = null,
    onClick: () -> Unit = {}
) {
    val guide = Slitok.Line
    Row(
        modifier.fillMaxWidth()
            .accentBar(highlighted)
            .drawBehind {
                if (nested) drawRect(guide, Offset(20.dp.toPx(), 0f), Size(1.dp.toPx(), size.height))
            }
            .then(if (kind == RowKind.SUM) Modifier else Modifier.clickable(onClick = onClick))
            .padding(start = if (nested) 32.dp else 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        lead?.invoke()
        Column(Modifier.weight(1f)) {
            val chevron: @Composable () -> Unit = {
                Icon(
                    when {
                        kind == RowKind.GO -> Icons.AutoMirrored.Filled.KeyboardArrowRight
                        expanded -> Icons.Filled.KeyboardArrowUp
                        else -> Icons.Filled.KeyboardArrowDown
                    },
                    contentDescription = null, tint = Slitok.Muted, modifier = Modifier.size(14.dp)
                )
            }
            // No line under the title: the chevron closes the title instead of hanging alone below it.
            val inTitle = subtitle == null && kind != RowKind.SUM
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge, color = Slitok.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (inTitle) { Spacer(Modifier.width(4.dp)); chevron() }
            }
            if (subtitle != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MoneyText(subtitle, Modifier.weight(1f, fill = false), MaterialTheme.typography.bodySmall, Slitok.Muted, maxLines = subtitleLines)
                    if (kind != RowKind.SUM) chevron()
                }
            }
        }
        amountMinor?.let {
            MoneyText(signedSum(it), style = Amounts.Small, color = if (it > 0L) Slitok.Mint else Slitok.Fg, compact = MoneyCompact.short(it, signed = true))
        }
    }
}

/** "+13 622 ₽" / "−4 000 ₽" with a real minus sign. */
fun signedSum(minor: Long): String = when {
    minor > 0L -> "+" + Money.ofMinor(minor).format()
    minor < 0L -> "−" + Money.ofMinor(-minor).format()
    else -> Money.ZERO.format()
}

/**
 * A [Slitok.Brass2] bar, 3 dp, down the left edge — the one «look here» mark of the
 * history line and the deposit row (U-68). Fades in over [Motion.NORMAL], out over [Motion.FAST].
 */
@Composable
fun Modifier.accentBar(active: Boolean): Modifier {
    val bar by animateColorAsState(
        if (active) Slitok.Brass2 else Color.Transparent,
        Motion.tween(if (active) Motion.NORMAL else Motion.FAST), label = "bar"
    )
    return drawBehind { if (bar.alpha > 0f) drawRect(bar, Offset.Zero, Size(3.dp.toPx(), size.height)) }
}
