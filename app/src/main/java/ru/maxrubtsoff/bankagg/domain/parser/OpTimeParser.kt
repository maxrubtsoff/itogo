package ru.maxrubtsoff.bankagg.domain.parser

import ru.maxrubtsoff.bankagg.domain.model.OpTime

/**
 * Finds the time of the operation in a push text (B-123): "3 ноября 17:39",
 * "03.11 17:39", "03.11.2026 в 17:39" or a lone "17:39". A date without a time
 * (the payment order's "от 27.10.26") is not an operation time. Several
 * different times — null.
 */
object OpTimeParser {
    private val MONTHS = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря"
    )

    private const val TIME = """(\d{1,2}):(\d{2})(?![\d:])"""
    private val TIME_RE = Regex("""(?<![\d:.,])$TIME""")
    private val WORD_DATE = Regex(
        """(?<![\d.])(\d{1,2})[\s ]+(${MONTHS.joinToString("|")})(?:[\s ]+(\d{4}))?(?:\s*г\.?)?[,\s ]*(?:в[\s ]+)?$TIME""",
        RegexOption.IGNORE_CASE
    )
    private val NUM_DATE = Regex(
        """(?<![\d.])(\d{1,2})\.(\d{2})(?:\.(\d{4}|\d{2}))?(?![\d.])[,\s ]*(?:в[\s ]+)?$TIME""",
        RegexOption.IGNORE_CASE
    )

    private val BOUND_WORD = Regex("""(?<![а-яё])(?:на|до|с)[\s ]+$""", RegexOption.IGNORE_CASE)

    fun find(text: String): OpTime? {
        val times = TIME_RE.findAll(text)
            .map { (it.groupValues[1].toInt()) to it.groupValues[2].toInt() }
            .filter { it.first in 0..23 && it.second in 0..59 }
            .toSet()
        if (times.size != 1) return null
        val (h, m) = times.first()

        WORD_DATE.find(text)?.let {
            val day = it.groupValues[1].toInt()
            val month = MONTHS.indexOf(it.groupValues[2].lowercase()) + 1
            if (day in 1..31 && it.groupValues[4].toInt() == h) {
                return OpTime(day, month, it.groupValues[3].toIntOrNull(), h, m)
            }
        }
        NUM_DATE.find(text)?.let {
            val day = it.groupValues[1].toInt()
            val month = it.groupValues[2].toInt()
            if (day in 1..31 && month in 1..12 && it.groupValues[4].toInt() == h) {
                val y = it.groupValues[3].let { s -> if (s.length == 2) 2000 + s.toInt() else s.toIntOrNull() }
                return OpTime(day, month, y, h, m)
            }
        }
        // A lone time after "на/до/с" is a deadline or a balance moment, not the operation's.
        val at = TIME_RE.find(text)!!.range.first
        if (BOUND_WORD.containsMatchIn(text.substring(0, at))) return null
        return OpTime(null, null, null, h, m)
    }
}
