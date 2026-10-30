package ru.maxrubtsoff.bankagg.domain.model

import java.time.LocalDate

/** What a history line does on tap (F-50): leads to an object, opens into parts, or is just a sum. */
enum class RowKind { GO, OPEN, SUM }

/** Rows of the history list: what each line is and how the lines are grouped. */
object HistoryRows {

    fun kindOf(e: HistoryEvent): RowKind = when {
        e.parts.isNotEmpty() -> RowKind.OPEN
        // A tap opens the sheet of the operation (F-51); the way to the object is inside it.
        e.target != null || e.ref != null -> RowKind.GO
        else -> RowKind.SUM
    }

    /** True when any event inside the row happened in the picked [span] of the chart (F-46). */
    fun touches(row: HistoryRow, span: DaySpan?): Boolean = span != null && row.sources.any { it.date in span }

    /** The first of [rows] the [span] touches; null when none does. */
    fun firstTouching(rows: List<HistoryRow>, span: DaySpan?): HistoryRow? = rows.firstOrNull { touches(row = it, span = span) }

    /** Ruble spendings and transfers are only a sum; a group with a currency child opens. */
    fun groupKind(eventKind: HistoryKind, children: List<HistoryEvent>): RowKind =
        if (eventKind in SUMMED && children.all { it.target == null && it.ref == null }) RowKind.SUM else RowKind.OPEN

    private val SUMMED = setOf(HistoryKind.SPEND, HistoryKind.TRANSFER, HistoryKind.TRANSACTION)

    /**
     * Rows of a day (week and month pages, F-48): events of one kind and part fold into one row
     * when there are two or more; the order is the order the events came in. Newest day first.
     */
    fun days(events: List<HistoryEvent>, page: CapitalPart?): List<HistoryDay> =
        events.groupBy { it.date }.toSortedMap(compareByDescending { it }).map { (day, list) ->
            val buckets = list.groupBy { it.kind to it.part }
            val seen = HashSet<Pair<HistoryKind, CapitalPart>>()
            val rows = ArrayList<HistoryRow>()
            list.forEachIndexed { i, e ->
                val key = e.kind to e.part
                val same = buckets.getValue(key)
                when {
                    same.size < 2 -> rows += HistoryRow.Single(e, id = "s:${day.toEpochDay()}:$i")
                    seen.add(key) -> rows += group("d:${day.toEpochDay()}:${e.kind.name}:${e.part.name}", e.kind, e.part, same, same, page, null)
                }
            }
            HistoryDay(day, rows)
        }

    /**
     * Rows of a month for the year and all-time pages (F-48): groups by meaning of the page, the biggest
     * by sum first. Interest and revaluation are one row each, split into parts by deposit / currency.
     */
    fun months(months: List<MonthEvents>, page: CapitalPart?, today: LocalDate): List<HistoryMonth> =
        months.map { m -> HistoryMonth(m.month, m.events.size, m.deltaMinor, monthRows(m, page, today)) }

    private fun monthRows(m: MonthEvents, page: CapitalPart?, today: LocalDate): List<HistoryRow> {
        val rows = ArrayList<HistoryRow>()
        m.events.groupBy { it.kind to it.part }.forEach { (key, same) ->
            val (kind, part) = key
            val id = "m:${m.month}:${kind.name}:${part.name}"
            rows += when {
                kind == HistoryKind.INTEREST -> interest(id, same, page)
                kind == HistoryKind.REVALUATION -> revaluation(id, same, page)
                same.size < 2 -> HistoryRow.Single(dated(same[0], today), id, same)
                else -> group(id, kind, part, same, same.map { dated(it, today) }, page, today)
            }
        }
        return rows.sortedWith(compareByDescending<HistoryRow> { kotlin.math.abs(totalOf(it)) }.thenBy { kindOf(it).ordinal })
    }

    private fun totalOf(r: HistoryRow): Long = when (r) {
        is HistoryRow.Single -> r.event.amountMinor ?: 0L
        is HistoryRow.Group -> r.totalMinor
    }

    private fun kindOf(r: HistoryRow): HistoryKind = when (r) {
        is HistoryRow.Single -> r.event.kind
        is HistoryRow.Group -> r.kind
    }

    private fun group(
        id: String, kind: HistoryKind, part: CapitalPart, sources: List<HistoryEvent>, children: List<HistoryEvent>,
        page: CapitalPart?, today: LocalDate?
    ) = HistoryRow.Group(
        id, sources.maxOf { it.date }, kind, sources.size, sources.sumOf { it.amountMinor ?: 0L }, children,
        groupTitle(kind, part, sources.size, page), groupSubtitle(kind, sources, today), groupKind(kind, sources), sources = sources
    )

    /** The event with its date in the line, for a row that stands for a whole month. */
    private fun dated(e: HistoryEvent, today: LocalDate): HistoryEvent =
        e.copy(subtitle = listOfNotNull(e.subtitle, RuDays.dayMonth(e.date, today)).joinToString(" · "))

    /** A day's interest on one deposit as a part, shaped like the parts of a many-deposit day: «name» over the bank. */
    private fun depositPart(e: HistoryEvent): HistoryEvent {
        val name = e.subtitle ?: return e
        val bank = e.bank?.title ?: return e.copy(title = name, subtitle = null)
        return if (name == bank) e.copy(title = bank, subtitle = null) else e.copy(title = "«$name»", subtitle = bank)
    }

    /** A month's interest: one row, split by deposit. One deposit — the row leads to it. */
    private fun interest(id: String, same: List<HistoryEvent>, page: CapitalPart?): HistoryRow {
        val byDeposit = LinkedHashMap<String, HistoryEvent>()
        same.forEach { e ->
            val items = if (e.parts.isNotEmpty()) e.parts else listOf(depositPart(e))
            items.forEach { p ->
                val dep = p.target as? HistoryTarget.Deposit ?: return@forEach
                val prev = byDeposit[dep.id]
                byDeposit[dep.id] = if (prev == null) p else
                    (if (prev.subtitle == null && p.subtitle != null) p else prev)
                        .copy(amountMinor = (prev.amountMinor ?: 0L) + (p.amountMinor ?: 0L), date = maxOf(prev.date, p.date))
            }
        }
        if (byDeposit.isEmpty()) return HistoryRow.Single(same[0], id, same)
        val parts = byDeposit.values.sortedByDescending { kotlin.math.abs(it.amountMinor ?: 0L) }
        val total = same.sumOf { it.amountMinor ?: 0L }
        val date = same.maxOf { it.date }
        val title = groupTitle(HistoryKind.INTEREST, CapitalPart.DEPOSITS, parts.size, page)
        if (parts.size >= 2) {
            return HistoryRow.Group(id, date, HistoryKind.INTEREST, same.size, total, parts, title, RuPlural.deposits(parts.size), RowKind.OPEN, sources = same)
        }
        val p = parts.single()
        val name = if (p.subtitle != null) "${p.subtitle} ${p.title}" else p.title
        return HistoryRow.Group(id, date, HistoryKind.INTEREST, same.size, total, parts, title, name, RowKind.GO, p.target, same)
    }

    /** A month's revaluation: one row, split by currency — the first rate of the month to the last. */
    private fun revaluation(id: String, same: List<HistoryEvent>, page: CapitalPart?): HistoryRow {
        val legs = LinkedHashMap<String, RevaluationLeg>()
        var manual = 0L
        same.sortedBy { it.date }.forEach { e ->
            val r = e.reason ?: return@forEach
            manual += r.manualMinor
            r.legs.forEach { l ->
                val prev = legs[l.currency]
                legs[l.currency] = if (prev == null) l else RevaluationLeg(l.currency, prev.fromRateMicro, l.toRateMicro, l.qtyMinor, prev.effectMinor + l.effectMinor)
            }
        }
        val date = same.maxOf { it.date }
        val total = same.sumOf { it.amountMinor ?: 0L }
        // One currency through the month — the line opens its accounts (F-55).
        val manualRefs = same.flatMap { it.parts + it }.mapNotNull { (it.ref as? HistoryRef.Balance)?.currency }.distinct()
        val parts = legs.values.sortedByDescending { kotlin.math.abs(it.effectMinor) }.map { l ->
            HistoryEvent(
                date, CapitalPart.CURRENCY, Currencies.nameOf(l.currency),
                "${RevaluationText.formatRate(l.fromRateMicro)} → ${RevaluationText.formatRate(l.toRateMicro)} ₽ за месяц", l.effectMinor,
                kind = HistoryKind.REVALUATION, target = HistoryTarget.Currency(l.currency)
            )
        } + (if (manual != 0L) listOf(
            HistoryEvent(date, CapitalPart.CURRENCY, "Остаток изменили вручную", legs.values.singleOrNull()?.let { Currencies.nameOf(it.currency) }, manual, kind = HistoryKind.REVALUATION,
                ref = manualRefs.singleOrNull()?.let { HistoryRef.Balance(it) })
        ) else emptyList())
        val title = groupTitle(HistoryKind.REVALUATION, CapitalPart.CURRENCY, 1, page)
        val subtitle = RevaluationText.subtitle(RevaluationReason(legs.values.toList(), manual))
        if (parts.size >= 2) return HistoryRow.Group(id, date, HistoryKind.REVALUATION, same.size, total, parts, title, subtitle, RowKind.OPEN, sources = same)
        val target = legs.values.singleOrNull()?.let { HistoryTarget.Currency(it.currency) } ?: HistoryTarget.Section(CapitalPart.CURRENCY)
        val child = parts.firstOrNull()?.copy(target = target)
            ?: HistoryEvent(date, CapitalPart.CURRENCY, title, subtitle, total, kind = HistoryKind.REVALUATION, target = target)
        return HistoryRow.Group(id, date, HistoryKind.REVALUATION, same.size, total, listOf(child), title, subtitle, RowKind.GO, target, same)
    }

    /** Title of a group by what its page is about (the table of groups in the design README). */
    fun groupTitle(kind: HistoryKind, part: CapitalPart, n: Int, page: CapitalPart?): String {
        val inCurrency = if (part == CapitalPart.CURRENCY) " в валюте" else ""
        return when (kind) {
            HistoryKind.INCOME -> "Поступления$inCurrency · $n"
            HistoryKind.SPEND -> "Списания$inCurrency"
            HistoryKind.TRANSFER -> "Переводы$inCurrency"
            HistoryKind.TRANSACTION -> "Операции$inCurrency"
            HistoryKind.OWN_TRANSFER -> "Переводы между счетами · $n"
            HistoryKind.DEPOSIT_OPEN -> "Новые вклады · $n"
            HistoryKind.DEPOSIT_TOPUP -> if (page == CapitalPart.DEPOSITS) "Пополнения · $n" else "Пополнения вкладов · $n"
            HistoryKind.DEPOSIT_CLOSE -> "Закрытые вклады · $n"
            HistoryKind.FX_BUY -> if (page == CapitalPart.CURRENCY) "Покупки · $n" else "Покупки валюты · $n"
            HistoryKind.FX_SELL -> if (page == CapitalPart.CURRENCY) "Продажи · $n" else "Продажи валюты · $n"
            HistoryKind.CASH -> if (page == CapitalPart.CASH) "Изменения · $n" else "Наличные · $n"
            HistoryKind.ACCOUNT_OPEN -> "Остатки счетов · $n"
            HistoryKind.INTEREST -> "Проценты"
            HistoryKind.REVALUATION -> if (page == CapitalPart.CURRENCY) "Переоценка" else "Переоценка валюты"
        }
    }

    private fun groupSubtitle(kind: HistoryKind, events: List<HistoryEvent>, today: LocalDate?): String? = when {
        kind in SUMMED -> RuPlural.operations(events.size)
        kind == HistoryKind.CASH && today != null -> dates(events, today)
        else -> names(events)
    }

    /** Up to two different lines of the [children], then «и ещё N»; null when none has a line. */
    fun names(children: List<HistoryEvent>): String? {
        val subs = children.mapNotNull { it.subtitle }.distinct()
        if (subs.isEmpty()) return null
        return subs.take(2).joinToString(", ") + if (subs.size > 2) " и ещё ${subs.size - 2}" else ""
    }

    /** The days of the [events]: «5 и 17 октября», «5 октября, 3 ноября». */
    private fun dates(events: List<HistoryEvent>, today: LocalDate): String {
        val ds = events.map { it.date }.distinct().sorted()
        val one = { d: LocalDate -> RuDays.dayMonth(d, today) }
        return when {
            ds.size == 1 -> one(ds[0])
            ds.size == 2 && ds[0].year == ds[1].year && ds[0].month == ds[1].month -> "${ds[0].dayOfMonth} и ${one(ds[1])}"
            else -> ds.take(2).joinToString(", ", transform = one) + if (ds.size > 2) " и ещё ${ds.size - 2}" else ""
        }
    }
}
