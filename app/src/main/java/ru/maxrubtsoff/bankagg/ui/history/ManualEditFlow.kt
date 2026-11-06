package ru.maxrubtsoff.bankagg.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.HistoryEvent
import ru.maxrubtsoff.bankagg.domain.model.HistoryRef
import ru.maxrubtsoff.bankagg.domain.model.ManualSource
import ru.maxrubtsoff.bankagg.domain.model.MoneyCompact
import ru.maxrubtsoff.bankagg.domain.model.RuDays
import ru.maxrubtsoff.bankagg.ui.capital.amountInput
import ru.maxrubtsoff.bankagg.ui.capital.color
import ru.maxrubtsoff.bankagg.ui.capital.parseAmountMinor
import ru.maxrubtsoff.bankagg.ui.common.AmountField
import ru.maxrubtsoff.bankagg.ui.common.BankIcon
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.DangerButton
import ru.maxrubtsoff.bankagg.ui.common.Dot
import ru.maxrubtsoff.bankagg.ui.common.MoneyText
import ru.maxrubtsoff.bankagg.ui.common.PlateButton
import ru.maxrubtsoff.bankagg.ui.common.SlitokChip
import ru.maxrubtsoff.bankagg.ui.common.SlitokSheet
import ru.maxrubtsoff.bankagg.ui.common.signedSum
import ru.maxrubtsoff.bankagg.ui.theme.Amounts
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import java.time.LocalDate

private enum class ManualMode { VIEW, EDIT, CONFIRM }

/**
 * «Правка остатка · вручную» (B-121): the hand change of the currency looked at, put at another sum or taken out.
 * Taking it out moves the saved days before it; the balances and deals stay. «Вернуть» is in the snackbar.
 */
@Composable
fun ManualEditFlow(event: HistoryEvent, ref: HistoryRef.Manual, vm: CapitalHistoryViewModel, today: LocalDate, onDismiss: () -> Unit) {
    var mode by remember { mutableStateOf(ManualMode.VIEW) }
    val from by produceState<LocalDate?>(null, ref) { value = vm.manualFrom(ref) }
    SlitokSheet(onDismiss, imeField = mode == ManualMode.EDIT) {
        when (mode) {
            ManualMode.VIEW -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (event.bank != null) BankIcon(event.bank, 36.dp) else Dot(event.part.color())
                    Text(
                        if (ref.source == ManualSource.ACCOUNT) "Правка остатка · " + (event.subtitle ?: "счёт") else "Правка остатка · Валюта",
                        style = MaterialTheme.typography.titleMedium, color = Slitok.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                MoneyText(signedSum(ref.manualMinor), style = Amounts.Large, color = if (ref.manualMinor > 0L) Slitok.Mint else Slitok.Fg, compact = MoneyCompact.short(ref.manualMinor, signed = true))
                Text(RuDays.dayWeekday(ref.date, today) + " · внесена вручную", style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlateButton({ mode = ManualMode.EDIT }, Modifier.weight(1f)) { Text("Изменить", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    DangerButton({ mode = ManualMode.CONFIRM }, Modifier.weight(1f)) { Text("Удалить", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            ManualMode.EDIT -> {
                var plus by remember { mutableStateOf(ref.manualMinor >= 0L) }
                var amount by remember { mutableStateOf(amountInput(kotlin.math.abs(ref.manualMinor))) }
                val minor = parseAmountMinor(amount)
                Text("Изменить правку", style = MaterialTheme.typography.titleLarge, color = Slitok.Fg)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SlitokChip("Плюс", plus, { plus = true }, Modifier.weight(1f))
                    SlitokChip("Минус", !plus, { plus = false }, Modifier.weight(1f))
                }
                AmountField(amount, { amount = it }, unit = "₽", label = "Сумма правки")
                BrassButton(
                    { vm.editManual(ref, if (plus) minor!! else -minor!!); onDismiss() },
                    Modifier.fillMaxWidth(), enabled = minor != null && minor > 0L
                ) { Text("Сохранить", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                PlateButton({ mode = ManualMode.VIEW }, Modifier.fillMaxWidth()) { Text("Отмена", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            ManualMode.CONFIRM -> {
                Text("Удалить правку?", style = MaterialTheme.typography.titleLarge, color = Slitok.Fg)
                Text("Текущий остаток и сделки не изменятся", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
                val f = from
                if (f != null) {
                    Text("График пересчитается с ${RuDays.dayMonth(f, today)}", style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
                } else {
                    Text("Раньше этого дня в графике ничего нет: уйдёт только строка", style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlateButton({ mode = ManualMode.VIEW }, Modifier.weight(1f)) { Text("Оставить", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    DangerButton({ vm.removeManual(ref); onDismiss() }, Modifier.weight(1f), filled = true) { Text("Удалить правку", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}
