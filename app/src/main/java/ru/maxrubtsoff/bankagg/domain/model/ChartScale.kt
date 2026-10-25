package ru.maxrubtsoff.bankagg.domain.model

import kotlin.math.abs

/** The Y range of a line chart (F-43): a wiggle of kopecks must not be drawn as a cliff. */
object ChartScale {

    /**
     * The (low, high) a series is drawn between: its own min and max, but at least
     * [minSpanBp] hundredths of a percent of its average wide (200 — 2 %), centred on
     * the middle when the series is narrower. Integers only; high is always above low.
     */
    fun range(points: List<Long>, minSpanBp: Int = 200): Pair<Long, Long> {
        if (points.isEmpty()) return 0L to 1L
        val min = points.min()
        val max = points.max()
        val ref = abs(points.sum() / points.size)
        val minSpan = maxOf(1L, ref * minSpanBp / 10_000L)
        if (max - min >= minSpan) return min to max
        val mid = min + (max - min) / 2
        return (mid - minSpan / 2) to (mid + (minSpan - minSpan / 2))
    }
}
