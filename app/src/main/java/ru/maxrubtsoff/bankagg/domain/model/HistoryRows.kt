package ru.maxrubtsoff.bankagg.domain.model

/** What a history line does on tap (F-50): leads to an object, opens into parts, or is just a sum. */
enum class RowKind { GO, OPEN, SUM }

/** Rows of the history list: what each line is and how a day's lines are grouped. */
object HistoryRows {

    fun kindOf(e: HistoryEvent): RowKind = when {
        e.parts.isNotEmpty() -> RowKind.OPEN
        e.target != null -> RowKind.GO
        else -> RowKind.SUM
    }

    /** Ruble spendings and transfers are only a sum; a group with a currency child opens. */
    fun groupKind(eventKind: HistoryKind, children: List<HistoryEvent>): RowKind =
        if (eventKind in SUMMED && children.all { it.target == null }) RowKind.SUM else RowKind.OPEN

    private val SUMMED = setOf(HistoryKind.SPEND, HistoryKind.TRANSFER, HistoryKind.TRANSACTION)

    /** A day's spendings, and a day's transfers, fold into one row each when there are two or more (F-39). */
    fun days(events: List<HistoryEvent>): List<HistoryRow> {
        fun foldable(e: HistoryEvent) = e.kind == HistoryKind.SPEND || e.kind == HistoryKind.TRANSFER
        val buckets = events.filter(::foldable).groupBy { it.date to it.kind }
        val seen = HashSet<Pair<java.time.LocalDate, HistoryKind>>()
        val out = ArrayList<HistoryRow>()
        for (e in events) {
            val list = if (foldable(e)) buckets.getValue(e.date to e.kind) else null
            when {
                list == null || list.size < 2 -> out += HistoryRow.Single(e)
                seen.add(e.date to e.kind) ->
                    out += HistoryRow.Group("${e.date.toEpochDay()}:${e.kind.name}", e.date, e.kind, list.size, list.sumOf { it.amountMinor ?: 0L }, list)
            }
        }
        return out
    }
}
