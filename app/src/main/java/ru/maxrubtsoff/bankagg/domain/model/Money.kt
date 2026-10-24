package ru.maxrubtsoff.bankagg.domain.model

import kotlin.math.abs

/**
 * Value object for a monetary amount. Stored in minor units (копейки) to
 * avoid floating-point drift; [currency] is an ISO-4217 code.
 */
@JvmInline
value class Money private constructor(val minor: Long) {

    operator fun plus(other: Money) = Money(minor + other.minor)
    operator fun minus(other: Money) = Money(minor - other.minor)
    operator fun unaryMinus() = Money(-minor)

    val isNegative: Boolean get() = minor < 0

    fun toMajor(): Double = minor / 100.0

    /**
     * Digits only, Russian style: "1 800 000" / "1 234,50" / "-15". Groups are
     * split by a no-break space (U+00A0), so a sum never wraps mid-number and
     * the output does not depend on the device locale.
     */
    fun formatNumber(): String {
        val a = abs(minor)
        val grouped = (a / 100).toString().reversed().chunked(3).joinToString(NBSP).reversed()
        val cents = a % 100
        val frac = if (cents == 0L) "" else ",%02d".format(cents)
        return (if (minor < 0) "-" else "") + grouped + frac
    }

    /** "1 800 000 ₽". The space before the symbol is a no-break space too. */
    fun format(currency: String = "RUB"): String =
        formatNumber() + NBSP + Currencies.symbolOf(currency)

    companion object {
        /** No-break space: keeps "1 000 ₽" on one line. */
        const val NBSP = " "

        fun ofMinor(minor: Long) = Money(minor)
        fun ofMajor(major: Double) = Money(Math.round(major * 100))
        val ZERO = Money(0)
    }
}
