@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package ru.maxrubtsoff.bankagg.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import ru.maxrubtsoff.bankagg.ui.common.SlitokChip
import java.time.YearMonth
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.domain.model.CapitalChange
import ru.maxrubtsoff.bankagg.domain.model.CapitalEvents
import ru.maxrubtsoff.bankagg.domain.model.CapitalHistory
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.HistoryEvent
import ru.maxrubtsoff.bankagg.domain.model.HistoryPeriod
import ru.maxrubtsoff.bankagg.domain.model.HistoryRow
import ru.maxrubtsoff.bankagg.domain.model.HistoryTarget
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.MoneyCompact
import ru.maxrubtsoff.bankagg.domain.model.RuDays
import ru.maxrubtsoff.bankagg.domain.model.RuPlural
import ru.maxrubtsoff.bankagg.ui.capital.color
import ru.maxrubtsoff.bankagg.ui.capital.label
import ru.maxrubtsoff.bankagg.ui.common.AmountText
import ru.maxrubtsoff.bankagg.ui.common.CapitalChart
import ru.maxrubtsoff.bankagg.ui.common.Dot
import ru.maxrubtsoff.bankagg.ui.common.EmptyState
import ru.maxrubtsoff.bankagg.ui.common.MoneyText
import ru.maxrubtsoff.bankagg.ui.common.PagerTabs
import ru.maxrubtsoff.bankagg.ui.common.SectionPager
import ru.maxrubtsoff.bankagg.ui.common.Panel
import ru.maxrubtsoff.bankagg.ui.common.PeriodChips
import ru.maxrubtsoff.bankagg.ui.common.ScreenHeader
import ru.maxrubtsoff.bankagg.ui.common.SlitokSheet
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
fun CapitalHistoryScreen(vm: CapitalHistoryViewModel, partArg: String?, onBack: () -> Unit, onOpenTarget: (HistoryTarget) -> Unit) {
    LaunchedEffect(partArg) { vm.open(CapitalPart.fromKey(partArg)) }
    val ui by vm.state.collectAsStateWithLifecycle()
    // A deposit that is gone has no screen: its event opens a sheet instead (F-44).
    var gone by remember { mutableStateOf<HistoryEvent?>(null) }
    val onTap: (HistoryEvent) -> Unit = { e ->
        val t = e.target
        val alive = ui?.aliveDeposits.orEmpty()
        if (t != null) {
            if (t is HistoryTarget.Deposit && t.id !in alive) gone = e else onOpenTarget(t)
        }
    }
    gone?.let { e ->
        SlitokSheet(onDismiss = { gone = null }) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(e.title, style = MaterialTheme.typography.titleMedium, color = Slitok.Fg)
                e.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted) }
                e.amountMinor?.let {
            MoneyText(signedSum(it), style = Amounts.Small, color = if (it > 0L) Slitok.Mint else Slitok.Fg, compact = MoneyCompact.short(it, signed = true))
        }
                Text("Этого вклада больше нет", style = MaterialTheme.typography.bodySmall, color = Slitok.Faint)
            }
        }
    }

    Scaffold(topBar = { ScreenHeader("История капитала", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val u = ui
            if (u == null) {
                Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator(color = Slitok.Brass) }
            } else {
                HistorySections(u, vm, onTap)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** One row of sections, swiped sideways; the period is common to all of them (F-45, B-73). */
@Composable
private fun HistorySections(u: HistoryScreenUi, vm: CapitalHistoryViewModel, onTap: (HistoryEvent) -> Unit) {
    val sections = u.sections
    // One kept section needs no switch: the total already is that section.
    val swipes = u.kept.size > 1
    val pager = rememberPagerState(initialPage = CapitalHistory.pageOf(u.part, sections)) { if (swipes) sections.size else 1 }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.drop(1).collect { vm.setPart(CapitalHistory.partOf(it, sections)) }
    }
    LaunchedEffect(u.part) {
        val page = CapitalHistory.pageOf(u.part, sections)
        if (swipes && pager.currentPage != page) pager.animateScrollToPage(page)
    }
    if (swipes) {
        PagerTabs(sections.map { it?.label ?: "Всё" }, pager) { scope.launch { pager.animateScrollToPage(it) } }
    }
    SectionPager(pager) { page -> HistoryContent(u.pages[page], vm, onTap) }
}

@Composable
private fun HistoryContent(u: HistoryUi, vm: CapitalHistoryViewModel, onTap: (HistoryEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { HistoryPage(u, vm, onTap) }
}

@Composable
private fun ColumnScope.HistoryPage(u: HistoryUi, vm: CapitalHistoryViewModel, onTap: (HistoryEvent) -> Unit) {
    // The picked day stays after the finger is gone; a new period starts clean (F-41).
    var picked by rememberSaveable(u.period) { mutableStateOf<Long?>(null) }
    var scrollTick by remember { mutableIntStateOf(0) }
    var open by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val requesters = remember { mutableMapOf<LocalDate, BringIntoViewRequester>() }
    val color = u.part?.color() ?: Slitok.Brass2

    val change = u.change
    if (!u.drawable || change == null) {
        EmptyState("Истории пока нет", art = { CapitalPartArt(u.part ?: CapitalPart.ACCOUNTS, Modifier.size(width = 92.dp, height = 70.dp)) }) {
            Text("Появится через день", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
        }
        return
    }

    val selected = picked?.let { d -> u.series.indexOfFirst { it.date.toEpochDay() == d }.takeIf { it >= 0 } }
    val at = selected?.let { u.series.getOrNull(it) }
    // Let go of the chart: the list moves to the nearest day with events (a folded month opens first).
    LaunchedEffect(scrollTick) {
        if (scrollTick == 0) return@LaunchedEffect
        val day = at?.date?.let { CapitalEvents.nearestDay(u.events, it) } ?: return@LaunchedEffect
        val month = YearMonth.from(day).toString()
        if (u.months.isNotEmpty() && month !in open) {
            open = open + month
            withFrameNanos { }
        }
        requesters[day]?.bringIntoView()
    }
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
                            color = if (change.deltaMinor > 0L) Slitok.Mint else Slitok.Fg, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false), compact = deltaText(change, compact = true)
                        )
                        MoneyText("с ${RuDays.dayMonth(change.from.date, u.today)}", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted, maxLines = 2)
                    }
                }
                Spacer(Modifier.height(12.dp))
                CapitalChart(points = u.series.map { it.minor }, axis = axisOf(u), selected = selected,
                    onScrub = { picked = u.series[it].date.toEpochDay() }, onRelease = { scrollTick++ }, color = color
                )
                Spacer(Modifier.height(10.dp))
                PeriodChips(u.period, onPick = { picked = null; vm.setPeriod(it) }, compact = true)
            }
        }
    }

    Events(u, at?.date, open, { open = it }, requesters, onReset = { picked = null }, onTap = onTap)
}

@Composable
private fun Events(
    u: HistoryUi,
    picked: LocalDate?,
    open: List<String>,
    setOpen: (List<String>) -> Unit,
    requesters: MutableMap<LocalDate, BringIntoViewRequester>,
    onReset: () -> Unit,
    onTap: (HistoryEvent) -> Unit
) {
    // Folded groups of the list that are open, by id (F-39).
    var groupsOpen by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val events = u.events
    if (picked != null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Caption(RuDays.dayMonth(picked, u.today), Slitok.Brass2, Modifier.weight(1f))
            SlitokChip("Сбросить", selected = false, onClick = onReset, compact = true)
        }
    }
    fun mark(day: LocalDate) = Modifier.bringIntoViewRequester(requesters.getOrPut(day) { BringIntoViewRequester() })
    when {
        events.isEmpty() -> Text(
            "За это время капитал не менялся", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        u.months.isNotEmpty() -> {
            u.months.forEach { m ->
                val key = m.month.toString()
                val expanded = key in open
                Panel(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().clickable { setOpen(if (expanded) open - key else open + key) }.padding(horizontal = 16.dp, vertical = 12.dp),
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
                        CapitalEvents.group(m.events).groupBy { it.date }.toSortedMap(compareByDescending { it }).forEach { (day, list) ->
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Slitok.Line)
                            Text(
                                dayCaption(day, u.today), style = MaterialTheme.typography.labelMedium, color = if (day == picked) Slitok.Brass2 else Slitok.Muted,
                                modifier = mark(day).padding(start = 16.dp, top = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            list.forEach { RowItem(it, groupsOpen, { id -> groupsOpen = if (id in groupsOpen) groupsOpen - id else groupsOpen + id }, onTap) }
                        }
                    }
                }
            }
        }
        else -> CapitalEvents.group(events).groupBy { it.date }.toSortedMap(compareByDescending { it }).forEach { (day, list) ->
            Caption(dayCaption(day, u.today), if (day == picked) Slitok.Brass2 else Slitok.Muted, mark(day))
            Panel(Modifier.fillMaxWidth()) {
                list.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Slitok.Line)
                    RowItem(r, groupsOpen, { id -> groupsOpen = if (id in groupsOpen) groupsOpen - id else groupsOpen + id }, onTap)
                }
            }
        }
    }
}

/** A single event, or a folded group that opens on tap into its events (F-39). */
@Composable
private fun RowItem(row: HistoryRow, opened: List<String>, toggle: (String) -> Unit, onTap: (HistoryEvent) -> Unit) {
    when (row) {
        is HistoryRow.Single -> EventRow(row.event, onTap)
        is HistoryRow.Group -> {
            val expanded = row.id in opened
            Row(
                Modifier.fillMaxWidth().clickable { toggle(row.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Dot(row.children.first().part.color())
                Text(row.title, style = MaterialTheme.typography.bodyLarge, color = Slitok.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                MoneyText(signedSum(row.totalMinor), style = Amounts.Small, color = if (row.totalMinor > 0L) Slitok.Mint else Slitok.Fg)
                Icon(
                    if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null, tint = Slitok.Faint, modifier = Modifier.size(20.dp)
                )
            }
            if (expanded) row.children.forEach { Box(Modifier.padding(start = 16.dp)) { EventRow(it, onTap) } }
        }
    }
}

@Composable
private fun EventRow(e: HistoryEvent, onTap: (HistoryEvent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (e.target != null) Modifier.clickable { onTap(e) } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Dot(e.part.color())
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.bodyLarge, color = Slitok.Fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            e.subtitle?.let { MoneyText(it, style = MaterialTheme.typography.bodySmall, color = Slitok.Muted, maxLines = if (e.reason != null) 2 else 1) }
        }
        e.amountMinor?.let { MoneyText(signedSum(it), style = Amounts.Small, color = if (it > 0L) Slitok.Mint else Slitok.Fg) }
        if (e.target != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Slitok.Faint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Caption(text: String, color: Color = Slitok.Muted, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color, modifier = modifier.padding(start = 4.dp, top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
internal fun deltaText(c: CapitalChange, compact: Boolean = false): String =
    (if (compact) MoneyCompact.short(c.deltaMinor, signed = true) else signedSum(c.deltaMinor)) + (c.percentBp?.let { " · " + signedPercent(it) } ?: "")

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
