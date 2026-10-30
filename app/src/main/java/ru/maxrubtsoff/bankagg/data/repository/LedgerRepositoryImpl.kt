package ru.maxrubtsoff.bankagg.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.maxrubtsoff.bankagg.data.local.AccountEntity
import ru.maxrubtsoff.bankagg.data.local.LedgerDao
import ru.maxrubtsoff.bankagg.data.local.toDomain
import ru.maxrubtsoff.bankagg.data.local.toEntity
import ru.maxrubtsoff.bankagg.domain.model.BalanceState
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.RunningBalance
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.Transaction
import ru.maxrubtsoff.bankagg.domain.repository.LedgerRepository

class LedgerRepositoryImpl(
    private val dao: LedgerDao
) : LedgerRepository {

    override suspend fun upsertAccountBalance(
        accountId: String,
        brand: BankBrand,
        currency: String,
        amountMinor: Long,
        balanceMinor: Long?,
        postedAt: Long
    ) {
        // Read-modify-write (the caller wraps it in one transaction): a new account
        // is inserted; an existing one only gets its balance moved — the title the
        // user gave it stays. Without the bank's balance the app counts (B-29).
        val existing = dao.account(accountId)
        val next = RunningBalance.apply(existing?.state(), amountMinor, balanceMinor, postedAt)
        if (existing == null) {
            dao.insertAccountIfMissing(
                AccountEntity(
                    id = accountId, brandKey = brand.key, title = brand.title, currency = currency,
                    balanceMinor = next.balanceMinor, updatedAt = next.updatedAt,
                    checkedAt = next.checkedAt, estimated = next.estimated
                )
            )
        } else {
            dao.setBalance(accountId, next.balanceMinor, next.updatedAt, next.checkedAt, next.estimated)
        }
    }

    override suspend fun setBalance(accountId: String, balanceMinor: Long, at: Long) {
        val existing = dao.account(accountId) ?: return
        val next = RunningBalance.setByUser(existing.state(), balanceMinor, at)
        dao.setBalance(accountId, next.balanceMinor, next.updatedAt, next.checkedAt, next.estimated)
    }

    override suspend fun restoreBalance(accountId: String, balanceMinor: Long, updatedAt: Long, checkedAt: Long?, estimated: Boolean) =
        dao.setBalance(accountId, balanceMinor, updatedAt, checkedAt, estimated)

    override suspend fun sumSince(accountId: String, from: Long): Long = dao.sumSince(accountId, from)

    private fun AccountEntity.state() = BalanceState(balanceMinor, updatedAt, checkedAt, estimated)

    override suspend fun setExcluded(accountId: String, excluded: Boolean) = dao.setExcluded(accountId, excluded)

    override suspend fun addTransaction(tx: Transaction): Boolean =
        dao.insertTransaction(tx.toEntity()) != -1L

    override suspend fun account(accountId: String): Account? = dao.account(accountId)?.toDomain()

    override suspend fun transaction(id: String): Transaction? = dao.transaction(id)?.toDomain()

    override suspend fun updateTransaction(tx: Transaction) = dao.replaceTransaction(tx.toEntity())

    override suspend fun deleteTransaction(id: String) = dao.deleteTransaction(id)

    override suspend fun setCountedBalance(accountId: String, balanceMinor: Long, estimated: Boolean) =
        dao.setCountedBalance(accountId, balanceMinor, estimated)

    override suspend fun hasTransactions(accountId: String) = dao.countTransactions(accountId) > 0

    override suspend fun deleteAccount(accountId: String) = dao.deleteAccount(accountId)

    override suspend fun resetBalanceBase(accountId: String) = dao.resetBalanceBase(accountId)

    override fun observeAccounts(): Flow<List<Account>> =
        dao.observeAccounts().map { list -> list.map { it.toDomain() } }

    override fun observeTransactions(limit: Int): Flow<List<Transaction>> =
        dao.observeTransactions(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun clear() {
        dao.clearTransactions()
        dao.clearAccounts()
    }
}
