package ru.maxrubtsoff.bankagg.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.Currencies
import ru.maxrubtsoff.bankagg.domain.model.HistoryEvent
import ru.maxrubtsoff.bankagg.domain.model.HistoryRef
import ru.maxrubtsoff.bankagg.domain.model.HistoryTarget
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.MoneyCompact
import ru.maxrubtsoff.bankagg.domain.model.OperationForm
import ru.maxrubtsoff.bankagg.domain.model.RuDays
import ru.maxrubtsoff.bankagg.domain.usecase.OperationDetail
import ru.maxrubtsoff.bankagg.ui.capital.amountInput
import ru.maxrubtsoff.bankagg.ui.capital.color
import ru.maxrubtsoff.bankagg.ui.capital.label
import ru.maxrubtsoff.bankagg.ui.capital.parseAmountMinor
import ru.maxrubtsoff.bankagg.ui.common.AmountField
import ru.maxrubtsoff.bankagg.ui.common.BankIcon
import ru.maxrubtsoff.bankagg.ui.common.BankNote
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.DangerButton
import ru.maxrubtsoff.bankagg.ui.common.Dot
import ru.maxrubtsoff.bankagg.ui.common.LinkLine
import ru.maxrubtsoff.bankagg.ui.common.MoneyText
import ru.maxrubtsoff.bankagg.ui.common.PlateButton
import ru.maxrubtsoff.bankagg.ui.common.SlitokChip
import ru.maxrubtsoff.bankagg.ui.common.SlitokDatePicker
import ru.maxrubtsoff.bankagg.ui.common.SlitokSheet
import ru.maxrubtsoff.bankagg.ui.common.WasBecomeLine
import ru.maxrubtsoff.bankagg.ui.common.signedSum
import ru.maxrubtsoff.bankagg.ui.theme.Amounts
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private enum class Mode { VIEW, EDIT, CONFIRM }

/**
 * The sheet of an operation of the history (F-51): the line looked at — who, the sum, the day, the balance after
 * and the bank's text at once; «Изменить» turns the same sheet into a form, «Удалить» into a confirmation with
 * the figures. Interest, revaluation and the opening of a deposit are only looked at and lead on to the object.
 */
@Composable
fun OperationSheet(
    event: HistoryEvent,
    vm: CapitalHistoryViewModel,
    today: LocalDate,
    showLog: Boolean,
    aliveDeposits: Set<String>,
    onDismiss: () -> Unit,
    onOpenTarget: (HistoryTarget) -> Unit,
    onOpenLog: () -> Unit
) {
    val detail by produceState<OperationDetail?>(null, event) { value = vm.detail(event) }
    var mode by remember { mutableStateOf(Mode.VIEW) }
    // The cash edit that undoes this one goes with it, when the person asks for both.
    var both by remember { mutableStateOf(false) }
    val d = detail

    SlitokSheet(onDismiss, imeField = mode == Mode.EDIT) {
        if (d == null) {
            Text(event.title, style = MaterialTheme.typography.titleLarge, color = Slitok.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            return@SlitokSheet
        }
        when (mode) {
            Mode.VIEW -> ViewBody(
                d, today, showLog, aliveDeposits,
                onEdit = { mode = Mode.EDIT },
                onDelete = { both = false; mode = Mode.CONFIRM },
                onDeleteBoth = { both = true; mode = Mode.CONFIRM },
                onOpenTarget = { onDismiss(); onOpenTarget(it) },
                onOpenLog = { onDismiss(); onOpenLog() }
            )
            Mode.EDIT -> EditBody(d, today, vm, onBack = { mode = Mode.VIEW }, onSaved = onDismiss)
            Mode.CONFIRM -> {
                val refs = listOfNotNull(event.ref, if (both) d.pair else null)
                ConfirmBody(refs, today, vm, onBack = { mode = Mode.VIEW }, onDelete = { vm.delete(refs); onDismiss() })
            }
        }
    }
}

@Composable
private fun ViewBody(
    d: OperationDetail, today: LocalDate, showLog: Boolean, alive: Set<String>,
    onEdit: () -> Unit, onDelete: () -> Unit, onDeleteBoth: () -> Unit,
    onOpenTarget: (HistoryTarget) -> Unit, onOpenLog: () -> Unit
) {
    val e = d.event
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (d.bank != null) BankIcon(d.bank, 36.dp) else Dot(e.part.color())
        Column(Modifier.weight(1f)) {
            Text(d.who ?: e.title, style = MaterialTheme.typography.titleMedium, color = Slitok.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = if (d.who != null) listOfNotNull(e.title, d.account?.title).joinToString(" · ") else e.subtitle
            if (line != null) Text(line, style = MaterialTheme.typography.bodySmall, color = Slitok.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val own = e.foreign
        val shown = own?.minor ?: e.amountMinor
        if (shown != null) {
            val text = if (own != null) signedForeign(own.minor, own.currency) else signedSum(shown)
            MoneyText(text, style = Amounts.Large, color = if (shown > 0L) Slitok.Mint else Slitok.Fg, compact = MoneyCompact.short(shown, signed = true))
        }
        Text(RuDays.dayWeekday(e.date, today), style = MaterialTheme.typography.bodySmall, color = Slitok.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    d.balanceAfterMinor?.let {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Остаток после", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            MoneyText(Money.ofMinor(it).format(d.currency ?: Currencies.RUB), style = MaterialTheme.typography.bodyMedium, color = Slitok.Fg)
        }
    }
    d.note?.let { BankNote(it, d.noteMark, onOpen = if (showLog) onOpenLog else null) }
    val t = e.target
    if (t != null) {
        when {
            t is HistoryTarget.Deposit && t.id !in alive ->
                Text("Этого вклада больше нет", style = MaterialTheme.typography.bodySmall, color = Slitok.Faint)
            else -> LinkLine(targetLine(t, e)) { onOpenTarget(t) }
        }
    }
    if (d.form != null) {
        if (d.pair != null) {
            Text("В тот же день было обратное изменение на ту же сумму", style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PlateButton(onEdit, Modifier.weight(1f)) { Text("Изменить", maxLines = 1, overflow = TextOverflow.Ellipsis) }
            DangerButton(onDelete, Modifier.weight(1f)) { Text("Удалить", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        if (d.pair != null) {
            DangerButton(onDeleteBoth, Modifier.fillMaxWidth()) { Text("Удалить обе", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun EditBody(d: OperationDetail, today: LocalDate, vm: CapitalHistoryViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    val ref = d.event.ref
    val seed = d.form
    if (ref == null || seed == null) return
    val scope = rememberCoroutineScope()
    var income by remember { mutableStateOf(seed.income) }
    var amount by remember { mutableStateOf(amountInput(seed.amountMinor)) }
    var rub by remember { mutableStateOf(seed.rubMinor?.let { amountInput(it) } ?: "") }
    var date by remember { mutableStateOf(seed.date) }
    var accountId by remember { mutableStateOf(seed.accountId) }
    var picking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val unit = Currencies.symbolOf(d.currency ?: Currencies.RUB)
    val isFx = ref is HistoryRef.Fx
    val isTopUp = ref is HistoryRef.TopUp

    Text("Изменить", style = MaterialTheme.typography.titleLarge, color = Slitok.Fg)
    if (!isTopUp) {
        val (yes, no) = when (ref) {
            is HistoryRef.Fx -> "Покупка" to "Продажа"
            is HistoryRef.Cash -> "Добавил" to "Взял"
            else -> "Приход" to "Расход"
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SlitokChip(yes, income, { income = true }, Modifier.weight(1f))
            SlitokChip(no, !income, { income = false }, Modifier.weight(1f))
        }
    }
    AmountField(amount, { amount = it; error = null }, unit = unit, label = "Сумма")
    if (isFx) AmountField(rub, { rub = it; error = null }, unit = "₽", label = if (income) "Отдал" else "Получил")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SlitokChip(date.format(DATE_FMT), selected = false, onClick = { picking = true })
    }
    if (ref is HistoryRef.Tx && d.accounts.size > 1) {
        Text("Счёт", style = MaterialTheme.typography.labelMedium, color = Slitok.Muted)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            d.accounts.forEach { a ->
                SlitokChip(a.title, a.id == accountId, { accountId = a.id }, Modifier.fillMaxWidth(), leading = { BankIcon(a.brand, 28.dp) })
            }
        }
    }
    error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Slitok.Coral) }
    val minor = parseAmountMinor(amount)?.takeIf { it > 0L }
    BrassButton(
        onClick = {
            val sum = minor ?: return@BrassButton
            scope.launch {
                val form = OperationForm(income, sum, date, accountId, if (isFx) parseAmountMinor(rub)?.takeIf { it > 0L } else null)
                error = vm.save(ref, form)
                if (error == null) onSaved()
            }
        },
        enabled = minor != null && (!isFx || parseAmountMinor(rub).let { it != null && it > 0L }),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Сохранить", maxLines = 1, overflow = TextOverflow.Ellipsis) }
    PlateButton(onBack, Modifier.fillMaxWidth()) { Text("Отмена", maxLines = 1, overflow = TextOverflow.Ellipsis) }
    if (picking) SlitokDatePicker(date, onPick = { date = it }, onDismiss = { picking = false }, latest = today)
}

@Composable
private fun ConfirmBody(refs: List<HistoryRef>, today: LocalDate, vm: CapitalHistoryViewModel, onBack: () -> Unit, onDelete: () -> Unit) {
    val preview by produceState<ru.maxrubtsoff.bankagg.domain.model.DeletePreview?>(null, refs) { value = vm.preview(refs) }
    val blocked by produceState<String?>(null, refs) { value = vm.check(refs) }
    Text(if (refs.size > 1) "Удалить обе операции?" else "Удалить операцию?", style = MaterialTheme.typography.titleLarge, color = Slitok.Fg)
    preview?.let { p ->
        if (p.changes) {
            WasBecomeLine(p.part.label, Money.ofMinor(p.partWas).format(), Money.ofMinor(p.partNow).format())
            WasBecomeLine("Капитал", Money.ofMinor(p.capitalWas).format(), Money.ofMinor(p.capitalNow).format())
            p.from?.let {
                Text("График пересчитается с ${RuDays.dayMonth(it, today)}", style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
            }
        } else {
            Text("Остаток и капитал не изменятся: операция уйдёт только из истории", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
        }
    }
    blocked?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Slitok.Coral) }
    if (blocked == null) {
        DangerButton(onDelete, Modifier.fillMaxWidth(), filled = true) { Text("Удалить", maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
    PlateButton(onBack, Modifier.fillMaxWidth()) { Text("Отмена", maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

private fun targetLine(t: HistoryTarget, e: HistoryEvent): String = when (t) {
    is HistoryTarget.Deposit -> "Вклад" + (e.subtitle?.let { " $it" } ?: "")
    is HistoryTarget.Currency -> Currencies.nameOf(t.code)
    is HistoryTarget.Section -> t.part.label
}

private fun signedForeign(minor: Long, currency: String): String =
    (if (minor > 0L) "+" else if (minor < 0L) "−" else "") + Money.ofMinor(kotlin.math.abs(minor)).format(currency)
