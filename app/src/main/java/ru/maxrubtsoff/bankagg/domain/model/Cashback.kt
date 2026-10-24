package ru.maxrubtsoff.bankagg.domain.model

/** A cashback rule for one bank/category in a given month. */
data class CashbackItem(
    val id: String,
    val monthKey: String,     // "YYYY-MM"
    val brand: BankBrand,
    val category: String,
    val percent: Double,
    val createdAt: Long = 0,
    /** [CategoryGroups] key: icon, grouping and comparison across banks. */
    val groupKey: String = CategoryGroups.OTHER
)
