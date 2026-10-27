package ru.maxrubtsoff.bankagg.domain.model

import kotlin.math.abs

/** How a mark is drawn on the chart (F-49): a big dot, a small one, a ring for a closed deposit. */
enum class MarkShape { BIG, SMALL, RING }

/** A mark at point [index] in the colour of [part]; [label] is the short sum, null — the dot goes without one. */
data class ChartMark(val index: Int, val part: CapitalPart, val shape: MarkShape, val amountMinor: Long, val label: String?)

/** Which actions the chart marks and which of them get a sum (F-49). Where the label fits is the screen's matter. */
object ChartMarks {

    /** An account operation is marked when it is over this share of the average balance. */
    private const val ACCOUNTS_SHARE = 10

    private class Candidate(val index: Int, val part: CapitalPart, val shape: MarkShape, val amount: Long, val labelable: Boolean, val label: String)

    /**
     * Marks of the actions in [events] on the points of [series]; interest, revaluation and transfers between
     * one's own accounts are never marked. [accountsAvgMinor] is the average balance of the accounts over the period.
     * At most [maxLabels] marks get a label — the biggest by sum.
     */
    fun of(series: List<DayValue>, events: List<HistoryEvent>, page: CapitalPart?, accountsAvgMinor: Long, maxLabels: Int = 3): List<ChartMark> {
        val byPoint = HashMap<Int, Candidate>()
        events.forEach { e ->
            val c = candidate(series, e, page, accountsAvgMinor) ?: return@forEach
            val prev = byPoint[c.index]
            if (prev == null || better(c, prev)) byPoint[c.index] = c
        }
        val labeled = byPoint.values.filter { it.labelable }.sortedByDescending { abs(it.amount) }.take(maxLabels).toSet()
        return byPoint.values.sortedBy { it.index }.map { c ->
            ChartMark(c.index, c.part, c.shape, c.amount, if (c in labeled) c.label else null)
        }
    }

    private fun rank(s: MarkShape) = when (s) { MarkShape.BIG -> 0; MarkShape.RING -> 1; MarkShape.SMALL -> 2 }

    private fun better(a: Candidate, b: Candidate): Boolean =
        rank(a.shape) < rank(b.shape) || (rank(a.shape) == rank(b.shape) && abs(a.amount) > abs(b.amount))

    private fun candidate(series: List<DayValue>, e: HistoryEvent, page: CapitalPart?, avg: Long): Candidate? {
        val amount = e.amountMinor?.takeIf { it != 0L } ?: return null
        val shape: MarkShape = when (e.kind) {
            HistoryKind.INCOME, HistoryKind.SPEND, HistoryKind.TRANSFER, HistoryKind.TRANSACTION, HistoryKind.ACCOUNT_OPEN ->
                if (e.part == CapitalPart.ACCOUNTS && (page == null || page == CapitalPart.ACCOUNTS) && avg > 0 && abs(amount) > avg / ACCOUNTS_SHARE) MarkShape.BIG else return null
            // On the total a deposit is a small dot or a ring, never a sum: only the deposits page labels them.
            HistoryKind.DEPOSIT_OPEN -> if (page == CapitalPart.DEPOSITS) MarkShape.BIG else if (page == null) MarkShape.SMALL else return null
            HistoryKind.DEPOSIT_TOPUP -> if (page == CapitalPart.DEPOSITS || page == null) MarkShape.SMALL else return null
            HistoryKind.DEPOSIT_CLOSE -> if (page == CapitalPart.DEPOSITS || page == null) MarkShape.RING else return null
            HistoryKind.FX_BUY, HistoryKind.FX_SELL -> if (page == CapitalPart.CURRENCY) MarkShape.BIG else if (page == null) MarkShape.SMALL else return null
            HistoryKind.CASH -> when {
                page == CapitalPart.CASH -> MarkShape.BIG
                page == null && avg > 0 && abs(amount) > avg / ACCOUNTS_SHARE -> MarkShape.BIG
                else -> return null
            }
            HistoryKind.INTEREST, HistoryKind.REVALUATION, HistoryKind.OWN_TRANSFER -> return null
        }
        val index = CapitalHistory.pointOf(series, e.date) ?: return null
        val label = e.foreign?.let { (if (it.minor > 0L) "+" else "−") + Money.ofMinor(abs(it.minor)).format(it.currency) }
            ?: MoneyCompact.short(amount, signed = true)
        return Candidate(index, e.part, shape, amount, shape == MarkShape.BIG || (shape == MarkShape.RING && page == CapitalPart.DEPOSITS), label)
    }

    /** The mark within [tolerance] points of [raw] (the earlier one on a tie), else [raw] itself: a touch near a dot picks it. */
    fun snap(raw: Int, marks: List<Int>, tolerance: Int): Int =
        marks.filter { abs(it - raw) <= tolerance }.minWithOrNull(compareBy<Int>({ abs(it - raw) }, { it })) ?: raw
}
