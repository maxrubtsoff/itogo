package ru.maxrubtsoff.bankagg.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The window the person looks at on the history chart (U-77): a [DaySpan] inside the period's [bounds],
 * moved and resized by pinch, two fingers and the little map under the chart. Pure functions: the window
 * is always inside the bounds and at least [MIN_DAYS] long (or the whole period when that is shorter).
 */
object ChartWindow {

    const val MIN_DAYS = 7

    /** A window wider than this is drawn by weeks, a narrower one by days. */
    const val WEEKLY_AFTER_DAYS = 92

    /** The whole of the series; null when it is empty. */
    fun bounds(full: List<DayValue>): DaySpan? =
        if (full.isEmpty()) null else DaySpan(full.first().date, full.last().date)

    fun isWhole(window: DaySpan, bounds: DaySpan): Boolean = window.from == bounds.from && window.to == bounds.to

    /** [window] scaled by [factor] (above 1 — closer) around [focus] (0..1 across the window): the day under it stays put. */
    fun zoom(window: DaySpan, bounds: DaySpan, factor: Double, focus: Double): DaySpan {
        if (factor <= 0.0 || factor.isNaN()) return fit(window, bounds)
        val f = focus.coerceIn(0.0, 1.0)
        val min = minOf(MIN_DAYS, bounds.days)
        val len = Math.round(window.days / factor).toInt().coerceIn(min, bounds.days)
        val anchor = window.from.plusDays(Math.round(f * (window.days - 1)))
        val from = anchor.minusDays(Math.round(f * (len - 1)))
        return fit(DaySpan(from, from.plusDays(len - 1L)), bounds)
    }

    /** [window] moved by [shiftFraction] of its own length (positive — to later days); the length stays. */
    fun pan(window: DaySpan, bounds: DaySpan, shiftFraction: Double): DaySpan =
        shift(window, bounds, Math.round(shiftFraction * window.days).toInt())

    /** [window] moved by [days]; the length stays and the edge of [bounds] stops it. */
    fun shift(window: DaySpan, bounds: DaySpan, days: Int): DaySpan =
        fit(DaySpan(window.from.plusDays(days.toLong()), window.to.plusDays(days.toLong())), bounds)

    /** An edge of the window pulled to the day at [fraction] (0..1) of [bounds]; the window keeps at least [MIN_DAYS]. */
    fun resize(window: DaySpan, bounds: DaySpan, startEdge: Boolean, fraction: Double): DaySpan {
        val min = minOf(MIN_DAYS, bounds.days)
        val day = bounds.from.plusDays(Math.round(fraction.coerceIn(0.0, 1.0) * (bounds.days - 1)))
        return if (startEdge) {
            val from = minOf(day, window.to.minusDays(min - 1L))
            fit(DaySpan(from, window.to), bounds)
        } else {
            val to = maxOf(day, window.from.plusDays(min - 1L))
            fit(DaySpan(window.from, to), bounds)
        }
    }

    /** [window] carried so that its middle is at [fraction] (0..1) of [bounds]; the length stays. */
    fun centerAt(window: DaySpan, bounds: DaySpan, fraction: Double): DaySpan {
        val middle = bounds.from.plusDays(Math.round(fraction.coerceIn(0.0, 1.0) * (bounds.days - 1)))
        val from = middle.minusDays((window.days - 1) / 2L)
        return fit(DaySpan(from, from.plusDays(window.days - 1L)), bounds)
    }

    /** The days of [full] inside [window]; null — all of them. */
    fun visible(full: List<DayValue>, window: DaySpan?): List<DayValue> =
        if (window == null) full else full.filter { it in window }

    /** What the chart draws for [visible]: by weeks when it is wide, then at most [CapitalHistory.MAX_POINTS], first and last kept. */
    fun drawn(visible: List<DayValue>): List<DayValue> =
        CapitalHistory.thin(if (visible.size > WEEKLY_AFTER_DAYS) CapitalHistory.weekly(visible) else visible)

    /** Where [window] stands across [bounds] by days (0..1, both ends); null — the whole period. */
    fun fractions(window: DaySpan?, bounds: DaySpan?): ClosedFloatingPointRange<Float>? {
        if (window == null || bounds == null || bounds.days < 2) return null
        val span = (bounds.days - 1).toFloat()
        return (daysBetween(bounds.from, window.from) / span).coerceIn(0f, 1f)..(daysBetween(bounds.from, window.to) / span).coerceIn(0f, 1f)
    }

    private operator fun DaySpan.contains(v: DayValue) = v.date in this

    /** Brings [w] inside [bounds]: the length is kept unless it does not fit, then it is the whole. */
    private fun fit(w: DaySpan, bounds: DaySpan): DaySpan {
        val len = minOf(w.days, bounds.days)
        var from: LocalDate = w.from
        var to: LocalDate = w.from.plusDays(len - 1L)
        if (from.isBefore(bounds.from)) { from = bounds.from; to = from.plusDays(len - 1L) }
        if (to.isAfter(bounds.to)) { to = bounds.to; from = to.minusDays(len - 1L) }
        return DaySpan(from, to)
    }

    /** Days from [a] to [b], for the little map's fractions. */
    fun daysBetween(a: LocalDate, b: LocalDate): Int = ChronoUnit.DAYS.between(a, b).toInt()
}
