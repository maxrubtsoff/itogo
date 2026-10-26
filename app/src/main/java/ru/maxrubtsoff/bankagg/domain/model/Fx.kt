package ru.maxrubtsoff.bankagg.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

enum class FxKind { BUY, SELL }

/**
 * One purchase or sale of cash foreign currency kept at home. [amountMinor] is
 * the currency amount in hundredths (1 000 $ → 100 000), [rubMinor] the rubles
 * paid or received in kopecks. The rate is never stored: it is derived as
 * rub / amount, so the history never drifts on rounding.
 */
data class FxOperation(
    val id: String,
    val currency: String,
    val kind: FxKind,
    val date: LocalDate,
    val amountMinor: Long,
    val rubMinor: Long,
    val createdAt: Long
) {
    /** Rubles per one unit of currency. */
    val rate: BigDecimal get() = FxMath.rateOf(amountMinor, rubMinor)
}

/** Official Bank of Russia rate: rubles per one unit, ×1 000 000. */
data class FxRate(val currency: String, val date: LocalDate, val rubPerUnitMicro: Long) {
    val rubPerUnit: BigDecimal get() = BigDecimal.valueOf(rubPerUnitMicro, 6)
}

/** Cash rubles at home: one amount and when it was last changed. */
data class CashBalance(val amount: Money, val updatedAt: Long)

/** F-48: one hand edit of the cash — when, the new amount and by how much it moved. */
data class CashChange(val atMs: Long, val amount: Money, val delta: Money)

object CashHistory {
    /** The change [next] makes over [prev]; `null` when the amount stayed the same. */
    fun change(prev: CashBalance?, next: CashBalance): CashChange? {
        val delta = next.amount - (prev?.amount ?: Money.ZERO)
        return if (delta.minor == 0L) null else CashChange(next.updatedAt, next.amount, delta)
    }
}

/** Rate arithmetic shared by the portfolio, the forms and the capital. BigDecimal, HALF_UP to the kopeck. */
object FxMath {

    /** Rubles per unit from an amount in hundredths and rubles in kopecks. */
    fun rateOf(amountMinor: Long, rubMinor: Long): BigDecimal =
        if (amountMinor == 0L) BigDecimal.ZERO
        else BigDecimal.valueOf(rubMinor).divide(BigDecimal.valueOf(amountMinor), 6, RoundingMode.HALF_UP)

    /** Kopecks for [amountMinor] (hundredths of a unit) at [rubPerUnitMicro]. */
    fun rubOf(amountMinor: Long, rubPerUnitMicro: Long): Long =
        BigDecimal.valueOf(amountMinor)
            .multiply(BigDecimal.valueOf(rubPerUnitMicro))
            .divide(BigDecimal.valueOf(1_000_000L), 0, RoundingMode.HALF_UP)
            .toLong()

    /** Kopecks for [amountMinor] at a rate given as rubles per unit. */
    fun rubOf(amountMinor: Long, rate: BigDecimal): Long =
        BigDecimal.valueOf(amountMinor).multiply(rate).setScale(0, RoundingMode.HALF_UP).toLong()

    /** Rate as ×1 000 000 micro-rubles. */
    fun micro(rate: BigDecimal): Long = rate.movePointRight(6).setScale(0, RoundingMode.HALF_UP).toLong()

    /**
     * A rate for display: two decimals when that is exact ("76,50"), otherwise
     * four ("76,4321"). Russian decimal comma, no-break group spaces.
     */
    fun formatRate(rate: BigDecimal): String {
        val two = rate.setScale(2, RoundingMode.HALF_UP)
        val shown = if (two.compareTo(rate) == 0) two else rate.setScale(4, RoundingMode.HALF_UP)
        val plain = shown.toPlainString()
        val (whole, frac) = plain.split('.').let { it[0] to it.getOrElse(1) { "" } }
        val grouped = whole.trimStart('-').reversed().chunked(3).joinToString(Money.NBSP).reversed()
        return (if (whole.startsWith("-")) "-" else "") + grouped + (if (frac.isEmpty()) "" else ",$frac")
    }

    /** "1 000 $" — an amount of currency in hundredths, with its symbol or code. */
    fun formatAmount(amountMinor: Long, currency: String): String = Money.ofMinor(amountMinor).format(currency)

    /** Parses a rate the user typed ("76,5", "76.50"); null for junk or ≤ 0. */
    fun parseRate(text: String): BigDecimal? {
        val t = text.trim().replace(',', '.').filterNot { it == ' ' || it == ' ' }
        val v = t.toBigDecimalOrNull() ?: return null
        return v.takeIf { it.signum() > 0 }
    }
}

/** Picking the rate "for today" from what is cached. */
object FxRates {

    /** A rate older than this many days is shown as stale. */
    const val STALE_AFTER_DAYS = 3L

    /** The rate with the latest date ≤ [day] (weekends use Saturday's rate). */
    fun latestOnOrBefore(rates: List<FxRate>, currency: String, day: LocalDate): FxRate? =
        rates.filter { it.currency == currency && !it.date.isAfter(day) }.maxByOrNull { it.date }

    /** Latest rate ≤ [day] for every currency present. */
    fun latestByCurrency(rates: List<FxRate>, day: LocalDate): Map<String, FxRate> =
        rates.filter { !it.date.isAfter(day) }
            .groupBy { it.currency }
            .mapValues { (_, list) -> list.maxBy { it.date } }

    fun isStale(rateDate: LocalDate, today: LocalDate): Boolean =
        rateDate.isBefore(today.minusDays(STALE_AFTER_DAYS))
}
