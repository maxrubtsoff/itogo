package ru.maxrubtsoff.bankagg.ui.history

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.Currencies
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.HistoryEvent
import ru.maxrubtsoff.bankagg.domain.usecase.OperationDetail
import ru.maxrubtsoff.bankagg.ui.capital.BalanceSheet
import ru.maxrubtsoff.bankagg.ui.common.BankIcon
import ru.maxrubtsoff.bankagg.ui.common.MoneyText
import ru.maxrubtsoff.bankagg.ui.common.SheetList
import ru.maxrubtsoff.bankagg.ui.common.SheetRow
import ru.maxrubtsoff.bankagg.ui.common.SlitokSheet
import ru.maxrubtsoff.bankagg.ui.theme.Amounts
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * «Остаток изменили вручную» (F-55): one account — straight to its balance with what the operations counted next
 * to it; several — a list of them, the changed one first. A fix is saved at once, with «Вернуть» in the snackbar.
 */
@Composable
fun BalanceFlow(event: HistoryEvent, vm: CapitalHistoryViewModel, onDismiss: () -> Unit) {
    val detail by produceState<OperationDetail?>(null, event) { value = vm.detail(event) }
    val b = detail?.balance ?: return
    var picked by remember { mutableStateOf<String?>(null) }
    val rows = b.rows
    val row = rows.singleOrNull() ?: picked?.let { id -> rows.firstOrNull { it.account.id == id } }
    if (row != null) {
        val a = row.account
        BalanceSheet(
            a, since = { vm.balanceSince(a) },
            onSave = { vm.setBalance(a.id, it, false); onDismiss() },
            onDismiss = { if (rows.size > 1) picked = null else onDismiss() },
            explained = row.explainedMinor,
            onRestore = { vm.setBalance(a.id, it, true); onDismiss() },
            heading = "Остаток · " + a.brand.title
        )
        return
    }
    SlitokSheet(onDismiss) {
        Text("Остаток · " + Currencies.nameOf(b.currency), style = MaterialTheme.typography.titleLarge, color = Slitok.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Где изменили", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
        SheetList {
            rows.forEach { r ->
                SheetRow(
                    title = r.account.brand.title,
                    subtitle = when {
                        r.explainedMinor == null -> r.account.title.takeIf { it.isNotBlank() && it != r.account.brand.title }
                        r.changed -> "по операциям " + Money.ofMinor(r.explainedMinor).format(b.currency)
                        else -> "совпадает"
                    },
                    leading = { BankIcon(r.account.brand, 36.dp) },
                    trailing = { MoneyText(r.account.balance.format(b.currency), style = Amounts.Small, color = Slitok.Fg) },
                    arrow = true,
                    onClick = { picked = r.account.id }
                )
            }
        }
    }
}
