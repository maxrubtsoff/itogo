package ru.maxrubtsoff.bankagg.domain.model

/** What the list of the «Счета» page is narrowed to from the spending card (F-58, F-62). */
sealed interface SpendFilter {
    /** No narrowing. */
    object All : SpendFilter
    /** The operations of one bank. */
    data class Bank(val brand: BankBrand) : SpendFilter
    /** The transfers to other people. */
    object Transfers : SpendFilter

    companion object {
        /** The [events] that pass [f]; a group stays whole when any of its parts passes. */
        fun keep(events: List<HistoryEvent>, f: SpendFilter): List<HistoryEvent> = when (f) {
            All -> events
            is Bank -> events.filter { e -> e.source == f.brand || e.parts.any { it.source == f.brand } }
            Transfers -> events.filter { e -> isOutTransfer(e) || e.parts.any(::isOutTransfer) }
        }

        /** An outgoing ruble transfer — what the spending card counts as «Переводы людям». */
        private fun isOutTransfer(e: HistoryEvent) =
            e.kind == HistoryKind.TRANSFER && (e.amountMinor ?: 0L) < 0L && e.foreign == null
    }
}
