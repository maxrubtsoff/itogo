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
    val target: HistoryTarget? = null,
    /** What the line opens into (F-47); empty — it does not open. */
    val parts: List<HistoryEvent> = emptyList(),
    /** The bank of a part that is a deposit, for its icon (F-47). */
    val bank: BankBrand? = null,
    /** A currency deal's sum in its own currency (F-48). */
    val foreign: ForeignAmount? = null,
    /** The record behind the line, for the sheet of the operation (F-51); null — nothing of its own to open. */
    val ref: HistoryRef? = null
)

/** The stored record an event stands for (F-51): what the sheet of an operation edits and deletes. */
sealed interface HistoryRef {
    data class Tx(val id: String) : HistoryRef
    data class Cash(val atMs: Long) : HistoryRef
    data class TopUp(val depositId: String, val topUpId: String) : HistoryRef
    data class Fx(val id: String) : HistoryRef
    /** A balance changed by hand (F-55): the sheet lists the accounts of [currency] to put right. */
    data class Balance(val currency: String, val merged: Boolean = false) : HistoryRef
}

/** What an event is, so the list can tell them apart and the screen can lead to the object. */
enum class HistoryKind {
    /** An operation of unknown kind. */
    TRANSACTION,
    INCOME, SPEND,
    /** Between the person's own accounts: never listed, kept for completeness. */
    OWN_TRANSFER,
    /** To another person. */
    TRANSFER,
    FX_BUY, FX_SELL, DEPOSIT_OPEN, DEPOSIT_TOPUP, DEPOSIT_CLOSE, INTEREST, REVALUATION, CASH,
    /** What an account held on the day it became known (B-109). */
    ACCOUNT_OPEN
}

/**
 * A row of the list (F-39, F-48): one event, or several of one kind folded into a group.
 * [sources] are the events inside, whatever the row shows — what the chart's picked days touch (F-46).
 */
sealed interface HistoryRow {
    val date: LocalDate
    val id: String
    val sources: List<HistoryEvent>

    data class Single(
        val event: HistoryEvent,
        /** Stable key for the opened state of an event with parts (F-47). */
        override val id: String = "${event.date.toEpochDay()}:${event.kind.name}:${event.title}",
        override val sources: List<HistoryEvent> = listOf(event)
    ) : HistoryRow {
        override val date: LocalDate get() = event.date
    }

    data class Group(
        override val id: String,
        override val date: LocalDate,
        val kind: HistoryKind,
        val count: Int,
        val totalMinor: Long,
        val children: List<HistoryEvent>,
        val title: String,
        val subtitle: String?,
        val rowKind: RowKind,
        /** Where a tap leads when [rowKind] is GO (a month's interest on one deposit). */
        val target: HistoryTarget? = null,
        override val sources: List<HistoryEvent> = children
    ) : HistoryRow
}

/** A day of the list with its rows (F-48). */
data class HistoryDay(val date: LocalDate, val rows: List<HistoryRow>)

/** A month of the list with its rows grouped by meaning (F-48). */
data class HistoryMonth(val month: YearMonth, val count: Int, val deltaMinor: Long, val rows: List<HistoryRow>)

/** A sum in a foreign currency, signed, in hundredths (F-48). */
data class ForeignAmount(val currency: String, val minor: Long)

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

    /** The folded line: figures only, no «cheaper / dearer» (F-47). */
    fun subtitle(reason: RevaluationReason): String {
        val legs = reason.legs
        if (legs.isEmpty()) return "Курс или остаток изменились"
        val manual = if (reason.manualMinor != 0L) " · остаток изменён" else ""
        return if (legs.size == 1) {
            val l = legs[0]
            "${Currencies.symbolOf(l.currency)} ${formatRate(l.fromRateMicro)} → ${formatRate(l.toRateMicro)} ₽" + manual
        } else {
            legs.joinToString(" · ") { l ->
                val d = l.toRateMicro - l.fromRateMicro
                "${Currencies.symbolOf(l.currency)} ${if (d < 0) "−" else "+"}${formatRate(kotlin.math.abs(d))} ₽"
            } + manual
        }
    }

    /** A currency's own line inside the opened revaluation: rate was → became · quantity. */
    fun partSubtitle(l: RevaluationLeg): String =
        "${formatRate(l.fromRateMicro)} → ${formatRate(l.toRateMicro)} ₽ · " + Money.ofMinor(l.qtyMinor).format(l.currency)
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
        rates: List<FxRate> = emptyList(),
        cashChanges: List<CashChange> = emptyList()
    ): List<HistoryEvent> {
        val titleOf = accounts.associate { it.id to it.title }
        val out = ArrayList<HistoryEvent>()

        val own = ownTransferIds(transactions, accounts, zone)
        transactions.filter { it.id !in own }.forEach { t ->
            val title = when (t.kind) {
                TransactionKind.INCOME -> "Пополнение"
                TransactionKind.EXPENSE -> "Списание"
                TransactionKind.TRANSFER -> "Перевод"
                TransactionKind.UNKNOWN -> "Операция"
            }
            val part = if (t.currency == Currencies.RUB) CapitalPart.ACCOUNTS else CapitalPart.CURRENCY
            // A ruble operation leads nowhere: the whole of «Счета» says nothing about it (F-50).
            val target = if (t.currency == Currencies.RUB) null else HistoryTarget.Currency(t.currency)
            val foreignOp = t.currency != Currencies.RUB
            // B-86: an account in a currency is shown in rubles at the CBR rate of its day; the own sum goes to the subtitle.
            val who = t.counterparty ?: titleOf[t.accountId]
            out += HistoryEvent(
                dayOf(t.postedAt, zone), part, title,
                if (foreignOp) listOfNotNull(who, Money.ofMinor(t.amount.minor).format(t.currency)).joinToString(" · ") else who,
                if (foreignOp) rubOf(t, rates, zone) else t.amount.minor,
                kind = when (t.kind) {
                    TransactionKind.INCOME -> HistoryKind.INCOME
                    TransactionKind.EXPENSE -> HistoryKind.SPEND
                    TransactionKind.TRANSFER -> HistoryKind.TRANSFER
                    TransactionKind.UNKNOWN -> HistoryKind.TRANSACTION
                },
                target = target,
                foreign = if (foreignOp) ForeignAmount(t.currency, t.amount.minor) else null,
                ref = HistoryRef.Tx(t.id)
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
                // The change of the «Валюта» part, in rubles (as in [moved]'s `dealt`).
                if (o.kind == FxKind.BUY) o.rubMinor else -o.rubMinor,
                kind = if (o.kind == FxKind.BUY) HistoryKind.FX_BUY else HistoryKind.FX_SELL, target = HistoryTarget.Currency(o.currency),
                foreign = ForeignAmount(o.currency, if (o.kind == FxKind.BUY) o.amountMinor else -o.amountMinor),
                ref = HistoryRef.Fx(o.id)
            )
        }
        deposits.forEach { d ->
            val name = d.title ?: d.brand.title
            val to = HistoryTarget.Deposit(d.id)
            out += HistoryEvent(d.startDate, CapitalPart.DEPOSITS, "Новый вклад", name, d.principal.minor, kind = HistoryKind.DEPOSIT_OPEN, target = to, bank = d.brand)
            d.topUps.forEach {
                out += HistoryEvent(it.date, CapitalPart.DEPOSITS, "Пополнение вклада", name, it.amount.minor, kind = HistoryKind.DEPOSIT_TOPUP, target = to, bank = d.brand, ref = HistoryRef.TopUp(d.id, it.id))
            }
            if (d.endDate.isBefore(today)) out += HistoryEvent(d.endDate, CapitalPart.DEPOSITS, "Вклад закрыт", name, -DepositMath.valueOn(d, d.endDate), kind = HistoryKind.DEPOSIT_CLOSE, target = to, bank = d.brand)
        }
        // F-48: every hand edit is an event with its delta; the lone «updatedAt» one is for cash edited before the log.
        cashChanges.forEach { c ->
            out += HistoryEvent(dayOf(c.atMs, zone), CapitalPart.CASH, "Наличные", "изменили вручную", c.delta.minor, kind = HistoryKind.CASH, target = HistoryTarget.Section(CapitalPart.CASH), ref = HistoryRef.Cash(c.atMs))
        }
        if (cash != null && cash.updatedAt > 0L && cashChanges.none { it.atMs == cash.updatedAt }) {
            out += HistoryEvent(dayOf(cash.updatedAt, zone), CapitalPart.CASH, "Наличные", "изменили вручную", null, kind = HistoryKind.CASH, target = HistoryTarget.Section(CapitalPart.CASH))
        }
        accounts.filterNot { it.excluded }.forEach { a ->
            val opening = CapitalReconstruction.openingOf(a, transactions)
            if (opening == 0L) return@forEach
            val from = CapitalReconstruction.knownFrom(a, transactions, zone)
            val rub = a.currency == Currencies.RUB
            out += HistoryEvent(
                from, if (rub) CapitalPart.ACCOUNTS else CapitalPart.CURRENCY, "Остаток на начало", a.title,
                if (rub) opening else CapitalReconstruction.rateAt(rates, a.currency, from)?.let { FxMath.rubOf(opening, it) },
                kind = HistoryKind.ACCOUNT_OPEN, bank = a.brand,
                foreign = if (rub) null else ForeignAmount(a.currency, opening),
                target = if (rub) null else HistoryTarget.Currency(a.currency)
            )
        }
        out += moved(points, fxOps, deposits, rates, transactions, zone, accounts)
        return out.filter { !it.date.isAfter(today) }.sortedByDescending { it.date }
    }

    /**
     * Ruble transfers that left one of the person's accounts and arrived at another the same day
     * for the same sum: both legs are the same money, so neither is listed. Pairs are 1:1.
     */
    private fun ownTransferIds(transactions: List<Transaction>, accounts: List<Account>, zone: ZoneId): Set<String> {
        val known = accounts.map { it.id }.toSet()
        val legs = transactions.filter {
            it.currency == Currencies.RUB && it.kind == TransactionKind.TRANSFER && it.accountId in known && it.amount.minor != 0L
        }
        val used = HashSet<String>()
        for (out in legs.filter { it.amount.minor < 0L }) {
            if (out.id in used) continue
            val day = dayOf(out.postedAt, zone)
            val back = legs.firstOrNull {
                it.id !in used && it.amount.minor == -out.amount.minor && it.accountId != out.accountId && dayOf(it.postedAt, zone) == day
            } ?: continue
            used += out.id
            used += back.id
        }
        return used
    }

    /** «Проценты» and «Переоценка валюты»: what a part moved from one day to the next beyond the deals themselves. */
    private fun moved(
        points: List<CapitalPoint>, fxOps: List<FxOperation>, deposits: List<Deposit>, rates: List<FxRate>,
        transactions: List<Transaction>, zone: ZoneId, accounts: List<Account>
    ): List<HistoryEvent> {
        val sorted = points.sortedBy { it.date }
        val out = ArrayList<HistoryEvent>()
        for (i in 1 until sorted.size) {
            val prev = sorted[i - 1]
            val cur = sorted[i]
            // A deposit put in between two points counts as put in, not as interest (B-85).
            val own = deposits.sumOf { d ->
                (if (d.startDate.inStep(prev.date, cur.date)) d.principal.minor else 0L) +
                    d.topUps.filter { it.date.inStep(prev.date, cur.date) }.sumOf { it.amount.minor }
            }
            val interest = cur.valueOf(CapitalPart.DEPOSITS) - prev.valueOf(CapitalPart.DEPOSITS) - own
            val liveDeps = deposits.filter { DepositMath.isActive(it, cur.date) && !cur.date.isBefore(it.startDate) }
            if (interest > 0L && liveDeps.isNotEmpty()) {
                // A deposit's weight: what it grew by, without what was put into it that day.
                val weights = liveDeps.map { d ->
                    val put = (if (d.startDate.inStep(prev.date, cur.date)) d.principal.minor else 0L) +
                        d.topUps.filter { it.date.inStep(prev.date, cur.date) }.sumOf { it.amount.minor }
                    d to maxOf(0L, DepositMath.valueOn(d, cur.date) - DepositMath.valueOn(d, prev.date) - put)
                }
                val shares = splitInterest(interest, weights, cur.date)
                fun name(d: Deposit) = d.title ?: d.brand.title
                if (shares.size >= 2) {
                    val parts = shares.map { (d, share) ->
                        HistoryEvent(cur.date, CapitalPart.DEPOSITS, d.title?.let { "«$it»" } ?: d.brand.title, d.title?.let { d.brand.title }, share,
                            kind = HistoryKind.INTEREST, target = HistoryTarget.Deposit(d.id), bank = d.brand)
                    }
                    out += HistoryEvent(cur.date, CapitalPart.DEPOSITS, "Проценты", RuPlural.deposits(shares.size), interest, kind = HistoryKind.INTEREST, parts = parts)
                } else {
                    val d = shares.single().first
                    out += HistoryEvent(cur.date, CapitalPart.DEPOSITS, "Проценты", name(d), interest, kind = HistoryKind.INTEREST,
                        target = HistoryTarget.Deposit(d.id), bank = d.brand)
                }
            }
            val dealt = fxOps.filter { it.date.inStep(prev.date, cur.date) }.sumOf { if (it.kind == FxKind.BUY) it.rubMinor else -it.rubMinor }
            // Operations of foreign-currency accounts are listed in rubles on their own (B-86): not a revaluation.
            val booked = transactions.filter { it.currency != Currencies.RUB && dayOf(it.postedAt, zone).inStep(prev.date, cur.date) }
                .sumOf { rubOf(it, rates, zone) ?: 0L }
            // A foreign account that became known in the step is listed as its opening (B-109): not a revaluation.
            val opened = accounts.filter { !it.excluded && it.currency != Currencies.RUB && CapitalReconstruction.knownFrom(it, transactions, zone).inStep(prev.date, cur.date) }
                .sumOf { a -> CapitalReconstruction.rateAt(rates, a.currency, CapitalReconstruction.knownFrom(a, transactions, zone))
                    ?.let { FxMath.rubOf(CapitalReconstruction.openingOf(a, transactions), it) } ?: 0L }
            val revalued = cur.valueOf(CapitalPart.CURRENCY) - prev.valueOf(CapitalPart.CURRENCY) - dealt - booked - opened
            if (revalued != 0L) {
                val reason = reasonOf(prev.date, cur.date, revalued, fxOps, rates)
                val parts = reason.legs.map { l ->
                    HistoryEvent(cur.date, CapitalPart.CURRENCY, Currencies.nameOf(l.currency), RevaluationText.partSubtitle(l), l.effectMinor,
                        kind = HistoryKind.REVALUATION, target = HistoryTarget.Currency(l.currency))
                } + (if (reason.manualMinor != 0L) listOf(manualEvent(cur.date, reason, accounts, zone)) else emptyList())
                // Nothing but a hand change: the line itself opens the sheet of the balance (F-55).
                val onlyManual = reason.legs.isEmpty() && reason.manualMinor != 0L
                out += HistoryEvent(cur.date, CapitalPart.CURRENCY, "Переоценка валюты", RevaluationText.subtitle(reason), revalued, reason,
                    kind = HistoryKind.REVALUATION, parts = if (parts.size >= 2) parts else emptyList(),
                    ref = if (onlyManual) ManualBalance.currencyOf(reason.legs, accounts)?.let { HistoryRef.Balance(it) } else null,
                    target = reason.legs.maxByOrNull { kotlin.math.abs(it.effectMinor) }?.let { HistoryTarget.Currency(it.currency) }
                        ?: HistoryTarget.Section(CapitalPart.CURRENCY)
                )
            }
        }
        return out
    }

    /** «Остаток изменили вручную» of a day; opens the accounts of its currency, with the bank in the subtitle when it is one (F-55). */
    private fun manualEvent(date: LocalDate, reason: RevaluationReason, accounts: List<Account>, zone: ZoneId): HistoryEvent {
        val currency = ManualBalance.currencyOf(reason.legs, accounts)
        val bank = currency?.let { ManualBalance.onlyChanged(ManualBalance.accountsOf(it, accounts), date, zone) }
        val name = currency?.let { Currencies.nameOf(it) }
        return HistoryEvent(date, CapitalPart.CURRENCY, "Остаток изменили вручную", listOfNotNull(name, bank?.brand?.title).joinToString(" · ").ifEmpty { null },
            reason.manualMinor, kind = HistoryKind.REVALUATION, ref = currency?.let { HistoryRef.Balance(it) })
    }

    /**
     * [total] interest by [weights] (a deposit's growth that day), floor per deposit; the
     * rest goes to the largest deposit, so the parts always add up to [total]. No weight at all —
     * everything to the largest. Parts of nothing are dropped.
     */
    internal fun splitInterest(total: Long, weights: List<Pair<Deposit, Long>>, on: LocalDate): List<Pair<Deposit, Long>> {
        if (weights.isEmpty()) return emptyList()
        val largest = weights.map { it.first }.sortedWith(compareByDescending<Deposit> { DepositMath.valueOn(it, on) }.thenBy { it.id }).first()
        val sum = weights.sumOf { it.second }
        val shares = weights.associate { (d, w) ->
            d.id to if (sum <= 0L) 0L
            else java.math.BigDecimal(total).multiply(java.math.BigDecimal(w)).divide(java.math.BigDecimal(sum), 0, java.math.RoundingMode.FLOOR).toLong()
        }
        val rest = total - shares.values.sum()
        return weights.map { it.first }.map { d -> d to shares.getValue(d.id) + if (d.id == largest.id) rest else 0L }.filter { it.second != 0L }
    }

    /**
     * Rates explain what they can: the balance held on [from] times the rate change, and what the day's own deals
     * gained against their price (a 100 $ bought at 80, valued at the CBR 80,74); the rest is a manual change.
     * Like the history itself, a day before the first known rate takes that first rate.
     */
    private fun reasonOf(from: LocalDate, to: LocalDate, revalued: Long, fxOps: List<FxOperation>, rates: List<FxRate>): RevaluationReason {
        fun rateOn(currency: String, day: LocalDate) = rateOn(rates, currency, day)
        val legs = fxOps.map { it.currency }.distinct().mapNotNull { cur ->
            val r1 = rateOn(cur, to) ?: return@mapNotNull null
            val qty0 = fxOps.filter { it.currency == cur && !it.date.isAfter(from) }
                .sumOf { if (it.kind == FxKind.BUY) it.amountMinor else -it.amountMinor }
            val r0 = rateOn(cur, from) ?: r1
            var effect = if (qty0 != 0L) FxMath.rubOf(qty0, r1) - FxMath.rubOf(qty0, r0) else 0L
            var qty = qty0
            var dealPrice: Long? = null
            fxOps.filter { it.currency == cur && it.date.inStep(from, to) }.forEach { o ->
                val gain = FxMath.rubOf(o.amountMinor, r1) - o.rubMinor
                effect += if (o.kind == FxKind.BUY) gain else -gain
                qty += if (o.kind == FxKind.BUY) o.amountMinor else -o.amountMinor
                if (dealPrice == null && o.amountMinor != 0L) {
                    dealPrice = (java.math.BigInteger.valueOf(o.rubMinor) * java.math.BigInteger.valueOf(1_000_000L) / java.math.BigInteger.valueOf(o.amountMinor)).toLong()
                }
            }
            if (effect == 0L) return@mapNotNull null
            // One rate «was» per currency: the held rate, the deal's price, or — both at once — the average the effect implies.
            val shownFrom = when {
                dealPrice == null -> r0
                qty0 == 0L -> dealPrice!!
                qty > 0L -> (java.math.BigInteger.valueOf(FxMath.rubOf(qty, r1) - effect) * java.math.BigInteger.valueOf(1_000_000L) /
                    java.math.BigInteger.valueOf(qty)).toLong()
                else -> r0
            }
            RevaluationLeg(cur, shownFrom, r1, qty, effect)
        }
        val rest = revalued - legs.sumOf { it.effectMinor }
        val manual = if (kotlin.math.abs(rest) < maxOf(100L, kotlin.math.abs(revalued) / 100)) 0L else rest
        return RevaluationReason(legs, manual)
    }

    /** The day with events closest to [date]; of two equally close the earlier. Null when there are no events (F-41). */
    fun nearestDay(events: List<HistoryEvent>, date: LocalDate): LocalDate? =
        events.map { it.date }.distinct()
            .minWithOrNull(compareBy<LocalDate>({ kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(it, date)) }, { it }))

    /** The newest day with events inside [span]; none — the nearest day with events to its end (F-46). */
    fun focusDay(events: List<HistoryEvent>, span: DaySpan): LocalDate? =
        events.map { it.date }.filter { it in span }.maxOrNull() ?: nearestDay(events, span.to)

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

    /** Whether the date falls into the step after [from] up to and including [to]: points are not on every day. */
    private fun LocalDate.inStep(from: LocalDate, to: LocalDate) = isAfter(from) && !isAfter(to)

    /** The CBR rate of [day]; a day before the first known rate takes that first one. */
    private fun rateOn(rates: List<FxRate>, currency: String, day: LocalDate): Long? =
        FxRates.latestOnOrBefore(rates, currency, day)?.rubPerUnitMicro
            ?: rates.filter { it.currency == currency }.minByOrNull { it.date }?.rubPerUnitMicro

    /** Kopecks of a foreign-currency operation at the rate of its day; null — no rate. */
    private fun rubOf(t: Transaction, rates: List<FxRate>, zone: ZoneId): Long? =
        rateOn(rates, t.currency, dayOf(t.postedAt, zone))?.let { FxMath.rubOf(t.amount.minor, it) }

    private fun dayOf(epochMs: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
}
