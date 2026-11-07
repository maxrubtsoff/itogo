package ru.maxrubtsoff.bankagg.domain.usecase

import ru.maxrubtsoff.bankagg.domain.repository.LedgerRepository

/**
 * Deleting an account by hand (F-57). Its operations go too: left behind, they
 * would count in the history with no account to hold them. A new notification
 * from the bank makes the account again.
 */
class DeleteAccount(private val ledger: LedgerRepository) {

    suspend fun hasOperations(accountId: String): Boolean = ledger.hasTransactions(accountId)

    suspend fun delete(accountId: String) {
        ledger.deleteTransactionsOf(accountId)
        ledger.deleteAccount(accountId)
    }
}
