package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.ui.theme.Slitok

/**
 * «Банк» (U-39): the one way to pick a single bank for a form — the person's
 * banks in a sheet, the picked one checked; a search when the list is long.
 * Opened from a [SelectRow]. With [onClear] the first row is «Не указан» — the
 * bank can be taken off again (checked while nothing is picked). With [banks] only
 * those are listed (U-69: an account is a bank + the operation's currency, one per bank).
 */
@Composable
fun BankPickSheet(selected: BankBrand?, onPick: (BankBrand) -> Unit, onDismiss: () -> Unit, onClear: (() -> Unit)? = null, banks: List<BankBrand>? = null) {
    val all = banks ?: pickable(LocalMyBanks.current, selected)
    var query by rememberSaveable { mutableStateOf("") }
    val shown = if (query.isBlank()) all else all.filter { it.title.contains(query.trim(), ignoreCase = true) }
    SlitokSheet(onDismiss) {
        Text("Банк", style = MaterialTheme.typography.titleLarge, color = Slitok.Fg)
        if (all.size > SEARCH_FROM) SlitokField(query, { query = it }, placeholder = "Найти банк")
        val clearRow = onClear != null && query.isBlank()
        // A search that finds nothing leaves no empty frame behind.
        if (shown.isNotEmpty() || clearRow) {
            SheetList {
                if (clearRow) {
                    SheetRow("Не указан", selected = selected == null, onClick = { onClear?.invoke(); onDismiss() })
                }
                shown.forEach { b ->
                    SheetRow(
                        b.title,
                        leading = { BankIcon(b, size = 36.dp) },
                        selected = b.key == selected?.key,
                        onClick = { onPick(b); onDismiss() }
                    )
                }
            }
        }
    }
}

private const val SEARCH_FROM = 6
