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

    /** Stamp of an old amount that never had one (B-127): a stable key for its log row, never a real time. */
    const val LEGACY_AT = 1L

    /** The key of the old edit's row: the amount's stamp, or [LEGACY_AT] when it has none. */
    fun legacyStamp(cash: CashBalance): Long = if (cash.updatedAt > 0L) cash.updatedAt else LEGACY_AT

    /** The log row for the old edit; null when there is nothing to show. */
    fun legacyChange(cash: CashBalance?, changes: List<CashChange>): CashChange? {
        if (cash == null) return null
        val stamp = legacyStamp(cash)
        if (changes.any { it.atMs == stamp }) return null
        val delta = legacyDelta(cash, changes)
        return if (delta == 0L) null else CashChange(stamp, cash.amount, Money.ofMinor(delta))
    }
}
