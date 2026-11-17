package ru.maxrubtsoff.bankagg.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * What the rates did to foreign-currency accounts over one history step (B-132): the balance held on the
 * step's start times the rate change, plus the step's own operations valued at [to] instead of their day.
 * Such a change is a revaluation, not a hand change.
 */
internal object AccountFxEffect {

    /** One leg per account currency with a non-zero effect, in kopecks. */
    fun legsOf(
        accounts: List<Account>, transactions: List<Transaction>, rates: List<FxRate>,
        from: LocalDate, to: LocalDate, zone: ZoneId
    ): List<RevaluationLeg> {
        fun rateOn(currency: String, day: LocalDate): Long? =
            FxRates.latestOnOrBefore(rates, currency, day)?.rubPerUnitMicro
                ?: rates.filter { it.currency == currency }.minByOrNull { it.date }?.rubPerUnitMicro
        fun dayOf(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val after = from.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val kept = accounts.filter { a ->
            if (a.excluded || a.currency == Currencies.RUB) return@filter false
            val known = CapitalReconstruction.knownFrom(a, transactions, zone)
            if (known.isAfter(from)) return@filter false
            // A balance named exactly inside the step may be a real hand change: left to «вручную».
            val checked = a.checkedAt?.let { dayOf(it) }
            !(checked != null && checked.isAfter(from) && !checked.isAfter(to))
        }
        return kept.groupBy { it.currency }.mapNotNull { (cur, accs) ->
            val r1 = rateOn(cur, to) ?: return@mapNotNull null
            val r0 = rateOn(cur, from) ?: r1
            var effect = 0L
            var qty = 0L
            accs.forEach { a ->
                val ops = transactions.filter { it.accountId == a.id }
                val qty0 = a.balance.minor - ops.filter { it.postedAt >= after }.sumOf { it.amount.minor }
                effect += FxMath.rubOf(qty0, r1) - FxMath.rubOf(qty0, r0)
                qty += qty0
                ops.filter { dayOf(it.postedAt).let { d -> d.isAfter(from) && !d.isAfter(to) } }.forEach { t ->
                    val rt = rateOn(cur, dayOf(t.postedAt)) ?: r1
                    effect += FxMath.rubOf(t.amount.minor, r1) - FxMath.rubOf(t.amount.minor, rt)
                    qty += t.amount.minor
                }
            }
            if (effect == 0L) null else RevaluationLeg(cur, r0, r1, qty, effect)
        }
    }
}
