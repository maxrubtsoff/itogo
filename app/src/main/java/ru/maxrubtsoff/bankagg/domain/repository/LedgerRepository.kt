package ru.maxrubtsoff.bankagg.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.Transaction

/** Local store of accounts (latest balances) and parsed transactions. */
interface LedgerRepository {

    /**
     * An operation of [amountMinor] on the account, with the bank's balance when
     * the push has one; without it the app keeps its own count (B-29).
     */
    suspend fun upsertAccountBalance(
        accountId: String,
        brand: BankBrand,
        currency: String,
        amountMinor: Long,
        balanceMinor: Long?,
        postedAt: Long
    )

    /** «Сейчас на счёте»: the balance the user sees in the bank's app — the new starting point (B-29). */
    suspend fun setBalance(accountId: String, balanceMinor: Long, at: Long)

    /** Operations on the account after [from] (B-29: what the counted balance added up). */
    suspend fun sumSince(accountId: String, from: Long): Long

    /** Adds a transaction; returns false if one with the same id already exists. */
    suspend fun addTransaction(tx: Transaction): Boolean

    fun observeAccounts(): Flow<List<Account>>

    /** «Не учитывать в капитале» / «Учитывать» (U-18). */
    suspend fun setExcluded(accountId: String, excluded: Boolean)
    fun observeTransactions(limit: Int = 100): Flow<List<Transaction>>

    suspend fun clear()
}
