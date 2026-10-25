package ru.maxrubtsoff.bankagg.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** One line of the history list: what happened to the capital on [date] (F-36). */
data class HistoryEvent(
    val date: LocalDate,
    val part: CapitalPart,
    val title: String,
    val subtitle: String?,
    /** Signed kopecks; null when the event has no sum of its own. */
    val amountMinor: Long?
)

/** Events of one month, for the folded list of a year (F-36). */
data class MonthEvents(val month: YearMonth, val events: List<HistoryEvent>, val deltaMinor: Long)

/**
 * Builds the list under the chart from the first sources — operations, top-ups,
 * currency deals, cash — not from the daily points. Interest on deposits and the
 * revaluation of currency are one line per day, taken from what the points moved
 * beyond the events themselves (F-36).
 */
object CapitalEvents {

    fun build(
        transactions: List<Transaction>,
        accounts: List<Account>,
        fxOps: List<FxOperation>,
        deposits: List<Deposit>,
        cash: CashBalance?,
        points: List<CapitalPoint>,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<HistoryEvent> {
        val titleOf = accounts.associate { it.id to it.title }
        val out = ArrayList<HistoryEvent>()

        transactions.forEach { t ->
            val title = when (t.kind) {
                TransactionKind.INCOME -> "Пополнение"
                TransactionKind.EXPENSE -> "Списание"
                TransactionKind.TRANSFER -> "Перевод"
                TransactionKind.UNKNOWN -> "Операция"
            }
            val part = if (t.currency == Currencies.RUB) CapitalPart.ACCOUNTS else CapitalPart.CURRENCY
            out += HistoryEvent(dayOf(t.postedAt, zone), part, title, t.counterparty ?: titleOf[t.accountId], t.amount.minor)
        }
        fxOps.forEach { o ->
            val sym = Currencies.symbolOf(o.currency)
            val sum = Money.ofMinor(o.amountMinor).formatNumber()
            val rub = Money.ofMinor(o.rubMinor).format()
            out += HistoryEvent(
                o.date, CapitalPart.CURRENCY,
                if (o.kind == FxKind.BUY) "Купили $sum $sym" else "Продали $sum $sym",
                if (o.kind == FxKind.BUY) "за $rub" else "получили $rub",
                null
            )
        }
        deposits.forEach { d ->
            val name = d.title ?: d.brand.title
            out += HistoryEvent(d.startDate, CapitalPart.DEPOSITS, "Новый вклад", name, d.principal.minor)
            d.topUps.forEach { out += HistoryEvent(it.date, CapitalPart.DEPOSITS, "Пополнение вклада", name, it.amount.minor) }
            if (d.endDate.isBefore(today)) out += HistoryEvent(d.endDate, CapitalPart.DEPOSITS, "Вклад закрыт", name, null)
        }
        if (cash != null && cash.updatedAt > 0L) {
            out += HistoryEvent(dayOf(cash.updatedAt, zone), CapitalPart.CASH, "Наличные", "изменили вручную", null)
        }
        out += moved(points, fxOps, deposits)
        return out.filter { !it.date.isAfter(today) }.sortedByDescending { it.date }
    }

    /** «Проценты» and «Переоценка валюты»: what a part moved from one day to the next beyond the deals themselves. */
    private fun moved(points: List<CapitalPoint>, fxOps: List<FxOperation>, deposits: List<Deposit>): List<HistoryEvent> {
        val sorted = points.sortedBy { it.date }
        val out = ArrayList<HistoryEvent>()
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val cur = sorted[i]
            val own = deposits.sumOf { d ->
                (if (d.startDate == cur.date) d.principal.minor else 0L) +
                    d.topUps.filter { it.date == cur.date }.sumOf { it.amount.minor }
            }
            val interest = cur.valueOf(CapitalPart.DEPOSITS) - prev.valueOf(CapitalPart.DEPOSITS) - own
            val live = deposits.count { DepositMath.isActive(it, cur.date) && !cur.date.isBefore(it.startDate) }
            if (interest > 0L && live > 0) {
                out += HistoryEvent(cur.date, CapitalPart.DEPOSITS, "Проценты", RuPlural.deposits(live), interest)
            }
            val dealt = fxOps.filter { it.date == cur.date }.sumOf { if (it.kind == FxKind.BUY) it.rubMinor else -it.rubMinor }
            val revalued = cur.valueOf(CapitalPart.CURRENCY) - prev.valueOf(CapitalPart.CURRENCY) - dealt
            if (revalued != 0L) out += HistoryEvent(cur.date, CapitalPart.CURRENCY, "Переоценка валюты", null, revalued)
        }
        return out
    }

    /** Events of [period] (and of one [part], null — all), newest first. */
    fun within(events: List<HistoryEvent>, from: LocalDate?, part: CapitalPart?): List<HistoryEvent> =
        events.filter { (from == null || !it.date.isBefore(from)) && (part == null || it.part == part) }

    /** Months with their events, newest first; the delta of each month comes from [points]. */
    fun byMonth(events: List<HistoryEvent>, points: List<CapitalPoint>, part: CapitalPart?): List<MonthEvents> =
        events.groupBy { YearMonth.from(it.date) }
            .map { (m, list) -> MonthEvents(m, list, CapitalHistory.monthDelta(points, m, part)) }
            .sortedByDescending { it.month }

    private fun dayOf(epochMs: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
}
