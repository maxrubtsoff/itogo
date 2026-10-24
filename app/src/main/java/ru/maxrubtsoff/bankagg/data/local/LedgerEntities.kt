package ru.maxrubtsoff.bankagg.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** An account with its latest known balance (from the most recent notification). */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,          // e.g. "tbank:RUB"
    val brandKey: String,
    val title: String,
    val currency: String,
    val balanceMinor: Long,
    val updatedAt: Long,
    @ColumnInfo(defaultValue = "0") val excluded: Boolean = false,
    /** B-29: when the balance was last exact; null — unknown yet. */
    val checkedAt: Long? = null,
    /** B-29: operations without a balance were added on top since [checkedAt]. */
    @ColumnInfo(defaultValue = "0") val estimated: Boolean = false
)

/** A parsed operation; id = source event dedup hash, so it is idempotent. */
@Entity(
    tableName = "transactions",
    indices = [Index(value = ["postedAt"]), Index(value = ["accountId"])]
)
data class TransactionEntity(
    @PrimaryKey val id: String,
    val brandKey: String,
    val accountId: String,
    val amountMinor: Long,               // negative = expense
    val currency: String,
    val kind: String,                    // INCOME | EXPENSE | TRANSFER | UNKNOWN
    val category: String?,
    val counterparty: String?,
    val postedAt: Long
)
