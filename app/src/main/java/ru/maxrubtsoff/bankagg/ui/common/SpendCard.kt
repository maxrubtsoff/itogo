package ru.maxrubtsoff.bankagg.ui.common

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.BankSpend
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.MonthSpending
import ru.maxrubtsoff.bankagg.domain.model.RuPlural
import ru.maxrubtsoff.bankagg.domain.model.Spending
import ru.maxrubtsoff.bankagg.domain.model.SummaryLine
import ru.maxrubtsoff.bankagg.ui.theme.Amounts
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import java.time.YearMonth

/**
 * A strip of shares (F-58): segments in [parts] colours, proportional to the sums, [gap] between them;
 * a tiny one keeps 3 dp so it reads as a piece of the bar, not a dot.
 */
@Composable
fun ShareBar(parts: List<Pair<Color, Long>>, modifier: Modifier = Modifier, height: Dp = 8.dp, gap: Dp = 2.dp) {
    Row(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)),
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        parts.filter { it.second > 0L }.forEach { (color, v) ->
            Box(Modifier.weight(v.toFloat()).widthIn(min = 3.dp).fillMaxHeight().background(color))
        }
    }
}

/** One column of [MonthBars]: a short [label] under a bar of [minor]; [description] is for TalkBack. */
data class MonthBar(val label: String, val minor: Long, val description: String)

/**
 * Columns of months (F-58): the height is proportional to the sum (at most 60 dp, at least 4), the picked one is
 * brass with a brass caption, the others pale. The whole column is the touch (at least 44 dp wide and 86 high).
 */
@Composable
fun MonthBars(months: List<MonthBar>, selected: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val max = months.maxOfOrNull { it.minor }?.coerceAtLeast(1L) ?: 1L
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        months.forEachIndexed { i, m ->
            val on = i == selected
            Column(
                Modifier.weight(1f).height(86.dp).heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onPick(i) }
                    .semantics { contentDescription = m.description; this.selected = on },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    Modifier.widthIn(max = 30.dp).fillMaxWidth().height((60f * m.minor / max).dp.coerceAtLeast(4.dp))
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                        .background(if (on) Slitok.Brass else Slitok.Fg.copy(alpha = 0.14f))
                )
                Spacer(Modifier.height(6.dp))
                Text(m.label, style = MaterialTheme.typography.bodySmall, color = if (on) Slitok.Brass2 else Slitok.Muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * The month's spending on the «Счета» page (F-58): the sum and what came in, six columns of months,
 * a strip of banks' shares with a row per bank, and the switch of transfers to people. [months] are
 * the columns, the current one last; the switch only changes this card.
 */
@Composable
fun SpendCard(
    months: List<MonthSpending>,
    selected: Int,
    includeTransfers: Boolean,
    onPickMonth: (Int) -> Unit,
    onIncludeTransfers: (Boolean) -> Unit,
    onBank: (BankBrand) -> Unit,
    modifier: Modifier = Modifier
) {
    val current = months[selected]
    val isNow = selected == months.lastIndex
    Panel(modifier.fillMaxWidth()) {
        Spacer(Modifier.height(16.dp))
        // The figures and rows change by overlay: the leaving one fast, the coming one at normal pace.
        Crossfade(current, animationSpec = Motion.tween(Motion.NORMAL), label = "spend") { m ->
            Column {
                SpendHead(m, isNow)
            }
        }
        Spacer(Modifier.height(16.dp))
        MonthBars(
            months.map { MonthBar(monthShort(it.month), it.totalMinor, "Траты " + monthIn(it.month)) },
            selected, onPickMonth
        )
        Crossfade(current, animationSpec = Motion.tween(Motion.NORMAL), label = "spendBanks") { m ->
            SpendBanks(m, onBank)
        }
        HorizontalDivider(color = Slitok.Line)
        SwitchRow(
            "Переводы людям", includeTransfers, onIncludeTransfers,
            hint = RuPlural.transfers(current.transfersCount) + " · " + Money.ofMinor(current.transfersMinor).format()
        )
    }
}

@Composable
private fun SpendHead(m: MonthSpending, isNow: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f, fill = false)) {
            if (m.totalMinor == 0L) {
                Text(if (isNow) "С 1 ${monthOf(m.month)}" else monthIn(m.month).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
                Spacer(Modifier.height(6.dp))
                Text("Трат пока нет", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
            } else {
                Text(
                    if (isNow) "Потрачено с 1 ${monthOf(m.month)}" else "Потрачено " + monthIn(m.month),
                    style = MaterialTheme.typography.bodySmall, color = Slitok.Muted
                )
                Spacer(Modifier.height(4.dp))
                AmountText(Money.ofMinor(m.totalMinor).format(), style = Amounts.Large, color = Slitok.Fg, minSize = 16.sp, textAlign = TextAlign.Start)
            }
        }
        if (m.totalMinor > 0L) {
            Column(horizontalAlignment = Alignment.End) {
                Text("Пришло", style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
                Spacer(Modifier.height(8.dp))
                MoneyText("+" + Money.ofMinor(m.incomeMinor).format(), style = Amounts.Small, color = Slitok.Mint)
            }
        }
    }
}

@Composable
private fun SpendBanks(m: MonthSpending, onBank: (BankBrand) -> Unit) {
    val fold = remember(m.byBank) { Spending.fold(m.byBank) }
    var open by remember(m.month) { mutableStateOf(false) }
    val accents = m.byBank.map { rememberBankAccent(it.brand) }
    val withTransfers = m.includeTransfers && m.transfersMinor > 0L
    if (m.byBank.isEmpty() && !withTransfers) return
    Column {
        // One bank needs no strip: it is the whole.
        if (m.byBank.size + (if (withTransfers) 1 else 0) > 1) {
            ShareBar(
                m.byBank.mapIndexed { i, b -> accents[i] to b.minor } + if (withTransfers) listOf(Slitok.Faint to m.transfersMinor) else emptyList(),
                Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp)
            )
        } else {
            Spacer(Modifier.height(10.dp))
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            fold.shown.forEach { b -> BankSpendRow(b, onBank) }
            if (fold.rest.isNotEmpty()) {
                SummaryRow(
                    tile = {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(20.dp)).background(Slitok.Inset), Alignment.Center) {
                            Text("+${fold.rest.size}", style = MaterialTheme.typography.bodySmall, color = Slitok.Muted)
                        }
                    },
                    title = "Ещё " + RuPlural.banks(fold.rest.size),
                    valueMinor = fold.rest.sumOf { it.minor },
                    line = SummaryLine(fold.rest.joinToString(", ") { it.brand.title }),
                    onClick = { open = !open }, chevron = false,
                    detailIcon = if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown
                )
                if (open) fold.rest.forEach { b -> BankSpendRow(b, onBank) }
            }
            if (withTransfers) {
                SummaryRow(
                    tile = { SummaryTile(Icons.Filled.SwapHoriz, Slitok.Muted) },
                    title = "Переводы людям", valueMinor = m.transfersMinor,
                    line = SummaryLine(RuPlural.transfers(m.transfersCount))
                )
            }
        }
    }
}

@Composable
private fun BankSpendRow(b: BankSpend, onBank: (BankBrand) -> Unit) {
    SummaryRow(
        tile = { BankIcon(b.brand, 40.dp) },
        title = b.brand.title, valueMinor = b.minor,
        line = SummaryLine(RuPlural.operations(b.count)),
        onClick = { onBank(b.brand) }, chevron = false
    )
}

private val MONTHS_SHORT = listOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")
private val MONTHS_GEN = listOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
private val MONTHS_IN = listOf("в январе", "в феврале", "в марте", "в апреле", "в мае", "в июне", "в июле", "в августе", "в сентябре", "в октябре", "в ноябре", "в декабре")

private fun monthShort(m: YearMonth) = MONTHS_SHORT[m.monthValue - 1]
private fun monthOf(m: YearMonth) = MONTHS_GEN[m.monthValue - 1]
private fun monthIn(m: YearMonth) = MONTHS_IN[m.monthValue - 1]
