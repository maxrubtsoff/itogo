package ru.maxrubtsoff.bankagg.data.repository.sample

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.Transaction
import ru.maxrubtsoff.bankagg.domain.repository.LedgerRepository

/**
 * Accounts and operations of a tour (U-54): in memory only, from the example
 * data. Nothing is written to the database or the backup; when the tour ends
 * the object is dropped with everything the person did in it.
 */
class SampleLedgerRepository(accounts: List<Account>, transactions: List<Transaction>) : LedgerRepository {

    private val accounts = MutableStateFlow(accounts)
    private val transactions = MutableStateFlow(transactions.sortedByDescending { it.postedAt })

    override suspend fun upsertAccountBalance(
        accountId: String, brand: BankBrand, currency: String, amountMinor: Long, balanceMinor: Long?, postedAt: Long
    ) {
        accounts.update { list ->
            val old = list.firstOrNull { it.id == accountId }
            val balance = balanceMinor ?: ((old?.balance?.minor ?: 0L) + amountMinor)
            val next = (old ?: Account(accountId, brand, brand.title, Money.ZERO, currency, postedAt)).copy(
                balance = Money.ofMinor(balance),
                updatedAt = postedAt,
                checkedAt = if (balanceMinor != null) postedAt else old?.checkedAt,
                estimated = balanceMinor == null
            )
            if (old == null) list + next else list.map { if (it.id == accountId) next else it }
        }
    }

    override suspend fun setBalance(accountId: String, balanceMinor: Long, at: Long) = change(accountId) {
        it.copy(balance = Money.ofMinor(balanceMinor), updatedAt = at, checkedAt = at, estimated = false)
    }

    override suspend fun sumSince(accountId: String, from: Long): Long =
        transactions.value.filter { it.accountId == accountId && it.postedAt > from }.sumOf { it.amount.minor }

    override suspend fun addTransaction(tx: Transaction): Boolean {
        if (transactions.value.any { it.id == tx.id }) return false
        transactions.update { (it + tx).sortedByDescending { t -> t.postedAt } }
        return true
    }

    override suspend fun account(accountId: String): Account? = accounts.value.firstOrNull { it.id == accountId }

    override suspend fun transaction(id: String): Transaction? = transactions.value.firstOrNull { it.id == id }

    override suspend fun deleteTransaction(id: String) = transactions.update { list -> list.filterNot { it.id == id } }

    override suspend fun setCountedBalance(accountId: String, balanceMinor: Long, estimated: Boolean) = change(accountId) {
        it.copy(balance = Money.ofMinor(balanceMinor), estimated = estimated)
    }

    override suspend fun hasTransactions(accountId: String) = transactions.value.any { it.accountId == accountId }

    override suspend fun deleteAccount(accountId: String) = accounts.update { list -> list.filterNot { it.id == accountId } }

    override suspend fun resetBalanceBase(accountId: String) = change(accountId) {
        it.copy(balance = Money.ZERO, checkedAt = null, estimated = false)
    }

    override fun observeAccounts(): Flow<List<Account>> = accounts

    override suspend fun setExcluded(accountId: String, excluded: Boolean) = change(accountId) { it.copy(excluded = excluded) }

    override fun observeTransactions(limit: Int): Flow<List<Transaction>> = transactions.map { it.take(limit) }

    override suspend fun clear() {
        accounts.value = emptyList()
        transactions.value = emptyList()
    }

    private fun change(accountId: String, edit: (Account) -> Account) =
        accounts.update { list -> list.map { if (it.id == accountId) edit(it) else it } }
}
