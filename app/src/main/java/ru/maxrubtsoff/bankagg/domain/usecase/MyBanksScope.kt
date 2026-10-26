package ru.maxrubtsoff.bankagg.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CashbackItem
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.model.Transaction
import ru.maxrubtsoff.bankagg.domain.repository.BankRepository
import ru.maxrubtsoff.bankagg.domain.repository.CashbackRepository
import ru.maxrubtsoff.bankagg.domain.repository.DepositRepository
import ru.maxrubtsoff.bankagg.domain.repository.LedgerRepository

/**
 * Only the user's banks are shown and counted (B-21). A bank taken out of
 * «Мои банки» hides its accounts, deposits and cashback everywhere — nothing
 * is deleted: adding the bank back brings it all back. Records with no bank
 * at all stay visible. Operations are hidden the same way, and so are those
 * of an excluded account (B-97).
 */
object MyBanksScope {
    fun keeps(brand: BankBrand, mine: Set<String>): Boolean = brand == BankBrand.UNKNOWN || brand.key in mine

    fun <T> only(items: List<T>, mine: Set<String>, brandOf: (T) -> BankBrand): List<T> =
        items.filter { keeps(brandOf(it), mine) }

    /**
     * Operations of the user's banks and of accounts counted in the capital.
     * [accounts] is the unscoped list, so the excluded flag is seen; an
     * operation with no known account stays.
     */
    fun ops(transactions: List<Transaction>, accounts: List<Account>, mine: Set<String>): List<Transaction> {
        val excluded = accounts.filter { it.excluded }.map { it.id }.toSet()
        return transactions.filter { keeps(it.brand, mine) && it.accountId !in excluded }
    }

    private fun keys(banks: List<BankBrand>): Set<String> = banks.map { it.key }.toSet()

    /** The repositories the screens read: same data, scoped to the user's banks. */
    class Ledger(private val inner: LedgerRepository, private val banks: BankRepository) : LedgerRepository by inner {
        override fun observeAccounts(): Flow<List<Account>> =
            combine(inner.observeAccounts(), banks.myBanks) { a, mine -> only(a, keys(mine)) { it.brand } }

        override fun observeTransactions(limit: Int): Flow<List<Transaction>> =
            combine(inner.observeTransactions(limit), inner.observeAccounts(), banks.myBanks) { t, a, mine ->
                ops(t, a, keys(mine))
            }
    }

    class Deposits(private val inner: DepositRepository, private val banks: BankRepository) : DepositRepository by inner {
        override fun observeAll(): Flow<List<Deposit>> =
            combine(inner.observeAll(), banks.myBanks) { d, mine -> only(d, keys(mine)) { it.brand } }
    }

    class Cashback(private val inner: CashbackRepository, private val banks: BankRepository) : CashbackRepository by inner {
        override fun observeMonth(monthKey: String): Flow<List<CashbackItem>> =
            combine(inner.observeMonth(monthKey), banks.myBanks) { c, mine -> only(c, keys(mine)) { it.brand } }
    }
}
