package ru.maxrubtsoff.bankagg.domain.model

import kotlin.math.abs

/**
 * Short money for tight spots (the deposits header): "2,80 млн" from a million,
 * "1,20 млрд" from a billion, otherwise whole rubles "248 764". No currency sign —
 * the caption above the figure says what it is. Integer math, half up.
 */
object MoneyCompact {

    private const val MILLION = 1_000_000L
    private const val BILLION = 1_000_000_000L

    fun format(m: Money, signed: Boolean = false): String {
        val rub = abs(m.minor) / 100 + if (abs(m.minor) % 100 >= 50) 1 else 0
        val body = when {
            rub >= BILLION -> scaled(rub, BILLION) + Money.NBSP + "млрд"
            rub >= MILLION -> scaled(rub, MILLION) + Money.NBSP + "млн"
            else -> Money.ofMinor(rub * 100).formatNumber()
        }
        val sign = when {
            m.minor < 0 -> "-"
            signed && m.minor > 0 -> "+"
            else -> ""
        }
        return sign + body
    }

    /** [rub] / [unit] with two decimals: 2 795 000 / 1e6 → "2,80". */
    private fun scaled(rub: Long, unit: Long): String {
        val hundredths = (rub * 100 + unit / 2) / unit
        return "${hundredths / 100},%02d".format(hundredths % 100)
    }
}
