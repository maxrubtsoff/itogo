package ru.maxrubtsoff.bankagg.domain.model

/** Nature of an operation. TRANSFER is kept out of spending totals. */
enum class TransactionKind { INCOME, EXPENSE, TRANSFER, UNKNOWN }

/** An account with its latest balance, built from bank notifications. */
data class Account(
    val id: String,
    val brand: BankBrand,
    val title: String,
    val balance: Money,
    val currency: String,
    val updatedAt: Long,
    /** Kept in the list but not counted in the capital (U-18). */
    val excluded: Boolean = false,
    /** When the balance was last given exactly (bank or user); null — never: ask for it (B-29). */
    val checkedAt: Long? = null,
    /** The app's own count since [checkedAt]: shown with «≈» (B-29). */
    val estimated: Boolean = false
) {
    /** The bank never told the balance and the user hasn't either. */
    val needsBalance: Boolean get() = checkedAt == null
}

/** An account-level operation. [amount] is negative for expenses. */
data class Transaction(
    val id: String,
    val brand: BankBrand,
    val accountId: String,
    val amount: Money,
    val currency: String,
    val kind: TransactionKind,
    val category: String?,
    val counterparty: String?,
    val postedAt: Long
)
