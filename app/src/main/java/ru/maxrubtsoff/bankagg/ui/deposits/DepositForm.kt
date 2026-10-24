package ru.maxrubtsoff.bankagg.ui.deposits

import androidx.compose.ui.text.style.TextOverflow
import ru.maxrubtsoff.bankagg.ui.theme.Amounts
import ru.maxrubtsoff.bankagg.ui.common.SlitokField
import ru.maxrubtsoff.bankagg.ui.common.SlitokDatePicker
import ru.maxrubtsoff.bankagg.ui.common.SlitokSwitch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.maxrubtsoff.bankagg.domain.model.AccrualMethod
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.model.DepositMath
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.ui.cashback.BankPickerRow
import ru.maxrubtsoff.bankagg.ui.common.AmountText
import ru.maxrubtsoff.bankagg.ui.common.AmountGrouping
import ru.maxrubtsoff.bankagg.ui.common.keepVisibleWhenFocused
import ru.maxrubtsoff.bankagg.ui.common.keyboardOpen
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import ru.maxrubtsoff.bankagg.ui.theme.Unbounded
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// ---- parsing ----

/**
 * "1 500 000,50" → 150 000 050 коп. Accepts plain, no-break (U+00A0) and narrow
 * no-break (U+202F) spaces between groups and a comma or dot before kopecks.
 * Integer arithmetic only — no Double on the way. Null for empty, zero or junk.
 */
fun parseRubles(text: String): Money? {
    val t = text.filterNot { it == ' ' || it == '\u00A0' || it == '\u202F' }.replace(',', '.')
    if (t.isEmpty()) return null
    val parts = t.split('.')
    if (parts.size > 2) return null
    val rub = parts[0].ifEmpty { "0" }
    val kop = parts.getOrNull(1).orEmpty()
    if (!rub.all { it.isDigit() } || !kop.all { it.isDigit() } || kop.length > 2 || rub.length > 15) return null
    val minor = rub.toLong() * 100 + kop.padEnd(2, '0').toLong()
    return if (minor > 0) Money.ofMinor(minor) else null
}

fun parseRateBp(text: String): Int? {
    val d = text.trim().replace(',', '.').replace("%", "").trim().toDoubleOrNull() ?: return null
    if (d <= 0.0 || d > 100.0) return null
    return Math.round(d * 100).toInt()
}

/** Money → input text in rubles ("500000" / "500000.50"). */
fun moneyInput(m: Money): String =
    if (m.minor % 100 == 0L) (m.minor / 100).toString() else String.format("%.2f", m.minor / 100.0)

/** rateBp → input text ("19" / "18.5"). */
fun rateInput(rateBp: Int): String {
    val v = rateBp / 100.0
    return if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
}

// ---- form state ----

class DepositFormState(
    brand: BankBrand?,
    title: String,
    principal: String,
    exit: String,
    rate: String,
    start: LocalDate?,
    end: LocalDate?,
    method: AccrualMethod,
    autoExit: Boolean
) {
    var brand by mutableStateOf(brand)
    var title by mutableStateOf(title)
    var principal by mutableStateOf(principal)
    var exit by mutableStateOf(exit)
    var rate by mutableStateOf(rate)
    var start by mutableStateOf(start)
    var end by mutableStateOf(end)
    var method by mutableStateOf(method)
    var autoExit by mutableStateOf(autoExit)

    val principalMoney: Money? get() = parseRubles(principal)
    val exitMoney: Money? get() = if (autoExit) null else parseRubles(exit)
    val rateBp: Int? get() = parseRateBp(rate)

    val isValid: Boolean
        get() {
            val s = start; val e = end
            // The rate is optional when the exit amount is known: income comes from it.
            return brand != null && principalMoney != null && (rateBp != null || exitMoney != null) &&
                s != null && e != null && e.isAfter(s)
        }

    /** A preview deposit built from the current fields, or null if not enough is filled. */
    fun previewDeposit(): Deposit? {
        val b = brand ?: return null
        val p = principalMoney ?: return null
        val s = start ?: return null
        val e = end ?: return null
        if (!e.isAfter(s)) return null
        val r = rateBp
        val ex = exitMoney
        if (r == null && ex == null) return null
        return Deposit(
            id = "", brand = b, title = title, principal = p,
            rateBp = r ?: 0, startDate = s, endDate = e, exitAmount = ex, method = method
        )
    }
}

@Composable
fun rememberDepositFormState(
    key: String,
    brand: BankBrand? = null,
    title: String = "",
    principal: String = "",
    exit: String = "",
    rate: String = "",
    start: LocalDate? = null,
    end: LocalDate? = null,
    method: AccrualMethod = AccrualMethod.LINEAR,
    autoExit: Boolean = false
): DepositFormState = remember(key) {
    DepositFormState(brand, title, principal, exit, rate, start, end, method, autoExit)
}

// ---- the fields ----

@Composable
fun DepositFormFields(state: DepositFormState) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FieldLabel("Банк")
        BankPickerRow(selected = state.brand, onSelect = { state.brand = it })

        TextInput(
            label = "Название (необязательно)",
            value = state.title,
            onChange = { state.title = it },
            placeholder = "напр. «Надёжный»"
        )

        AmountField(
            label = "Сумма на входе",
            value = state.principal,
            onChange = { state.principal = it },
            placeholder = "0 ₽"
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)) {
                AmountField(
                    label = "Ставка, % годовых",
                    value = state.rate,
                    onChange = { state.rate = it },
                    placeholder = "0,0",
                    allowDot = true
                )
            }
            Box(Modifier.weight(1f)) {
                DateField("Дата начала", state.start) { state.start = it }
            }
        }

        DateField("Дата окончания", state.end) { state.end = it }

        MethodPicker(state.method) { state.method = it }

        // Auto-compute vs explicit exit amount.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Считать доход по ставке", style = MaterialTheme.typography.bodyLarge, color = Slitok.Fg)
                Text(
                    "сумму на выходе посчитаю сам",
                    style = MaterialTheme.typography.bodySmall,
                    color = Slitok.Muted
                )
            }
            SlitokSwitch(checked = state.autoExit, onChange = { state.autoExit = it })
        }

        if (state.autoExit) {
            // While typing it rides on top of the keyboard instead (KeyboardExitSum).
            if (!keyboardOpen()) ComputedExitRow(state.payoutPreview())
        } else {
            AmountField(
                label = "Сумма на выходе",
                value = state.exit,
                onChange = { state.exit = it },
                placeholder = "0 ₽"
            )
        }
    }
}

private fun DepositFormState.payoutPreview(): Money? =
    previewDeposit()?.let { DepositMath.project(it, LocalDate.now()).payoutAmount }

/**
 * While the keyboard is up, the computed exit sum sits right above it and
 * updates as you type; place it under the form's scroll area.
 */
@Composable
fun KeyboardExitSum(state: DepositFormState) {
    if (state.autoExit && keyboardOpen()) {
        Box(Modifier.fillMaxWidth().background(Slitok.Bg).padding(horizontal = 16.dp, vertical = 8.dp)) {
            ComputedExitRow(state.payoutPreview())
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = Slitok.Muted
    )
}

@Composable
private fun TextInput(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String
) {
    Box(Modifier.keepVisibleWhenFocused()) {
        SlitokField(value, { onChange(it.take(40)) }, label = label, placeholder = placeholder)
    }
}

@Composable
internal fun AmountField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    allowDot: Boolean = true
) {
    Box(Modifier.keepVisibleWhenFocused()) {
        ru.maxrubtsoff.bankagg.ui.common.AmountField(
            value,
            { raw -> onChange(raw.take(14)) },
            label = label,
            placeholder = placeholder,
            allowFraction = allowDot
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateField(label: String, date: LocalDate?, onPick: (LocalDate) -> Unit) {
    var show by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(13.dp)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        FieldLabel(label)
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Slitok.Inset)
                .border(1.dp, Slitok.Line, shape)
                .clickable { show = true }
                .padding(horizontal = 13.dp, vertical = 15.dp)
        ) {
            Text(
                date?.let(::formatDate) ?: "дд.мм.гггг",
                style = Amounts.Row,
                color = if (date == null) Slitok.Faint else Slitok.Fg
            )
        }
    }

    if (show) {
        SlitokDatePicker(date, onPick = onPick, onDismiss = { show = false })
    }
}

@Composable
private fun MethodPicker(selected: AccrualMethod, onSelect: (AccrualMethod) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        FieldLabel("Способ начисления")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AccrualMethod.entries.forEach { m ->
                val active = m == selected
                val shape = RoundedCornerShape(11.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(if (active) Slitok.Violet.copy(alpha = 0.16f) else Slitok.Inset)
                        .border(
                            1.dp,
                            if (active) Slitok.Violet.copy(alpha = 0.5f) else Slitok.Line,
                            shape
                        )
                        .clickable { onSelect(m) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AmountText(
                        methodLabel(m),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) Slitok.Fg else Slitok.Muted,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        minSize = 9.sp,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ComputedExitRow(payout: Money?) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Slitok.Mint.copy(alpha = 0.08f))
            .border(1.dp, Slitok.Mint.copy(alpha = 0.22f), shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Сумма на выходе", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, softWrap = false)
        Spacer(Modifier.width(12.dp))
        AmountText(
            payout?.let(::money) ?: "— ₽",
            style = Amounts.Row,
            fontWeight = FontWeight.SemiBold,
            color = Slitok.Mint,
            minSize = 11.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}
