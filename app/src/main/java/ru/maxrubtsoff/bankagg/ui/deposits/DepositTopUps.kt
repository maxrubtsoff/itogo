package ru.maxrubtsoff.bankagg.ui.deposits

import ru.maxrubtsoff.bankagg.ui.theme.Amounts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.model.DepositTopUp
import ru.maxrubtsoff.bankagg.ui.common.SlitokSheet
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.LongPressMenu
import ru.maxrubtsoff.bankagg.ui.common.MoneyText
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.PlateButton
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import java.time.LocalDate

/** «Пополнить» and the list of top-ups under it (F-20); a long press removes one. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopUpsBlock(d: Deposit, today: LocalDate, onTopUp: () -> Unit, onDelete: (DepositTopUp) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (d.topUps.isNotEmpty()) Panel(Modifier.fillMaxWidth(), radius = 20.dp) {
            Text("Пополнения", style = MaterialTheme.typography.titleSmall, color = Slitok.Muted, modifier = Modifier.padding(start = 16.dp, top = 14.dp))
            d.topUps.sortedBy { it.date }.forEachIndexed { i, t ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Slitok.Line)
                LongPressMenu(items = listOf("Удалить" to { onDelete(t) }), danger = setOf("Удалить")) { onLong ->
                    Row(
                        Modifier.fillMaxWidth().combinedClickable(onClick = {}, onLongClick = onLong).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            formatDate(t.date) + if (t.date.isAfter(today)) " · запланировано" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (t.date.isAfter(today)) Slitok.Muted else Slitok.Fg,
                            modifier = Modifier.weight(1f)
                        )
                        MoneyText(
                            "+" + t.amount.format(),
                            style = Amounts.Row,
                            fontWeight = FontWeight.SemiBold,
                            color = Slitok.VioletText
                        )
                    }
                }
            }
        }
        if (!today.isAfter(d.endDate)) PlateButton(onClick = onTopUp, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("Пополнить")
        }
    }
}

/** The sum and the day (today unless changed), inside the deposit's term. */
@Composable
fun TopUpSheet(d: Deposit, today: LocalDate, onSave: (Long, LocalDate) -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(today.coerceIn(d.startDate, d.endDate.minusDays(1).coerceAtLeast(d.startDate))) }
    val money = parseRubles(amount)
    val dateOk = !date.isBefore(d.startDate) && date.isBefore(d.endDate)
    SlitokSheet(onDismiss) {
        Text("Пополнить вклад", style = MaterialTheme.typography.titleLarge, color = Slitok.Fg)
        AmountField("Сумма", amount, { amount = it }, placeholder = "0")
        DateField("Дата", date) { date = it }
        BrassButton(
            onClick = { money?.let { onSave(it.minor, date) } },
            enabled = money != null && money.minor > 0 && dateOk,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Пополнить") }
    }
}
