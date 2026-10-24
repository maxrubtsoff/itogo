package ru.maxrubtsoff.bankagg.ui.cashback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.CashbackMonth
import ru.maxrubtsoff.bankagg.ui.common.SlitokSheet
import ru.maxrubtsoff.bankagg.ui.common.pressScale
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/*
 * What the cashback's section row holds (U-58): the month on the left, the view
 * (by bank / by category) next to the «+».
 */

/** «Октябрь ▾»: the month of the cashback in view. */
@Composable
internal fun MonthChip(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .pressScale()
            .height(36.dp)
            .clip(shape)
            .background(Slitok.Inset)
            .border(1.dp, Slitok.Line, shape)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Slitok.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Выбрать месяц", tint = Slitok.Muted, modifier = Modifier.size(18.dp))
    }
}

/** By bank / by category, as two icons (U-47): a tap, no swipe — the swipe turns «Кэшбэк · Подписки». */
@Composable
internal fun ViewToggle(byCategory: Boolean, onChange: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.height(36.dp).clip(shape).background(Slitok.Inset).border(1.dp, Slitok.Line, shape).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        ToggleIcon(Icons.Filled.AccountBalance, "По банкам", !byCategory) { onChange(false) }
        ToggleIcon(Icons.Filled.GridView, "По категориям", byCategory) { onChange(true) }
    }
}

@Composable
private fun ToggleIcon(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 36.dp, height = 30.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (on) Slitok.BrassPill else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = if (on) Slitok.Brass2 else Slitok.Muted, modifier = Modifier.size(18.dp))
    }
}

/** The months to pick from: half a year back and the next one. */
@Composable
internal fun MonthSheet(anchor: String, current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    SlitokSheet(onDismiss) {
        Text("Месяц", style = MaterialTheme.typography.titleLarge)
        Column {
            for (offset in 1 downTo -6) {
                val key = CashbackMonth.keyForOffset(anchor, offset)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPick(offset) }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        CashbackMonth.label(key),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (offset == current) Slitok.Brass2 else Slitok.Fg,
                        modifier = Modifier.weight(1f)
                    )
                    if (offset == current) Icon(Icons.Filled.Check, contentDescription = "Выбран", tint = Slitok.Brass2)
                }
            }
        }
    }
}
