package ru.maxrubtsoff.bankagg.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToLong

/** How often a subscription is paid (F-27). [DAYS] — every [Period.days] days. */
enum class PeriodUnit { WEEK, MONTH, YEAR, DAYS }

data class Period(val unit: PeriodUnit, val days: Int = 0) {

    fun next(from: LocalDate): LocalDate = when (unit) {
        PeriodUnit.WEEK -> from.plusWeeks(1)
        PeriodUnit.MONTH -> from.plusMonths(1)
        PeriodUnit.YEAR -> from.plusYears(1)
        PeriodUnit.DAYS -> from.plusDays(days.coerceAtLeast(1).toLong())
    }

    /** What a payment of [priceMinor] costs per month on average. */
    fun perMonth(priceMinor: Long): Long = when (unit) {
        PeriodUnit.WEEK -> (priceMinor * 52.0 / 12).roundToLong()
        PeriodUnit.MONTH -> priceMinor
        PeriodUnit.YEAR -> (priceMinor / 12.0).roundToLong()
        PeriodUnit.DAYS -> (priceMinor * 30.0 / days.coerceAtLeast(1)).roundToLong()
    }

    /** «раз в месяц», «раз в год», «каждые 10 дней». */
    fun label(): String = when (unit) {
        PeriodUnit.WEEK -> "раз в неделю"
        PeriodUnit.MONTH -> "раз в месяц"
        PeriodUnit.YEAR -> "раз в год"
        PeriodUnit.DAYS -> "каждые ${RuPlural.days(days)}"
    }

    companion object {
        val MONTHLY = Period(PeriodUnit.MONTH)
    }
}

enum class SubscriptionStatus { ACTIVE, ARCHIVED, IGNORED }

/**
 * A subscription (F-27): found in the bank pushes or added by hand. [payee] —
 * the counterparty as the bank writes it, to tie new payments to it; null for
 * one added by hand without a match. [brand] — the bank it is charged from, a
 * second note next to its own look ([decor], F-33). [previousPriceMinor] — the price before
 * the last change, for «подорожала на …». An [SubscriptionStatus.IGNORED] row
 * only remembers «Не подписка» for its payee.
 */
data class Subscription(
    val id: String,
    val title: String,
    val priceMinor: Long,
    val period: Period,
    val nextDate: LocalDate,
    val brand: BankBrand? = null,
    val payee: String? = null,
    val remind: Boolean = false,
    val status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
    val previousPriceMinor: Long? = null,
    val createdAt: Long = 0L,
    /** The user's own look (F-33); null — never touched, [look] gives the default. */
    val decor: BrandDecor? = null
) {
    /** What to draw: the user's look, or the one the name gives (always valid). */
    val look: BrandDecor get() = decor ?: BrandDecor.defaultFor(title)

    /** Up by how much since the last change, or null. */
    val raisedBy: Long? get() = previousPriceMinor?.let { priceMinor - it }?.takeIf { it > 0 }
}

/** One payment seen in the pushes. */
data class Payment(val payee: String, val amountMinor: Long, val date: LocalDate, val brand: BankBrand)

/** «Похоже на подписку»: a run of similar payments to one payee (F-27). */
data class Finding(val payee: String, val priceMinor: Long, val period: Period, val payments: List<Payment>, val nextDate: LocalDate) {
    /** A readable name from the payee: «OKKO.TV» → «Okko.tv». */
    val title: String get() = Subscriptions.titleFrom(payee)
}

/** Finding subscriptions in payments and keeping them up to date (F-27). */
object Subscriptions {

    /** Neighbouring payments within this share count as one price (a raise like 299 → 399 still is). */
    private const val PRICE_TOLERANCE = 0.35
    /** A new payment of a known subscription may differ more: its payee already says what it is. */
    private const val MATCH_TOLERANCE = 0.5
    private const val MIN_PAYMENTS = 2

    fun key(payee: String): String = payee.uppercase().replace(Regex("""\s+"""), " ").trim()

    fun titleFrom(payee: String): String =
        payee.trim().lowercase().split(' ').filter { it.isNotEmpty() }
            .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    /**
     * Runs of payments to one payee at a steady interval — a week, a month or a
     * year — and a steady price. Payees already tied to a subscription or marked
     * «Не подписка» are skipped. A shop paid several times a month isn't one: its
     * intervals don't hold.
     */
    fun find(payments: List<Payment>, known: Set<String>, today: LocalDate): List<Finding> =
        payments.filter { it.amountMinor > 0 }
            .groupBy { key(it.payee) }
            .filterKeys { it !in known }
            .mapNotNull { (_, list) -> finding(list.sortedBy { it.date }, today) }
            .sortedByDescending { it.payments.last().date }

    private fun finding(sorted: List<Payment>, today: LocalDate): Finding? {
        if (sorted.size < MIN_PAYMENTS) return null
        val last = sorted.last()
        if (sorted.zipWithNext().any { (a, b) -> !samePrice(a.amountMinor, b.amountMinor) }) return null
        val gaps = sorted.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.date, b.date) }
        val period = periodOf(gaps) ?: return null
        // A run that stopped long ago isn't a subscription any more.
        if (ChronoUnit.DAYS.between(last.date, today) > maxGap(period) * 2) return null
        return Finding(last.payee, last.amountMinor, period, sorted, period.next(last.date))
    }

    private fun periodOf(gaps: List<Long>): Period? = when {
        gaps.all { it in 6..8 } -> Period(PeriodUnit.WEEK)
        gaps.all { it in 26..35 } -> Period(PeriodUnit.MONTH)
        gaps.all { it in 350..380 } -> Period(PeriodUnit.YEAR)
        else -> null
    }

    private fun maxGap(p: Period): Long = when (p.unit) {
        PeriodUnit.WEEK -> 8
        PeriodUnit.MONTH -> 35
        PeriodUnit.YEAR -> 380
        PeriodUnit.DAYS -> p.days.toLong() + 3
    }

    private fun samePrice(a: Long, b: Long, tolerance: Double = PRICE_TOLERANCE): Boolean = abs(a - b) <= maxOf(a, b) * tolerance

    /** Whether a new payment belongs to [s]: its payee and about its price. */
    fun matches(s: Subscription, p: Payment): Boolean =
        s.status == SubscriptionStatus.ACTIVE && s.payee != null && key(s.payee) == key(p.payee) && p.amountMinor > 0 &&
            samePrice(p.amountMinor, s.priceMinor, MATCH_TOLERANCE)

    /**
     * A payment of [s] went through: the next date moves on from it and a new
     * price is remembered with the old one, for «подорожала на …».
     */
    fun paid(s: Subscription, p: Payment): Subscription {
        val next = s.period.next(p.date).let { if (it.isAfter(s.nextDate) || p.date >= s.nextDate.minusDays(3)) it else s.nextDate }
        return if (p.amountMinor != s.priceMinor) s.copy(priceMinor = p.amountMinor, previousPriceMinor = s.priceMinor, nextDate = next)
        else s.copy(nextDate = next)
    }

    /**
     * Brings [s] up to the payments seen so far: each one due (from three days
     * before the expected date) moves it on. Payments it already counted are
     * before that window, so running it again changes nothing.
     */
    fun sync(s: Subscription, payments: List<Payment>): Subscription =
        payments.filter { matches(s, it) }.sortedBy { it.date }.fold(s) { cur, p ->
            if (p.date >= cur.nextDate.minusDays(3)) paid(cur, p) else cur
        }

    /**
     * [sync] of a record read just now: only the date and price move, everything else
     * stays as stored (a flag changed after the snapshot isn't undone). Null: nothing to do (B-99).
     */
    fun syncFresh(fresh: Subscription, payments: List<Payment>): Subscription? =
        sync(fresh, payments).takeIf { it != fresh }

    /** The payments of [s] seen in the pushes, newest first (its card's history). */
    fun history(s: Subscription, payments: List<Payment>): List<Payment> =
        s.payee?.let { payee -> payments.filter { key(it.payee) == key(payee) && it.amountMinor > 0 }.sortedByDescending { it.date } }.orEmpty()

    /** The payee keys the finder must leave alone: tied to a subscription or marked «Не подписка». */
    fun knownPayees(list: List<Subscription>): Set<String> = list.mapNotNull { it.payee?.let(::key) }.toSet()

    /** A new subscription from a finding, the fields filled in. */
    fun fromFinding(f: Finding, id: String, now: Long): Subscription =
        Subscription(id, f.title, f.priceMinor, f.period, f.nextDate, brand = f.payments.last().brand, payee = f.payee, createdAt = now,
            decor = BrandDecor.defaultFor(f.title))

    /** The next payment day not before [today] (a missed push doesn't leave a date in the past). */
    fun upcoming(s: Subscription, today: LocalDate): LocalDate {
        var d = s.nextDate
        var guard = 0
        while (d.isBefore(today) && guard++ < 1000) d = s.period.next(d)
        return d
    }

    /** All active subscriptions per month. */
    fun perMonth(list: List<Subscription>): Long =
        list.filter { it.status == SubscriptionStatus.ACTIVE }.sumOf { it.period.perMonth(it.priceMinor) }

    /** «завтра», «через 3 дня», «28 ноября». */
    fun whenText(date: LocalDate, today: LocalDate): String {
        val days = ChronoUnit.DAYS.between(today, date)
        return when {
            days == 0L -> "сегодня"
            days == 1L -> "завтра"
            days in 2..6 -> "через ${RuPlural.days(days.toInt())}"
            else -> RuDays.relative(date, today)
        }
    }
}
