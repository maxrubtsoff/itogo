package ru.maxrubtsoff.bankagg.ui.capital

import ru.maxrubtsoff.bankagg.ui.common.MoneyText
import ru.maxrubtsoff.bankagg.ui.common.AmountField
import ru.maxrubtsoff.bankagg.ui.common.SlitokSheet
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.Currencies
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.RuDays
import ru.maxrubtsoff.bankagg.ui.common.BankIcon
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** "15 октября" — the day a balance was last exact. */
internal fun checkedDay(at: Long, today: LocalDate = LocalDate.now()): String =
    RuDays.relative(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate(), today)

/**
 * «Сейчас на счёте» (B-29): the balance from the bank's app becomes the new
 * starting point. Under the field — how the app's own count came out:
 * the last exact balance, what was added since, what it should be now.
 */
@Composable
fun BalanceSheet(
    account: Account, since: suspend () -> Pair<Long, Long>?, onSave: (Long) -> Unit, onDismiss: () -> Unit,
    /** The history's fix (F-55): what the operations alone counted; shown beside «Сейчас» with a button to put it back. */
    explained: Long? = null,
    onRestore: ((Long) -> Unit)? = null,
    heading: String? = null
) {
    val start = if (account.needsBalance) "" else amountInput(account.balance.minor)
    var text by remember { mutableStateOf(TextFieldValue(start, TextRange(0, start.length))) }
    var breakdown by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    LaunchedEffect(account.id) { if (account.estimated) breakdown = since() }
    val input = parseAmountMinor(text.text)

    SlitokSheet(onDismiss, imeField = true) {
        if (heading != null) {
            Text(heading, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (explained != null) {
                Line("По операциям", Money.ofMinor(explained).format(account.currency))
                Line("Сейчас", account.balance.format(account.currency), strong = true)
            }
        } else Row(verticalAlignment = Alignment.CenterVertically) {
            BankIcon(account.brand, size = 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(account.title.ifBlank { account.brand.title }, style = MaterialTheme.typography.titleLarge)
                Text("Остаток из приложения банка", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
            }
        }
        Text("Сейчас на счёте", style = MaterialTheme.typography.labelMedium, color = Slitok.Muted)
        AmountField(text, { text = it }, Currencies.symbolOf(account.currency), autoFocus = true)
        breakdown?.let { (checked, added) ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Slitok.Inset).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Line("Сверено ${account.checkedAt?.let { checkedDay(it) }.orEmpty()}", Money.ofMinor(checked).format(account.currency))
                Line("Операции после", (if (added > 0) "+" else "") + Money.ofMinor(added).format(account.currency))
                Line("Должно быть сейчас", account.balance.format(account.currency), strong = true)
            }
        }
        if (explained != null && onRestore != null && explained != account.balance.minor) {
            ru.maxrubtsoff.bankagg.ui.common.PlateButton({ onRestore(explained) }, Modifier.fillMaxWidth()) {
                Text("Вернуть " + Money.ofMinor(explained).format(account.currency), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
        BrassButton(onClick = { input?.let(onSave) }, Modifier.fillMaxWidth(), enabled = input != null) { Text("Сохранить") }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Line(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (strong) Slitok.Fg else Slitok.Muted,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal)
        MoneyText(value, style = MaterialTheme.typography.bodyMedium, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1, softWrap = false)
    }
}
