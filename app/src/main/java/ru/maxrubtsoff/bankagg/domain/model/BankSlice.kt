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
    val hasCashback: Boolean = false,
    val subscriptionsCount: Int = 0,
    val subscriptionsMonthlyMinor: Long = 0,
    val monthCashbackCategories: Int = 0,
    /** Average income of the bank's deposits per month. */
    val depositsMonthlyMinor: Long = 0,
    /** For the rest slice: how much is bought-by-hand currency. */
    val restCurrencyMinor: Long = 0,
    /** For the rest slice: how much is cash. */
    val restCashMinor: Long = 0
) {
    data class Line(val title: String, val valueMinor: Long)

    val accountsCount: Int get() = accounts.size
    val depositsCount: Int get() = deposits.size
    val accountsTotalMinor: Long get() = accounts.sumOf { it.valueMinor }
    val depositsTotalMinor: Long get() = deposits.sumOf { it.valueMinor }

    /** «2 счёта · 1 вклад · 3 подписки»; empty positions are skipped. */
    val countersLine: String
        get() = listOfNotNull(
            plural(accountsCount, "счёт", "счёта", "счетов"),
            plural(depositsCount, "вклад", "вклада", "вкладов"),
            plural(subscriptionsCount, "подписка", "подписки", "подписок")
        ).joinToString(" · ")

    /** Stable id of the row: the bank's key, or a fixed one for the rest. */
    val key: String get() = brand?.key ?: REST_KEY

    val isRest: Boolean get() = brand == null

    companion object {
        const val REST_KEY = "rest"
    }
}

/** «N слов» in the right form; null for zero. */
fun plural(n: Int, one: String, few: String, many: String): String? {
    if (n <= 0) return null
    val form = when {
        n % 100 in 11..14 -> many
        n % 10 == 1 -> one
        n % 10 in 2..4 -> few
        else -> many
    }
    return "$n $form"
}

/** How the capital is cut on the Overview (F-53). */
enum class SliceMode { BY_PARTS, BY_BANKS }
