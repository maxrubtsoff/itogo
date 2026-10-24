package ru.maxrubtsoff.bankagg.domain.model

/** How the deposits list is ordered. Each sort has a natural direction; [DepositOrder.reversed] flips it. */
enum class DepositSort(val key: String) {
    /** Ending soonest first. */
    END("end"),
    /** Opened most recently first. */
    START("start"),
    /** Largest current value first. */
    AMOUNT("amount"),
    /** Most accrued so far first. */
    GAIN("gain"),
    /** Highest rate first. */
    RATE("rate"),
    /** Largest income over the whole term first. */
    INCOME("income"),
    /** Bank name, А→Я. */
    BANK("bank");

    companion object {
        val DEFAULT = END
        fun fromKey(key: String?): DepositSort? = entries.firstOrNull { it.key == key }
    }
}

typealias DepositOrder = SortOrder<DepositSort>

object DepositSorting {

    fun <T> sort(
        items: List<T>,
        order: DepositOrder,
        deposit: (T) -> Deposit,
        projection: (T) -> DepositMath.Projection
    ): List<T> {
        val natural: Comparator<T> = when (order.sort) {
            DepositSort.END -> compareBy { deposit(it).endDate }
            DepositSort.START -> compareByDescending { deposit(it).startDate }
            DepositSort.AMOUNT -> compareByDescending { projection(it).currentValue.minor }
            DepositSort.GAIN -> compareByDescending { projection(it).accruedIncome.minor }
            DepositSort.RATE -> compareByDescending { deposit(it).rateBp }
            DepositSort.INCOME -> compareByDescending { projection(it).totalIncome.minor }
            DepositSort.BANK -> compareBy { deposit(it).brand.title.lowercase() }
        }
        val primary = if (order.reversed) natural.reversed() else natural
        // Stable tie-break so equal rows never jump around.
        return items.sortedWith(primary.thenBy { deposit(it).endDate }.thenBy { deposit(it).id })
    }
}
