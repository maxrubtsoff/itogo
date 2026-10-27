package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.maxrubtsoff.bankagg.domain.model.BankSlice
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.ui.theme.Amounts
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * What a bank holds (F-53): its sum, accounts and deposits with their sums, and
 * ways on — its subscriptions in «Платежи», its cashback — shown only where
 * there is something to open.
 */
@Composable
fun BankSheet(
    slice: BankSlice,
    onDismiss: () -> Unit,
    onOpenSubscriptions: () -> Unit,
    onOpenCashback: () -> Unit
) {
    val brand = slice.brand ?: return
    SlitokSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BankIcon(brand, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(brand.title, style = MaterialTheme.typography.titleLarge, color = Slitok.Fg, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                AmountText(Money.ofMinor(slice.valueMinor).format(), style = Amounts.Large, minSize = 14.sp, textAlign = TextAlign.Start)
            }
        }
        Spacer(Modifier.height(4.dp))
        if (slice.accounts.isNotEmpty()) Lines("Счета", slice.accounts)
        if (slice.deposits.isNotEmpty()) Lines("Вклады", slice.deposits)
        if (slice.hasSubscriptions || slice.hasCashback) {
            SheetList {
                if (slice.hasSubscriptions) SheetRow("Подписки этого банка", arrow = true, onClick = onOpenSubscriptions)
                if (slice.hasSubscriptions && slice.hasCashback) HorizontalDivider(color = Slitok.Line)
                if (slice.hasCashback) SheetRow("Кэшбэк этого банка", arrow = true, onClick = onOpenCashback)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun Lines(title: String, lines: List<BankSlice.Line>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
        SheetList {
            lines.forEachIndexed { i, l ->
                if (i > 0) HorizontalDivider(color = Slitok.Line)
                SheetRow(
                    l.title,
                    trailing = { AmountText(Money.ofMinor(l.valueMinor).format(), style = Amounts.Small, minSize = 10.sp) }
                )
            }
        }
    }
}
