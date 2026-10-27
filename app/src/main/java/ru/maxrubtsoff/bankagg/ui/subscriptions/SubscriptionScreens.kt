package ru.maxrubtsoff.bankagg.ui.subscriptions

import ru.maxrubtsoff.bankagg.domain.model.Figures
import ru.maxrubtsoff.bankagg.ui.theme.Amounts
import ru.maxrubtsoff.bankagg.ui.common.SlitokField
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import ru.maxrubtsoff.bankagg.ui.common.SlitokDatePicker
import ru.maxrubtsoff.bankagg.ui.common.SlitokSwitch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.maxrubtsoff.bankagg.domain.model.BrandDecor
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.Period
import ru.maxrubtsoff.bankagg.domain.model.PeriodUnit
import ru.maxrubtsoff.bankagg.domain.model.RuDays
import ru.maxrubtsoff.bankagg.domain.model.Subscription
import ru.maxrubtsoff.bankagg.domain.model.Subscriptions
import ru.maxrubtsoff.bankagg.ui.common.AmountField
import ru.maxrubtsoff.bankagg.ui.capital.ModeChip
import ru.maxrubtsoff.bankagg.ui.capital.amountInput
import ru.maxrubtsoff.bankagg.ui.capital.parseAmountMinor
import ru.maxrubtsoff.bankagg.ui.common.BankIcon
import ru.maxrubtsoff.bankagg.ui.common.BankPickSheet
import ru.maxrubtsoff.bankagg.ui.common.BrandAvatar
import ru.maxrubtsoff.bankagg.ui.common.BrandDecorEditor
import ru.maxrubtsoff.bankagg.ui.common.SelectRow
import ru.maxrubtsoff.bankagg.ui.common.rememberBrandDecorState
import ru.maxrubtsoff.bankagg.ui.common.squircle
import ru.maxrubtsoff.bankagg.ui.common.BrassButton
import ru.maxrubtsoff.bankagg.ui.common.DangerButton
import ru.maxrubtsoff.bankagg.ui.common.MoneyText
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.nbsp
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset


/**
 * A subscription to fill in (F-27): by hand from «+», or already filled from a
 * run of payments — then only what's off needs a touch.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SubscriptionFormScreen(vm: SubscriptionsViewModel, onBack: () -> Unit) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val listState by vm.state.collectAsStateWithLifecycle()
    val start = draft ?: run { LaunchedEffect(Unit) { onBack() }; return }
    val banks by vm.myBanks.collectAsStateWithLifecycle()

    val look = rememberBrandDecorState(start.title, start.look, touched = start.decor != null && start.decor != BrandDecor.defaultFor(start.title), key = start.id)
    val price0 = if (start.priceMinor > 0) amountInput(start.priceMinor) else ""
    var price by remember(start.id) { mutableStateOf(TextFieldValue(price0, TextRange(price0.length))) }
    var unit by remember(start.id) { mutableStateOf(start.period.unit) }
    var days by remember(start.id) { mutableStateOf(start.period.days.takeIf { it > 0 }?.toString() ?: "") }
    var next by remember(start.id) { mutableStateOf(start.nextDate) }
    var brand by remember(start.id) { mutableStateOf(start.brand) }
    var remind by remember(start.id) { mutableStateOf(start.remind) }
    var picking by remember { mutableStateOf(false) }
    var pickingBank by remember { mutableStateOf(false) }

    val priceMinor = parseAmountMinor(price.text)
    val period = Period(unit, if (unit == PeriodUnit.DAYS) days.toIntOrNull() ?: 0 else 0)
    val ready = look.title.isNotBlank() && priceMinor != null && priceMinor > 0 && (unit != PeriodUnit.DAYS || period.days > 0)
    val isNew = listState.active.none { it.id == start.id }

    Scaffold(
        topBar = {
            ScreenHeader(if (isNew) "Новая подписка" else "Подписка", onBack)
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BrandDecorEditor(look, squircle(64.dp))
            Field("Цена") { AmountField(price, { price = it }, "₽") }
            Field("Период") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(PeriodUnit.WEEK to "Неделя", PeriodUnit.MONTH to "Месяц", PeriodUnit.YEAR to "Год", PeriodUnit.DAYS to "Своё").forEach { (u, label) ->
                        ModeChip(label, unit == u) { unit = u }
                    }
                }
                if (unit == PeriodUnit.DAYS) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("каждые", style = MaterialTheme.typography.bodyLarge, color = Slitok.Muted)
                        Box(Modifier.width(88.dp)) { TextLine(days, KeyboardType.Number) { days = it.filter(Char::isDigit).take(3) } }
                        Text("дн.", style = MaterialTheme.typography.bodyLarge, color = Slitok.Muted)
                    }
                }
            }
            Field("Следующее списание") {
                Box(
                    Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(Slitok.Inset)
                        .border(1.dp, Slitok.Line, RoundedCornerShape(14.dp)).clickable { picking = true }.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) { Text(RuDays.relative(next, LocalDate.now()).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge) }
            }
            if (banks.isNotEmpty() || brand != null) Field("Списывается с") {
                SelectRow(
                    value = brand?.title,
                    placeholder = "Не указан",
                    onClick = { pickingBank = true },
                    leading = brand?.let { b -> { BankIcon(b, size = 36.dp) } }
                )
            }
            Panel(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Напомнить за день", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    SlitokSwitch(checked = remind, onChange = { remind = it })
                }
            }
            BrassButton(
                onClick = {
                    vm.saveWithPicture(
                        start.copy(
                            title = look.title.trim(), priceMinor = priceMinor ?: 0, period = period, nextDate = next,
                            brand = brand, remind = remind, decor = look.decor()
                        ),
                        look.picked, look.usePicture, onBack
                    )
                },
                Modifier.fillMaxWidth(), enabled = ready
            ) { Text("Сохранить") }
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (picking) {
        SlitokDatePicker(next, onPick = { next = it }, onDismiss = { picking = false })
    }
    if (pickingBank) BankPickSheet(brand, onPick = { brand = it }, onClear = { brand = null }, onDismiss = { pickingBank = false })
}

@Composable
private fun Field(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Slitok.Muted)
        content()
    }
}

@Composable
private fun TextLine(value: String, keyboard: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    SlitokField(value, onChange, keyboard = keyboard)
}

/** A subscription's card (F-27): price and raise, when and from which bank, its payments, «Больше не плачу». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionCardScreen(vm: SubscriptionsViewModel, id: String, onEdit: () -> Unit, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state.active.firstOrNull { it.id == id } ?: return
    val today = state.today
    val history = Subscriptions.history(s, state.payments)

    Scaffold(
        topBar = {
            ScreenHeader(s.title, onBack) {
                IconButton(onClick = { vm.edit(s); onEdit() }) { Icon(Icons.Filled.Edit, contentDescription = "Изменить") }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Panel(Modifier.fillMaxWidth(), radius = 20.dp) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        BrandAvatar(s.look, squircle(48.dp), 48.dp, overlay = s.brand)
                        Column {
                            MoneyText(Money.ofMinor(s.priceMinor).format(), style = Amounts.Large)
                            Text(s.period.label(), style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
                            s.raisedBy?.let { MoneyText("подорожала на ${Money.ofMinor(it).format()}".nbsp(), style = MaterialTheme.typography.bodyMedium, color = Slitok.Coral) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Fact("Следующее", Subscriptions.whenText(Subscriptions.upcoming(s, today), today), Modifier.weight(1f))
                        Fact("Списывается с", s.brand?.title ?: "—", Modifier.weight(1f), quiet = true)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Fact("В год", Money.ofMinor(s.period.perMonth(s.priceMinor) * 12).format(), Modifier.weight(1f))
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text("Напомнить", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted, modifier = Modifier.weight(1f))
                            SlitokSwitch(checked = s.remind, onChange = { vm.setRemind(s, it) })
                        }
                    }
                }
            }
            if (history.isNotEmpty()) {
                Text("Платежи", style = MaterialTheme.typography.titleMedium)
                Panel(Modifier.fillMaxWidth()) {
                    history.forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider(color = Slitok.Line)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(RuDays.relative(p.date, today).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            MoneyText(Money.ofMinor(p.amountMinor).format(), style = Amounts.Small, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
            DangerButton(onClick = { vm.stop(s); onBack() }, Modifier.fillMaxWidth()) { Text("Больше не плачу") }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier, quiet: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
        MoneyText(value.nbsp(), style = if (Figures.isFigure(value)) Amounts.Row else MaterialTheme.typography.bodyLarge, color = if (quiet) Slitok.Muted else Color.Unspecified, maxLines = 2)
    }
}
