package ru.maxrubtsoff.bankagg.domain.model

import java.time.LocalDate
import java.time.YearMonth

/** One day of capital (F-36): the total of the included parts and each part on its own, in kopecks. */
data class CapitalPoint(val date: LocalDate, val totalMinor: Long, val parts: Map<CapitalPart, Long>) {
    /** The total, or one part when [part] is given. */
    fun valueOf(part: CapitalPart?): Long = if (part == null) totalMinor else parts[part] ?: 0L
}

/** How far back the history screen looks. [days] null — from the very first day. */
enum class HistoryPeriod(val days: Int?, val key: String) {
    WEEK(7, "week"), MONTH(30, "month"), YEAR(365, "year"), ALL(null, "all");

    companion object {
        fun fromKey(key: String?): HistoryPeriod? = entries.firstOrNull { it.key == key }
    }
}

/** A value on a day; the series the chart draws is a list of these, one per day. */
data class DayValue(val date: LocalDate, val minor: Long)

/** The days one chart point stands for, both ends included (F-46). */
data class DaySpan(val from: LocalDate, val to: LocalDate) {
    val days: Int get() = (java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1).toInt()
    operator fun contains(d: LocalDate): Boolean = !d.isBefore(from) && !d.isAfter(to)
}

/** What changed over a period:[percentBp] is hundredths of a percent (1,5 % → 150), null when it started from nothing. */
data class CapitalChange(val from: DayValue, val to: DayValue, val deltaMinor: Long, val percentBp: Long?)

/** Pure logic of the capital history: the series of a period, its change, thinning, month deltas. */
object CapitalHistory {

    /** Most points the chart gets; longer series are thinned (keeps first and last). */
    const val MAX_POINTS = 120

    /** Pages of the history: the total (null) first, then each kept part (F-45). */
    fun sections(kept: List<CapitalPart>): List<CapitalPart?> = listOf<CapitalPart?>(null) + kept

    /** The page of [part]; a part that is not there leads to the total. */
    fun pageOf(part: CapitalPart?, sections: List<CapitalPart?>): Int = sections.indexOf(part).coerceAtLeast(0)

    /** The part a page shows; null — the total. */
    fun partOf(page: Int, sections: List<CapitalPart?>): CapitalPart? = sections.getOrNull(page)

    /** The first day shown for [period]. */
    fun startOf(period: HistoryPeriod, points: List<CapitalPoint>, today: LocalDate): LocalDate? {
        val first = points.minOfOrNull { it.date } ?: return null
        val days = period.days ?: return first
        val wanted = today.minusDays(days.toLong())
        return if (wanted.isBefore(first)) first else wanted
    }

    /**
     * One value per day from the start of [period] to [today]; a day with no
     * saved point keeps the last known value (a gap is not a fall to zero).
     * [part] null — the total. Empty when there is nothing yet.
     */
    fun series(points: List<CapitalPoint>, period: HistoryPeriod, today: LocalDate, part: CapitalPart? = null): List<DayValue> {
        val sorted = points.filter { !it.date.isAfter(today) }.sortedBy { it.date }
        if (sorted.isEmpty()) return emptyList()
        val start = startOf(period, sorted, today) ?: return emptyList()
        // The value on the start day is the last known one on or before it.
        var carried = sorted.lastOrNull { !it.date.isAfter(start) }?.valueOf(part) ?: sorted.first().valueOf(part)
        val byDay = sorted.associateBy { it.date }
        val out = ArrayList<DayValue>()
        var day = start
        while (!day.isAfter(today)) {
            byDay[day]?.let { carried = it.valueOf(part) }
            out += DayValue(day, carried)
            day = day.plusDays(1)
        }
        return out
    }

    /** At most [max] points, evenly spread, first and last always kept. */
    fun thin(series: List<DayValue>, max: Int = MAX_POINTS): List<DayValue> {
        if (series.size <= max || max < 2) return series
        val last = series.size - 1
        return (0 until max).map { i -> series[(i.toLong() * last / (max - 1)).toInt()] }
    }

    /** The last point, then every 7 days back, then the first: a year and all-time are drawn by weeks (F-46). */
    fun weekly(series: List<DayValue>): List<DayValue> {
        if (series.size <= 2) return series
        val out = ArrayList<DayValue>()
        var i = series.size - 1
        while (i > 0) { out += series[i]; i -= 7 }
        out += series[0]
        return out.reversed()
    }

    /** The days a point stands for: from the day after the point before it to its own day. */
    fun spanAt(series: List<DayValue>, i: Int): DaySpan =
        DaySpan(if (i <= 0) series[0].date else series[i - 1].date.plusDays(1), series[i].date)

    /** What the point added to the one before it; null for the first. */
    fun stepAt(series: List<DayValue>, i: Int): Long? = if (i <= 0) null else series[i].minor - series[i - 1].minor

    /** The point whose span holds [date]; null before the first point and after the last. */
    fun pointOf(series: List<DayValue>, date: LocalDate): Int? {
        if (series.isEmpty() || date.isBefore(series[0].date)) return null
        return series.indices.firstOrNull { !series[it].date.isBefore(date) }
    }

    /** The mean of the series, whole kopecks; 0 when empty. */
    fun average(series: List<DayValue>): Long = if (series.isEmpty()) 0L else series.sumOf { it.minor } / series.size

    /** First to last of [series]; null when it is empty. */
    fun change(series: List<DayValue>): CapitalChange? {
        val from = series.firstOrNull() ?: return null
        val to = series.last()
        val delta = to.minor - from.minor
        val pct = if (from.minor > 0L) Math.round(delta * 10_000.0 / from.minor) else null
        return CapitalChange(from, to, delta, pct)
    }

    /** The value at the end of [month] (the last known one by then) against the end of the month before. */
    fun monthDelta(points: List<CapitalPoint>, month: YearMonth, part: CapitalPart? = null): Long {
        val sorted = points.sortedBy { it.date }
        val end = sorted.lastOrNull { !it.date.isAfter(month.atEndOfMonth()) } ?: return 0L
        val before = sorted.lastOrNull { it.date.isBefore(month.atDay(1)) } ?: sorted.firstOrNull { !it.date.isBefore(month.atDay(1)) } ?: return 0L
        return end.valueOf(part) - before.valueOf(part)
    }

    /** The month at a glance for the line under a sum: its change and a short run of points for the little line. */
    data class Glance(val change: CapitalChange, val spark: List<Long>)

    /** Null while there is less than two days of history — the line stays away. */
    fun glance(points: List<CapitalPoint>, today: LocalDate, part: CapitalPart? = null): Glance? {
        val s = series(points, HistoryPeriod.MONTH, today, part)
        if (!drawable(s)) return null
        val change = change(s) ?: return null
        return Glance(change, thin(s, SPARK_POINTS).map { it.minor })
    }

    private const val SPARK_POINTS = 16

    /** True when a chart has something to draw: at least two days. */
    fun drawable(series: List<DayValue>): Boolean = series.size >= 2
}
