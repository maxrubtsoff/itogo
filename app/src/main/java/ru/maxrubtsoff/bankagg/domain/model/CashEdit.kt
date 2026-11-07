package ru.maxrubtsoff.bankagg.domain.model

/** Changing the cash amount in one of three ways; the result can never go below zero. */
object CashEdit {

    enum class Mode { SET, ADD, TAKE }

    /** The new amount in kopecks, or null when taking more than there is. */
    fun result(currentMinor: Long, mode: Mode, inputMinor: Long): Long? = when (mode) {
        Mode.SET -> inputMinor.takeIf { it >= 0L }
        Mode.ADD -> currentMinor + inputMinor
        Mode.TAKE -> (currentMinor - inputMinor).takeIf { it >= 0L }
    }

    /** B-124: the delta of the edit made before the log existed: what the logged edits do not explain (the sum of all deltas is the amount). */
    fun legacyDelta(cash: CashBalance, changes: List<CashChange>): Long = cash.amount.minor - changes.sumOf { it.delta.minor }

    /** The log row for the old edit; null when there is nothing to show. */
    fun legacyChange(cash: CashBalance?, changes: List<CashChange>): CashChange? {
        if (cash == null || cash.updatedAt <= 0L || changes.any { it.atMs == cash.updatedAt }) return null
        val delta = legacyDelta(cash, changes)
        return if (delta == 0L) null else CashChange(cash.updatedAt, cash.amount, Money.ofMinor(delta))
    }
}
