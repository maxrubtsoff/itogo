package ru.maxrubtsoff.bankagg.domain.model

import java.time.LocalDate

/**
 * Taking a hand change of the currency out of the history (B-121). The change is a step in the saved days: the days
 * before it sit [HistoryRef.Manual.manualMinor] higher or lower than the days after. Deleting it moves those past
 * days by the step; today's point, the days from the change on, the accounts and the deals stay as they are.
 */
object ManualUndo {

    /** The saved days the step covers: a plateau up to its compensation, else back to its start (an opposite hand change, else the first day). */
    fun range(points: List<CapitalPoint>, ref: HistoryRef.Manual): ClosedRange<LocalDate>? {
        val to = ref.stepTo
        val inside = points.map { it.date }.filter {
            if (to != null) !it.isBefore(ref.date) && it.isBefore(to)
            else it.isBefore(ref.date) && (ref.stepFrom == null || !it.isBefore(ref.stepFrom))
        }
        if (inside.isEmpty()) return null
        return inside.min()..inside.max()
    }

    /** The days of the [range] with the part and the total moved by [byMinor]; only the changed days. */
    fun shift(points: List<CapitalPoint>, ref: HistoryRef.Manual, byMinor: Long): List<CapitalPoint> {
        val span = range(points, ref) ?: return emptyList()
        if (byMinor == 0L) return emptyList()
        return points.filter { it.date in span }.map {
            CapitalPoint(it.date, it.totalMinor + byMinor, it.parts + (ref.part to (it.parts[ref.part] ?: 0L) + byMinor))
        }
    }

    /** Deleting the change: the past (or, when made good later, the plateau) comes to the level without it. */
    fun deleteBy(ref: HistoryRef.Manual): Long = if (ref.stepTo != null) -ref.manualMinor else ref.manualMinor

    /** Changing the step to [newMinor]: the difference to move the covered days by. */
    fun editBy(ref: HistoryRef.Manual, newMinor: Long): Long =
        if (ref.stepTo != null) newMinor - ref.manualMinor else ref.manualMinor - newMinor
}
