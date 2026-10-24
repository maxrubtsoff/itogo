package ru.maxrubtsoff.bankagg.domain.model

/**
 * How a list is ordered (U-12): a sort with its natural direction, or reversed.
 * One model for deposits and cashback — the arrow button flips the direction,
 * the sheet picks the sort.
 */
data class SortOrder<S>(val sort: S, val reversed: Boolean = false) {
    /** Another sort starts in its natural direction; the same one stays as it is. */
    fun pick(next: S): SortOrder<S> = if (next == sort) this else SortOrder(next)

    fun flipped(): SortOrder<S> = copy(reversed = !reversed)
}
