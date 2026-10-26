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

    private val UNITS = listOf("тыс" to java.math.BigDecimal("1000"), "млн" to java.math.BigDecimal("1000000"), "млрд" to java.math.BigDecimal("1000000000"))
    private const val TEN_THOUSAND_MINOR = 1_000_000L

    /**
     * A ruble sum for a place too narrow for the full one (F-40, B-72): "48,3 тыс ₽",
     * "1,77 млн ₽", with the sign. Under 10 000 ₽ the full sum is short enough already.
     * [signed]: "+" before a gain and a real minus "−" before a loss.
     */
    fun short(minor: Long, signed: Boolean = false): String {
        val abs = abs(minor)
        val sign = when {
            minor == 0L -> ""
            minor > 0L -> if (signed) "+" else ""
            else -> if (signed) "−" else "-"
        }
        if (abs < TEN_THOUSAND_MINOR) return sign + Money.ofMinor(abs).format()
        val rub = java.math.BigDecimal.valueOf(abs, 2)
        var tier = when {
            abs >= 100_000_000_000L -> 2
            abs >= 100_000_000L -> 1
            else -> 0
        }
        var value = scaledTo(rub, tier)
        // 999 999 ₽ rounds to 1000 тыс: that is already a million.
        if (value.compareTo(java.math.BigDecimal(1000)) >= 0 && tier < UNITS.lastIndex) {
            tier += 1
            value = scaledTo(rub, tier)
        }
        val number = value.stripTrailingZeros().toPlainString().replace('.', ',')
        return sign + number + Money.NBSP + UNITS[tier].first + Money.NBSP + Currencies.symbolOf("RUB")
    }

    private fun scaledTo(rub: java.math.BigDecimal, tier: Int): java.math.BigDecimal =
        rub.divide(UNITS[tier].second, 2, java.math.RoundingMode.HALF_UP)
}
