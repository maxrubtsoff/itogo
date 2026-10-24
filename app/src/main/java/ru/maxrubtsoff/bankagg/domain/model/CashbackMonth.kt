package ru.maxrubtsoff.bankagg.domain.model

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Month helpers for the cashback section. A month is identified by a stable
 * "YYYY-MM" key; labels are rendered in Russian ("Октябрь 2026").
 */
object CashbackMonth {

    private val RU = Locale("ru")

    fun keyOf(date: LocalDate): String = "%04d-%02d".format(date.year, date.monthValue)

    fun currentKey(today: LocalDate = LocalDate.now()): String = keyOf(today)

    fun nextKey(key: String): String = keyOf(parse(key).atDay(1).plusMonths(1))

    /** Key [offset] months away from [key] (negative = past). Drives the month pager. */
    fun keyForOffset(key: String, offset: Int): String =
        keyOf(parse(key).atDay(1).plusMonths(offset.toLong()))

    /** Human label, e.g. "Октябрь 2026". */
    fun label(key: String): String {
        val ym = parse(key)
        val month = ym.month.getDisplayName(TextStyle.FULL_STANDALONE, RU)
            .replaceFirstChar { it.titlecase(RU) }
        return "$month ${ym.year}"
    }

    /** Short label for tabs, e.g. "Октябрь". */
    fun shortLabel(key: String): String =
        parse(key).month.getDisplayName(TextStyle.FULL_STANDALONE, RU)
            .replaceFirstChar { it.titlecase(RU) }

    /** From this day of the month the next month starts being highlighted. */
    const val HINT_FROM_DAY = 25

    /**
     * Whether to nudge the user toward the next month: from the 25th onward the
     * next month is surfaced as a hint ("можно уже добавлять кэшбэк"), so they
     * can prepare it before it starts. Earlier in the month it stays quiet.
     */
    fun shouldOfferNextMonth(today: LocalDate = LocalDate.now()): Boolean =
        today.dayOfMonth >= HINT_FROM_DAY

    /**
     * The month a screenshot is about, from the months it names: the only one;
     * of several (tabs "В сентябре · В октябре"), the current month, then the next,
     * then the last named. The year is the one nearest to [today]. Null if none.
     */
    fun guessKey(months: List<Int>, today: LocalDate): String? {
        if (months.isEmpty()) return null
        val now = YearMonth.from(today)
        val m = when {
            months.distinct().size == 1 -> months.first()
            now.monthValue in months -> now.monthValue
            now.plusMonths(1).monthValue in months -> now.plusMonths(1).monthValue
            else -> months.last()
        }
        var ym = YearMonth.of(today.year, m)
        if (ym.isAfter(now.plusMonths(5))) ym = ym.minusYears(1)
        else if (ym.isBefore(now.minusMonths(6))) ym = ym.plusYears(1)
        return keyOf(ym.atDay(1))
    }
    private fun parse(key: String): YearMonth {
        val parts = key.split("-")
        return YearMonth.of(parts[0].toInt(), parts[1].toInt())
    }
}
