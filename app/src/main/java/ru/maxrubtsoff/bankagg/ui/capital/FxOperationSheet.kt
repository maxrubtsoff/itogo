package ru.maxrubtsoff.bankagg.ui.capital

import ru.maxrubtsoff.bankagg.ui.common.DangerButton
import ru.maxrubtsoff.bankagg.ui.common.SlitokDatePicker
import ru.maxrubtsoff.bankagg.ui.common.AmountField
import ru.maxrubtsoff.bankagg.ui.common.SlitokSheet
import ru.maxrubtsoff.bankagg.ui.common.SearchField

import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.domain.model.Currencies
import ru.maxrubtsoff.bankagg.domain.model.FxKind
import ru.maxrubtsoff.bankagg.domain.model.FxMath
import ru.maxrubtsoff.bankagg.domain.model.FxOperation
import ru.maxrubtsoff.bankagg.domain.model.FxPortfolio
import ru.maxrubtsoff.bankagg.domain.model.FxRate
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.ui.common.AmountGrouping
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import ru.maxrubtsoff.bankagg.ui.theme.Unbounded
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** Which of "курс" / "₽" the user typed last — the other one follows it. */
private enum class Main { RATE, RUB }

/**
 * Purchase or sale of cash currency in a sheet. Amount gets focus at once; the
 * rate and the ruble sum fill each other live (whichever was typed last
 * leads). A chip offers the CBR rate on the chosen date. A sale that the
 * history can't cover is blocked with a plain message.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FxOperationSheet(vm: CapitalViewModel, request: FxSheetRequest, onDone: () -> Unit) {
    val ops by vm.operations.collectAsStateWithLifecycle()
    val today by vm.todayDate.collectAsStateWithLifecycle()
    val existing = remember(request.editId, ops) { request.editId?.let { id -> ops.firstOrNull { it.id == id } } }
    val kind = existing?.kind ?: request.kind
    val scope = rememberCoroutineScope()

    var currency by rememberSaveable { mutableStateOf(existing?.currency ?: request.currency ?: "USD") }
    var amountText by remember { mutableStateOf(TextFieldValue(existing?.let { amountInput(it.amountMinor) } ?: "")) }
    var dateDay by rememberSaveable { mutableStateOf((existing?.date ?: today).toEpochDay()) }
    var rateText by rememberSaveable { mutableStateOf(existing?.let { FxMath.formatRate(it.rate).replace(" ", "") } ?: "") }
    var rubText by rememberSaveable { mutableStateOf(existing?.let { amountInput(it.rubMinor) } ?: "") }
    var main by rememberSaveable { mutableStateOf(Main.RATE) }
    var picking by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var cbr by remember { mutableStateOf<FxRate?>(null) }

    val date = LocalDate.ofEpochDay(dateDay)
    val amountMinor = parseAmountMinor(amountText.text)?.takeIf { it > 0 }
    val rate = FxMath.parseRate(rateText)
    val rubMinor = parseAmountMinor(rubText)?.takeIf { it > 0 }

    // The follower field is recomputed from the leader whenever inputs change.
    LaunchedEffect(amountMinor, rateText, rubText, main) {
        if (amountMinor == null) return@LaunchedEffect
        when (main) {
            Main.RATE -> rate?.let { r ->
                val rub = amountInput(FxMath.rubOf(amountMinor, r))
                if (rub != rubText) rubText = rub
            }
            Main.RUB -> rubMinor?.let { rm ->
                val r = FxMath.formatRate(FxMath.rateOf(amountMinor, rm)).replace(" ", "")
                if (r != rateText) rateText = r
            }
        }
    }
    LaunchedEffect(currency, dateDay) { cbr = null; cbr = vm.cbrRateOn(currency, date) }

    val candidate: FxOperation? = run {
        val a = amountMinor ?: return@run null
        val rub = when (main) {
            Main.RATE -> rate?.let { FxMath.rubOf(a, it) }
            Main.RUB -> rubMinor
        } ?: return@run null
        FxOperation(
            id = existing?.id ?: "new",
            currency = currency, kind = kind, date = date,
            amountMinor = a, rubMinor = rub,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
    }
    val check = candidate?.let { FxPortfolio.checkSave(ops, it) }
    val blocked = (check as? FxPortfolio.Check.Blocked)?.message

    SlitokSheet(onDone, scroll = true, imeField = existing == null) {
        Text(
            when {
                existing != null && kind == FxKind.BUY -> "Покупка"
                existing != null -> "Продажа"
                kind == FxKind.BUY -> "Покупка валюты"
                else -> "Продажа валюты"
            },
            style = MaterialTheme.typography.titleLarge
        )
        Text("наличная валюта", style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ModeChip("${Currencies.symbolOf(currency)} $currency", active = true) { picking = true }
            ModeChip(date.format(DATE_FMT), active = false) { pickingDate = true }
        }

        Label("Сколько")
        AmountField(amountText, { amountText = it }, Currencies.symbolOf(currency), autoFocus = existing == null)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label("Курс")
                SmallNumberField(rateText, { rateText = it; main = Main.RATE }, "₽", active = main == Main.RATE)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Label(if (kind == FxKind.BUY) "Отдал" else "Получил")
                SmallNumberField(rubText, { rubText = it; main = Main.RUB }, "₽", active = main == Main.RUB)
            }
        }
        cbr?.let { r ->
            ModeChip("ЦБ на %02d.%02d: %s".format(r.date.dayOfMonth, r.date.monthValue, FxMath.formatRate(r.rubPerUnit)), active = false) {
                rateText = FxMath.formatRate(r.rubPerUnit).replace(" ", "")
                main = Main.RATE
            }
        }

        val shown = error ?: blocked
        if (shown != null) {
            Text(shown, style = MaterialTheme.typography.bodyMedium, color = Slitok.Coral)
        } else if (kind == FxKind.SELL && amountMinor != null) {
            val left = FxPortfolio.availableOn(ops, currency, date, existing?.id)
            Text(
                "на ${date.format(DATE_FMT)} есть ${FxMath.formatAmount(left, currency)}",
                style = MaterialTheme.typography.bodySmall, color = Slitok.Muted
            )
        }

        BrassButton(
            enabled = candidate != null && blocked == null,
            onClick = {
                val op = candidate ?: return@BrassButton
                scope.launch {
                    val saved = op.copy(id = existing?.id ?: vm.newOperationId())
                    when (val r = vm.saveOperation(saved)) {
                        FxPortfolio.Check.Ok -> onDone()
                        is FxPortfolio.Check.Blocked -> error = r.message
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                candidate?.let { "Сохранить${" "}· ${Money.ofMinor(it.rubMinor).format()}" } ?: "Сохранить",
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (existing != null) {
            DangerButton(onClick = {
                scope.launch {
                    when (val r = vm.deleteOperation(existing.id)) {
                        FxPortfolio.Check.Ok -> onDone()
                        is FxPortfolio.Check.Blocked -> error = r.message
                    }
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Удалить")
            }
        }
    }

    if (picking) {
        CurrencyPickerSheet(
            used = ops.map { it.currency }.distinct(),
            onPick = { currency = it; picking = false },
            onDismiss = { picking = false }
        )
    }
    if (pickingDate) {
        SlitokDatePicker(date, onPick = { dateDay = it.toEpochDay() }, onDismiss = { pickingDate = false }, latest = today)
    }

}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Slitok.Muted)
}

@Composable
private fun SmallNumberField(value: String, onChange: (String) -> Unit, suffix: String, active: Boolean) {
    // U-40/A2: the app's one amount field; [active] — the field the sum is computed from.
    AmountField(value, onChange, unit = suffix, modifier = Modifier.fillMaxWidth())
}

/** Currency picker: used ones first, then USD, EUR, CNY, then the rest; search by code or name. */
@Composable
fun CurrencyPickerSheet(used: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val list = remember(query, used) { Currencies.pickerOrder(used, query) }
    SlitokSheet(onDismiss) {
        Text("Валюта", style = MaterialTheme.typography.titleLarge)
        SearchField(query, { query = it.take(30) }, "Код или название")
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
            items(list, key = { it.code }) { c ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(c.code) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CurrencyBadge(c.code, size = 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(c.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(c.code, style = MaterialTheme.typography.labelLarge, color = Slitok.Muted)
                }
            }
        }
    }
}
