@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package ru.maxrubtsoff.bankagg.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.key
import ru.maxrubtsoff.bankagg.ui.common.sectionSwipe
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
import ru.maxrubtsoff.bankagg.domain.model.DaySpan
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
import ru.maxrubtsoff.bankagg.ui.common.BankIcon
import ru.maxrubtsoff.bankagg.ui.capital.CurrencyBadge
import ru.maxrubtsoff.bankagg.ui.common.HistoryLine
import ru.maxrubtsoff.bankagg.ui.common.signedSum
import ru.maxrubtsoff.bankagg.domain.model.HistoryRows
import ru.maxrubtsoff.bankagg.domain.model.RowKind
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

    // The pager lives here so the swipe can take the whole screen, header and empty space included (B-75).
    val first = ui
    val sections = first?.sections ?: listOf<CapitalPart?>(null)
    val swipes = (first?.kept?.size ?: 0) > 1
    val pager = key(first != null) {
        rememberPagerState(initialPage = first?.let { CapitalHistory.pageOf(it.part, sections) } ?: 0) { if (swipes) sections.size else 1 }
    }
    Scaffold(modifier = Modifier.sectionSwipe(pager), topBar = { ScreenHeader("История капитала", onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val u = ui
            if (u == null) {
                Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator(color = Slitok.Brass) }
            } else {
                HistorySections(u, vm, onTap, pager)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** One row of sections, swiped sideways; the period is common to all of them (F-45, B-73). */
@Composable
private fun HistorySections(u: HistoryScreenUi, vm: CapitalHistoryViewModel, onTap: (HistoryEvent) -> Unit, pager: PagerState) {
    val sections = u.sections
    // One kept section needs no switch: the total already is that section.
    val swipes = u.kept.size > 1
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
    SectionPager(pager, swipe = false) { page -> HistoryContent(u.pages[page], vm, onTap) }
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
    // By key: «d:<day>» a day's caption, a row's id, «m:<month>» a month's header.
    val requesters = remember { mutableMapOf<String, BringIntoViewRequester>() }
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
    // The days the picked point stands for: a day, or a week on a year and all-time (F-46).
    val span = selected?.let { CapitalHistory.spanAt(u.series, it) }
    // Let go of the chart: the list moves to what touches the span, or to the nearest day with events
    // (a folded month opens first).
    LaunchedEffect(scrollTick) {
        if (scrollTick == 0 || span == null) return@LaunchedEffect
        val day = CapitalEvents.focusDay(u.events, span) ?: return@LaunchedEffect
        if (u.months.isEmpty()) {
            requesters["d:$day"]?.bringIntoView()
            return@LaunchedEffect
        }
        val month = YearMonth.from(day)
        if (month.toString() !in open) {
            open = open + month.toString()
            withFrameNanos { }
            withFrameNanos { }
        }
        val rows = u.months.firstOrNull { it.month == month }?.rows.orEmpty()
        val row = HistoryRows.firstTouching(rows, span) ?: rows.firstOrNull { r -> r.sources.any { it.date == day } }
        (row?.let { requesters[it.id] } ?: requesters["m:$month"])?.bringIntoView()
    }
    Panel(Modifier.fillMaxWidth(), radius = 22.dp) {
        Box(Modifier.fillMaxWidth().heroGlow(color)) {
            Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 12.dp)) {
                AmountText(Money.ofMinor(at?.minor ?: change.to.minor).format(), style = Amounts.Hero, color = Slitok.Fg, minSize = 20.sp)
                Spacer(Modifier.height(2.dp))
                if (at != null && span != null) {
                    Text(
                        if (span.days == 1) RuDays.dayWeekday(at.date, u.today) else RuDays.span(span.from, span.to, u.today),
                        style = MaterialTheme.typography.labelMedium, color = Slitok.Brass2, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    val step = CapitalHistory.stepAt(u.series, selected ?: 0)
                    val word = RuDays.spanWord(span.days)
                    if (step != null && step != 0L) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                            MoneyText(
                                signedSum(step), style = MaterialTheme.typography.bodyMedium,
                                color = if (step > 0L) Slitok.Mint else Slitok.Fg, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f, fill = false), compact = MoneyCompact.short(step, signed = true)
                            )
                            Text(word, style = MaterialTheme.typography.bodySmall, color = Slitok.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        Text("без изменений $word", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
                    }
                } else if (change.deltaMinor == 0L) {
                    Text("без изменений ${u.period.spanWord}", style = MaterialTheme.typography.bodyMedium, color = Slitok.Muted)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        MoneyText(
                            deltaText(change), style = MaterialTheme.typography.bodyMedium,
                            color = if (change.deltaMinor > 0L) Slitok.Mint else Slitok.Fg, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false), compact = deltaText(change, compact = true)
                        )
                        MoneyText("с ${RuDays.dayMonth(change.from.date, u.today)}", style = MaterialTheme.typography.bodySmall, color = Slitok.Muted, maxLines = 2)
                    }
                }
                Spacer(Modifier.height(12.dp))
                CapitalChart(points = u.series.map { it.minor }, axis = axisOf(u), selected = selected,
                    onScrub = { picked = u.series[it].date.toEpochDay() }, onRelease = { scrollTick++ }, color = color,
                    range = if (selected != null && span != null && span.days > 1) (selected - 1)..selected else null
                )
                Spacer(Modifier.height(10.dp))
                PeriodChips(u.period, onPick = { picked = null; vm.setPeriod(it) }, compact = true)
            }
        }
    }

    Events(u, span, open, { open = it }, requesters, onReset = { picked = null }, onTap = onTap)
}

@Composable
private fun Events(
    u: HistoryUi,
    span: DaySpan?,
    open: List<String>,
    setOpen: (List<String>) -> Unit,
    requesters: MutableMap<String, BringIntoViewRequester>,
    onReset: () -> Unit,
    onTap: (HistoryEvent) -> Unit
) {
    // Folded groups of the list that are open, by id (F-39).
    var groupsOpen by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val events = u.events
    if (span != null) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Caption(RuDays.span(span.from, span.to, u.today), Slitok.Brass2, Modifier.weight(1f))
            SlitokChip("Сбросить", selected = false, onClick = onReset, compact = true)
        }
    }
    fun mark(key: String) = Modifier.bringIntoViewRequester(requesters.getOrPut(key) { BringIntoViewRequester() })
    val toggle: (String) -> Unit = { id -> groupsOpen = if (id in groupsOpen) groupsOpen - id else groupsOpen + id }
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
                    HistoryLine(
                        RuDays.monthTitle(m.month, u.today), RuPlural.events(m.count), m.deltaMinor, RowKind.OPEN,
                        modifier = mark("m:$key"), expanded = expanded, onClick = { setOpen(if (expanded) open - key else open + key) }
                    )
                    if (expanded) {
                        m.rows.forEach { r ->
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Slitok.Line)
                            Column(mark(r.id)) { RowItem(r, span, groupsOpen, toggle, onTap) }
                        }
                    }
                }
            }
        }
        else -> u.days.forEach { d ->
            val day = d.date
            Caption(dayCaption(day, u.today), if (span != null && day in span) Slitok.Brass2 else Slitok.Muted, mark("d:$day"))
            Panel(Modifier.fillMaxWidth()) {
                d.rows.forEachIndexed { i, r ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Slitok.Line)
                    RowItem(r, span, groupsOpen, toggle, onTap)
                }
            }
        }
    }
}

/** A single event, or a folded group that opens on tap into its events (F-39, F-50). */
@Composable
private fun RowItem(row: HistoryRow, span: DaySpan?, opened: List<String>, toggle: (String) -> Unit, onTap: (HistoryEvent) -> Unit) {
    // A bar on the left of what the picked point of the chart touched (F-46).
    val lit = HistoryRows.touches(row, span)
    fun litChild(e: HistoryEvent) = span != null && e.date in span
    when (row) {
        is HistoryRow.Single -> {
            val e = row.event
            val expanded = e.parts.isNotEmpty() && row.id in opened
            EventRow(e, onTap, expanded = expanded, highlighted = lit, onToggle = { toggle(row.id) })
            if (expanded) e.parts.forEach { EventRow(it, onTap, nested = true, highlighted = lit) }
        }
        is HistoryRow.Group -> {
            val expanded = row.id in opened
            val kind = row.rowKind
            HistoryLine(
                row.title, row.subtitle, row.totalMinor, kind, expanded = expanded, highlighted = lit,
                lead = { Dot(row.children.first().part.color()) },
                // A month's interest on one deposit leads to it; the other groups open.
                onClick = { if (kind == RowKind.GO) onTap(row.children.first()) else toggle(row.id) }
            )
            if (expanded && kind == RowKind.OPEN) row.children.forEach { EventRow(it, onTap, nested = true, highlighted = litChild(it)) }
        }
    }
}

@Composable
private fun EventRow(
    e: HistoryEvent, onTap: (HistoryEvent) -> Unit, nested: Boolean = false, expanded: Boolean = false,
    highlighted: Boolean = false, onToggle: () -> Unit = {}
) {
    val kind = HistoryRows.kindOf(e)
    val target = e.target
    HistoryLine(
        e.title, e.subtitle, e.amountMinor, kind, nested = nested, expanded = expanded, highlighted = highlighted,
        subtitleLines = if (e.reason != null) 2 else 1,
        lead = {
            when {
                e.bank != null -> BankIcon(e.bank, 28.dp)
                target is HistoryTarget.Currency && nested -> CurrencyBadge(target.code, 28.dp)
                else -> Dot(e.part.color())
            }
        },
        onClick = { if (kind == RowKind.OPEN) onToggle() else onTap(e) }
    )
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

/** Hundredths of a percent → "+0,6 %" (one decimal, no ",0"). */
internal fun signedPercent(bp: Long): String {
    val tenths = (kotlin.math.abs(bp) + 5) / 10
    val num = if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10},${tenths % 10}"
    val sign = if (tenths == 0L) "" else if (bp > 0) "+" else "−"
    return sign + num + Money.NBSP + "%"
}
