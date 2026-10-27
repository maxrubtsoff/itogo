package ru.maxrubtsoff.bankagg.domain.model

/**
 * One bank's share of the capital (F-53): what lies with it and what links
 * lead on from it. [brand] null — the rest that is not in any bank (cash, a
 * currency bought by hand, accounts of an unknown bank).
 */
data class BankSlice(
    val brand: BankBrand?,
    val valueMinor: Long,
    /** Share of the banks' positive total, 0..1. */
    val share: Float,
    val accounts: List<Line> = emptyList(),
    val deposits: List<Line> = emptyList(),
    val hasSubscriptions: Boolean = false,
    val hasCashback: Boolean = false
) {
    data class Line(val title: String, val valueMinor: Long)

    /** Stable id of the row: the bank's key, or a fixed one for the rest. */
    val key: String get() = brand?.key ?: REST_KEY

    companion object {
        const val REST_KEY = "rest"
    }
}

/** How the capital is cut on the Overview (F-53). */
enum class SliceMode { BY_PARTS, BY_BANKS }
