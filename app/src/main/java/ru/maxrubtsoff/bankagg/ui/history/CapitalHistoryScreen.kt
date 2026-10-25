package ru.maxrubtsoff.bankagg.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.maxrubtsoff.bankagg.domain.model.CapitalChange
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.HistoryEvent
import ru.maxrubtsoff.bankagg.domain.model.HistoryPeriod
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.RuDays
import ru.maxrubtsoff.bankagg.domain.model.RuPlural
import ru.maxrubtsoff.bankagg.ui.capital.color
import ru.maxrubtsoff.bankagg.ui.capital.label
import ru.maxrubtsoff.bankagg.ui.common.AmountText
import ru.maxrubtsoff.bankagg.ui.common.CapitalChart
import ru.maxrubtsoff.bankagg.ui.common.Dot
import ru.maxrubtsoff.bankagg.ui.common.EmptyState
import ru.maxrubtsoff.bankagg.ui.common.MoneyText
import ru.maxrubtsoff.bankagg.ui.common.PagerTabsStatic
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.PeriodChips
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import ru.maxrubtsoff.bankagg.ui.common.SlitokChip
import ru.maxrubtsoff.bankagg.ui.common.StatTile
import ru.maxrubtsoff.bankagg.ui.common.heroGlow
import ru.maxrubtsoff.bankagg.ui.common.spanWord
import ru.maxrubtsoff.bankagg.ui.overview.CapitalPartArt
import ru.maxrubtsoff.bankagg.ui.theme.Amounts
import ru.maxrubtsoff.bankagg.ui.theme.Slitok
import java.time.LocalDate

/**
 * «История капитала» (F-36): the line of the total or of one part over a week,
 * a month, a year or everything; was / became / change; and below it what
 * happened — by days for a week and a month, folded into months for longer.
 * [partArg] is the part the person came from (a section's hero), null — the total.
 */
@Composable
fun CapitalHistoryScreen(vm: CapitalHistoryViewModel, partArg: String?, onBack: () -> Unit) {
    LaunchedEffect(partArg) { vm.open(CapitalPart.fromKey(partArg)) }
    val ui by vm.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { ScreenHeader("История капитала", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val u = ui
            if (u == null) {
                Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator(color = Slitok.Brass) }
            } else {
                HistoryContent(u, vm)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HistoryContent(u: HistoryUi, vm: CapitalHistoryViewModel) {
    var scrub by remember { mutableStateOf<Int?>(null) }
    val color = u.part?.color() ?: Slitok.Brass2

    PeriodChips(u.period, onPick = { scrub = null; vm.setPeriod(it) })
    if (u.kept.size > 1) {
        PagerTabsStatic(listOf("Всего", "По категориям"), if (u.part == null) 0 else 1) { vm.setByParts(it == 1, u.kept) }
        if (u.part != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                u.kept.forEach { p ->
                    SlitokChip(p.label, selected = p == u.part, onClick = { vm.setPart(p) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    val change = u.change
    if (!u.drawable || change == null) {
        EmptyState("Истории пока нет", art = { CapitalPartArt(u.part ?: CapitalPart.ACCOUNTS, Modifier.size(width = 92.dp, height = 70.dp)) }) {
            Text("Появится через день", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
        }
        return
    }

    val at = scrub?.let { u.series.getOrNull(it) }
    Panel(Modifier.fillMaxWidth(), radius = 22.dp) {
        Box(Modifier.fillMaxWidth().heroGlow(color)) {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp)) {
                AmountText(Money.ofMinor(at?.minor ?: change.to.minor).format(), style = Amounts.Hero, color = Slitok.Fg, minSize = 20.sp)
                Spacer(Modifier.height(2.dp))
                if (at != null) {
                    Text(RuDays.dayWeekday(at.date, u.today), style = MaterialTheme.typography.labelMedium, color = Slitok.Brass2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else if (change.deltaMinor == 0L) {
                    Text("без изменений ${u.period.spanWord}", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        MoneyText(
                            deltaText(change), style = MaterialTheme.typography.bodyMedium,
                            color = if (change.deltaMinor > 0L) Slitok.Mint else Slitok.Fg, fontWeight = FontWeight.SemiBold
                        )
                        MoneyText("с ${RuDays.dayMonth(change.from.date, u.today)}", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted, maxLines = 2)
                    }
                }
                Spacer(Modifier.height(12.dp))
                CapitalChart(points = u.series.map { it.minor }, axis = axisOf(u), onScrub = { scrub = it }, color = color)
            }
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile("Было", Modifier.weight(1f)) { Sum(change.from.minor) }
        StatTile("Стало", Modifier.weight(1f)) { Sum(at?.minor ?: change.to.minor) }
        StatTile("Дельта", Modifier.weight(1f)) { Sum((at?.minor ?: change.to.minor) - change.from.minor, signed = true) }
    }

    Events(u, at?.date)
}

@Composable
private fun Sum(minor: Long, signed: Boolean = false) {
    AmountText(
        if (signed) signedSum(minor) else Money.ofMinor(minor).format(),
        style = Amounts.Small, color = if (signed && minor > 0L) Slitok.Mint else Slitok.Fg, minSize = 9.sp
    )
}

@Composable
private fun Events(u: HistoryUi, scrubbed: LocalDate?) {
    val events = if (scrubbed != null) u.events.filter { it.date == scrubbed } else u.events
    if (scrubbed != null) Caption(RuDays.dayMonth(scrubbed, u.today))
    when {
        events.isEmpty() -> Text(
            "За это время капитал не менялся", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        scrubbed == null && u.months.isNotEmpty() -> {
            var open by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
            u.months.forEach { m ->
                val key = m.month.toString()
                val expanded = key in open
                Panel(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().clickable { open = if (expanded) open - key else open + key }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(RuDays.monthTitle(m.month, u.today), style = MaterialTheme.typography.bodyLarge, color = Slitok.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(RuPlural.events(m.events.size), style = MaterialTheme.typography.bodySmall, color = Slitok.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        MoneyText(signedSum(m.deltaMinor), style = Amounts.Small, color = if (m.deltaMinor > 0L) Slitok.Mint else Slitok.Fg)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Slitok.Faint, modifier = Modifier.size(20.dp))
                    }
                    if (expanded) {
                        m.events.groupBy { it.date }.toSortedMap(compareByDescending { it }).forEach { (day, list) ->
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Slitok.Line)
                            Text(dayCaption(day, u.today), style = MaterialTheme.typography.labelMedium, color = Slitok.Muted, modifier = Modifier.padding(start = 16.dp, top = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            list.forEach { EventRow(it) }
                        }
                    }
                }
            }
        }
        else -> events.groupBy { it.date }.toSortedMap(compareByDescending { it }).forEach { (day, list) ->
            Caption(dayCaption(day, u.today))
            Panel(Modifier.fillMaxWidth()) {
                list.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Slitok.Line)
                    EventRow(e)
                }
            }
        }
    }
}

@Composable
private fun EventRow(e: HistoryEvent) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Dot(e.part.color())
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.bodyLarge, color = Slitok.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            e.subtitle?.let { MoneyText(it, style = MaterialTheme.typography.bodySmall, color = Slitok.Muted, maxLines = 1) }
        }
        e.amountMinor?.let { MoneyText(signedSum(it), style = Amounts.Small, color = if (it > 0L) Slitok.Mint else Slitok.Fg) }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Slitok.Muted, modifier = Modifier.padding(start = 4.dp, top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private fun dayCaption(day: LocalDate, today: LocalDate): String =
    RuDays.relative(day, today).replaceFirstChar { it.uppercase() }

/** Three to five labels along the X axis. */
private fun axisOf(u: HistoryUi): List<String> {
    val long = u.period == HistoryPeriod.YEAR || u.period == HistoryPeriod.ALL
    val n = minOf(u.series.size, if (u.period == HistoryPeriod.WEEK) 4 else 5)
    if (n < 2) return emptyList()
    return (0 until n).map { i -> RuDays.axisLabel(u.series[i * (u.series.size - 1) / (n - 1)].date, long) }
}

/** "+13 622 ₽ · +0,6 %" / "−4 000 ₽ · −0,2 %" with a real minus sign. */
internal fun deltaText(c: CapitalChange): String =
    signedSum(c.deltaMinor) + (c.percentBp?.let { " · " + signedPercent(it) } ?: "")

internal fun signedSum(minor: Long): String = when {
    minor > 0L -> "+" + Money.ofMinor(minor).format()
    minor < 0L -> "−" + Money.ofMinor(-minor).format()
    else -> Money.ZERO.format()
}

/** Hundredths of a percent → "+0,6 %" (one decimal, no ",0"). */
internal fun signedPercent(bp: Long): String {
    val tenths = (kotlin.math.abs(bp) + 5) / 10
    val num = if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10},${tenths % 10}"
    val sign = if (tenths == 0L) "" else if (bp > 0) "+" else "−"
    return sign + num + Money.NBSP + "%"
}
