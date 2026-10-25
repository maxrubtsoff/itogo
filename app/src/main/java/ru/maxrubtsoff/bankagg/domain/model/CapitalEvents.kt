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
    val amountMinor: Long?,
    /** Why the currency was revalued (F-42); null for every other event. */
    val reason: RevaluationReason? = null,
    val kind: HistoryKind = HistoryKind.TRANSACTION,
    /** Where a tap leads (F-44); null — nowhere. */
    val target: HistoryTarget? = null
)

/** What an event is, so the list can tell them apart and the screen can lead to the object. */
enum class HistoryKind { TRANSACTION, FX_DEAL, DEPOSIT_OPEN, DEPOSIT_TOPUP, DEPOSIT_CLOSE, INTEREST, REVALUATION, CASH }

/** The object an event is about (F-44). */
sealed interface HistoryTarget {
    data class Deposit(val id: String) : HistoryTarget
    data class Currency(val code: String) : HistoryTarget
    data class Section(val part: CapitalPart) : HistoryTarget
}

/** One currency's share in a revaluation: its balance and the rate on both days (F-42). */
data class RevaluationLeg(
    val currency: String,
    val fromRateMicro: Long,
    val toRateMicro: Long,
    val qtyMinor: Long,
    val effectMinor: Long
)

/** What moved the currency part: rate [legs] and the [manualMinor] left that the rates do not explain. */
data class RevaluationReason(val legs: List<RevaluationLeg>, val manualMinor: Long)

/** The line under «Переоценка валюты» (F-42). */
object RevaluationText {

    /** "91,20" — a rate in micro-rubles, two decimals, HALF_UP. */
    fun formatRate(micro: Long): String =
        java.math.BigDecimal.valueOf(micro, 6).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',')

    fun subtitle(reason: RevaluationReason): String {
        val legs = reason.legs
        val manual = if (reason.manualMinor != 0L) " · остаток изменили вручную" else ""
        return when {
            legs.isEmpty() -> "Курс или остаток изменились"
            legs.size == 1 -> {
                val l = legs[0]
                "${l.currency} ${formatRate(l.fromRateMicro)} → ${formatRate(l.toRateMicro)} ₽ · " +
                    Money.ofMinor(l.qtyMinor).format(l.currency) + manual
            }
            else -> "Курс " + legs.joinToString(", ") { it.currency } + manual
        }
    }
}

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
        zone: ZoneId = ZoneId.systemDefault(),
        rates: List<FxRate> = emptyList()
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
            val target = if (t.currency == Currencies.RUB) HistoryTarget.Section(CapitalPart.ACCOUNTS) else HistoryTarget.Currency(t.currency)
            out += HistoryEvent(
                dayOf(t.postedAt, zone), part, title, t.counterparty ?: titleOf[t.accountId], t.amount.minor,
                kind = HistoryKind.TRANSACTION, target = target
            )
        }
        fxOps.forEach { o ->
            val sym = Currencies.symbolOf(o.currency)
            val sum = Money.ofMinor(o.amountMinor).formatNumber()
            val rub = Money.ofMinor(o.rubMinor).format()
            out += HistoryEvent(
                o.date, CapitalPart.CURRENCY,
                if (o.kind == FxKind.BUY) "Купили $sum $sym" else "Продали $sum $sym",
                if (o.kind == FxKind.BUY) "за $rub" else "получили $rub",
                null,
                kind = HistoryKind.FX_DEAL, target = HistoryTarget.Currency(o.currency)
            )
        }
        deposits.forEach { d ->
            val name = d.title ?: d.brand.title
            val to = HistoryTarget.Deposit(d.id)
            out += HistoryEvent(d.startDate, CapitalPart.DEPOSITS, "Новый вклад", name, d.principal.minor, kind = HistoryKind.DEPOSIT_OPEN, target = to)
            d.topUps.forEach {
                out += HistoryEvent(it.date, CapitalPart.DEPOSITS, "Пополнение вклада", name, it.amount.minor, kind = HistoryKind.DEPOSIT_TOPUP, target = to)
            }
            if (d.endDate.isBefore(today)) out += HistoryEvent(d.endDate, CapitalPart.DEPOSITS, "Вклад закрыт", name, null, kind = HistoryKind.DEPOSIT_CLOSE, target = to)
        }
        if (cash != null && cash.updatedAt > 0L) {
            out += HistoryEvent(dayOf(cash.updatedAt, zone), CapitalPart.CASH, "Наличные", "изменили вручную", null, kind = HistoryKind.CASH, target = HistoryTarget.Section(CapitalPart.CASH))
        }
        out += moved(points, fxOps, deposits, rates)
        return out.filter { !it.date.isAfter(today) }.sortedByDescending { it.date }
    }

    /** «Проценты» and «Переоценка валюты»: what a part moved from one day to the next beyond the deals themselves. */
    private fun moved(points: List<CapitalPoint>, fxOps: List<FxOperation>, deposits: List<Deposit>, rates: List<FxRate>): List<HistoryEvent> {
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
            val liveDeps = deposits.filter { DepositMath.isActive(it, cur.date) && !cur.date.isBefore(it.startDate) }
            val live = liveDeps.size
            if (interest > 0L && live > 0) {
                val target = if (live == 1) HistoryTarget.Deposit(liveDeps[0].id) else HistoryTarget.Section(CapitalPart.DEPOSITS)
                out += HistoryEvent(cur.date, CapitalPart.DEPOSITS, "Проценты", RuPlural.deposits(live), interest, kind = HistoryKind.INTEREST, target = target)
            }
            val dealt = fxOps.filter { it.date == cur.date }.sumOf { if (it.kind == FxKind.BUY) it.rubMinor else -it.rubMinor }
            val revalued = cur.valueOf(CapitalPart.CURRENCY) - prev.valueOf(CapitalPart.CURRENCY) - dealt
            if (revalued != 0L) {
                val reason = reasonOf(prev.date, cur.date, revalued, fxOps, rates)
                out += HistoryEvent(cur.date, CapitalPart.CURRENCY, "Переоценка валюты", RevaluationText.subtitle(reason), revalued, reason,
                    kind = HistoryKind.REVALUATION,
                    target = reason.legs.maxByOrNull { kotlin.math.abs(it.effectMinor) }?.let { HistoryTarget.Currency(it.currency) }
                        ?: HistoryTarget.Section(CapitalPart.CURRENCY)
                )
            }
        }
        return out
    }

    /** Rates explain what they can: balance on [from] times the rate change; the rest is a manual change. */
    private fun reasonOf(from: LocalDate, to: LocalDate, revalued: Long, fxOps: List<FxOperation>, rates: List<FxRate>): RevaluationReason {
        fun rateOn(currency: String, day: LocalDate): Long? =
            rates.filter { it.currency == currency && !it.date.isAfter(day) }.maxByOrNull { it.date }?.rubPerUnitMicro
        val legs = fxOps.map { it.currency }.distinct().mapNotNull { cur ->
            val qty = fxOps.filter { it.currency == cur && !it.date.isAfter(from) }
                .sumOf { if (it.kind == FxKind.BUY) it.amountMinor else -it.amountMinor }
            val r0 = rateOn(cur, from)
            val r1 = rateOn(cur, to)
            if (qty == 0L || r0 == null || r1 == null || r0 == r1) return@mapNotNull null
            val effect = FxMath.rubOf(qty, r1) - FxMath.rubOf(qty, r0)
            if (effect == 0L) null else RevaluationLeg(cur, r0, r1, qty, effect)
        }
        val rest = revalued - legs.sumOf { it.effectMinor }
        val manual = if (kotlin.math.abs(rest) < maxOf(100L, kotlin.math.abs(revalued) / 100)) 0L else rest
        return RevaluationReason(legs, manual)
    }

    /** False when [target] is a deposit that no longer exists. */
    fun isAlive(target: HistoryTarget, deposits: List<Deposit>): Boolean =
        target !is HistoryTarget.Deposit || deposits.any { it.id == target.id }

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
