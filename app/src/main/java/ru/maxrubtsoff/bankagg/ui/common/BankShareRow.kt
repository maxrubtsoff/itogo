package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.maxrubtsoff.bankagg.domain.model.BankSlice
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.ui.theme.Amounts
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * A bank in the capital cut (F-53): its logo, name and sum, and a thin bar of
 * its share in the colour of its logo. [onClick] null — the row only informs
 * (the rest that is not in any bank).
 */
@Composable
fun BankShareRow(slice: BankSlice, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val brand = slice.brand
    val accent = if (brand != null) rememberBankAccent(brand) else Slitok.Faint
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (brand != null) BankIcon(brand, size = 36.dp)
        else Box(Modifier.size(36.dp).clip(CircleShape).background(Slitok.Inset))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    brand?.title ?: "Не в банке", style = MaterialTheme.typography.bodyLarge,
                    color = if (brand != null) Slitok.Fg else Slitok.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(10.dp))
                AmountText(
                    Money.ofMinor(slice.valueMinor).format(),
                    style = Amounts.Row,
                    minSize = 11.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(99.dp)).background(Slitok.Inset)) {
                if (slice.share > 0f) Box(
                    Modifier.fillMaxWidth(slice.share.coerceIn(0.02f, 1f)).fillMaxHeight()
                        .clip(RoundedCornerShape(99.dp)).background(accent)
                )
            }
        }
    }
}
