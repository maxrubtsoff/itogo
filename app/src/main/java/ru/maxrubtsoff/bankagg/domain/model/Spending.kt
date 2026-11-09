package ru.maxrubtsoff.bankagg.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** What one bank's accounts spent in a month (F-58): kopecks, positive. */
data class BankSpend(val brand: BankBrand, val minor: Long, val count: Int)

/**
 * The spending of one month on the «Счета» page (F-58). All sums are positive kopecks. [totalMinor] is the
 * purchases and, when [includeTransfers], the transfers to other people; transfers between the person's own
 * accounts are never spending (F-59). [byBank] counts purchases only, so it does not move with the switch.
 */
data class MonthSpending(
    val month: YearMonth,
    val totalMinor: Long,
    val purchasesMinor: Long,
    val transfersMinor: Long,
    val transfersCount: Int,
    /** What came in over the month, own transfers apart. */
    val incomeMinor: Long,
    val count: Int,
    val byBank: List<BankSpend>,
    val includeTransfers: Boolean
)

/** Banks shown as rows and the rest folded into «Ещё N банков». */
data class SpendFold(val shown: List<BankSpend>, val rest: List<BankSpend>)

object Spending {
    const val MONTHS = 6
    /** More banks than this fold: four rows and the rest. */
    const val MAX_ROWS = 5

    /** A ruble operation on a counted account; own transfers [ownIds] are none of spending. */
    private fun counted(t: Transaction, byId: Map<String, Account>, ownIds: Set<String>): Boolean =
        t.currency == Currencies.RUB && t.id !in ownIds && byId[t.accountId]?.excluded == false

    private fun isSpend(t: Transaction) = t.amount.minor < 0L && t.kind != TransactionKind.INCOME

    fun month(
        transactions: List<Transaction>,
        accounts: List<Account>,
        ownIds: Set<String>,
        month: YearMonth,
        includeTransfers: Boolean,
        zone: ZoneId
    ): MonthSpending {
        val byId = accounts.associateBy { it.id }
        val inMonth = transactions.filter { counted(it, byId, ownIds) && YearMonth.from(Instant.ofEpochMilli(it.postedAt).atZone(zone)) == month }
        val spends = inMonth.filter(::isSpend)
        val purchases = spends.filter { it.kind != TransactionKind.TRANSFER }
        val transfers = spends.filter { it.kind == TransactionKind.TRANSFER }
        val purchasesMinor = purchases.sumOf { -it.amount.minor }
        val transfersMinor = transfers.sumOf { -it.amount.minor }
        val byBank = purchases.groupBy { it.brand }
            .map { (b, list) -> BankSpend(b, list.sumOf { -it.amount.minor }, list.size) }
            .sortedByDescending { it.minor }
        return MonthSpending(
            month = month,
            totalMinor = purchasesMinor + if (includeTransfers) transfersMinor else 0L,
            purchasesMinor = purchasesMinor,
            transfersMinor = transfersMinor,
            transfersCount = transfers.size,
            incomeMinor = inMonth.filter { it.amount.minor > 0L }.sumOf { it.amount.minor },
            count = purchases.size + if (includeTransfers) transfers.size else 0,
            byBank = byBank,
            includeTransfers = includeTransfers
        )
    }

    /** The months of the card's columns: [size] ending with the month of [today]. */
    fun window(today: LocalDate, size: Int = MONTHS): List<YearMonth> {
        val last = YearMonth.from(today)
        return (size - 1 downTo 0).map { last.minusMonths(it.toLong()) }
    }

    /** Whether there is a single spending in the whole history: if not, the card is not shown. */
    fun hasAny(transactions: List<Transaction>, accounts: List<Account>, ownIds: Set<String>): Boolean {
        val byId = accounts.associateBy { it.id }
        return transactions.any { counted(it, byId, ownIds) && isSpend(it) }
    }

    fun fold(byBank: List<BankSpend>): SpendFold =
        if (byBank.size <= MAX_ROWS) SpendFold(byBank, emptyList()) else SpendFold(byBank.take(MAX_ROWS - 1), byBank.drop(MAX_ROWS - 1))
}
