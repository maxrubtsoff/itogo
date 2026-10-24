package ru.maxrubtsoff.bankagg.data

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleCashbackRepository
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleDepositRepository
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleLedgerRepository
import ru.maxrubtsoff.bankagg.domain.model.TourSample

/** The tours' in-memory repositories (U-54): they behave like the real ones and forget everything with the tour. */
class SampleRepositoriesTest {

    private val today = LocalDate.of(2026, 10, 24)
    private val data = TourSample.data(today)

    @Test fun ledgerTakesTheBanksBalanceAndKeepsTheOperation() = runBlocking {
        val repo = SampleLedgerRepository(data.accounts, data.transactions)
        val purchase = TourSample.purchase(today)
        assertTrue(repo.addTransaction(purchase))
        assertTrue(!repo.addTransaction(purchase))
        repo.upsertAccountBalance(
            purchase.accountId, purchase.brand, purchase.currency,
            purchase.amount.minor, TourSample.BALANCE_AFTER_PURCHASE_MINOR, purchase.postedAt
        )
        assertEquals(TourSample.BALANCE_AFTER_PURCHASE_MINOR, repo.account(purchase.accountId)!!.balance.minor)
        assertEquals(purchase, repo.observeTransactions(10).first().first { it.id == purchase.id })
    }

    @Test fun anotherInstanceStartsFromTheExamples() = runBlocking {
        val first = SampleLedgerRepository(data.accounts, data.transactions)
        first.clear()
        val second = SampleLedgerRepository(data.accounts, data.transactions)
        assertEquals(data.accounts, second.observeAccounts().first())
        assertTrue(first.observeAccounts().first().isEmpty())
    }

    @Test fun depositAddedInTheTourShowsInTheListOnly() = runBlocking {
        val repo = SampleDepositRepository(data.deposits)
        repo.add(TourSample.scannedDeposit(today))
        assertEquals(data.deposits.size + 1, repo.observeAll().first().size)
        assertEquals(data.deposits.size, SampleDepositRepository(data.deposits).observeAll().first().size)
    }

    @Test fun cashbackAppendsABankToTheMonth() = runBlocking {
        val repo = SampleCashbackRepository(data.cashback)
        val before = repo.observeMonth(data.monthKey).first().size
        val incoming = TourSample.scannedCashbackLines().mapIndexed { i, l ->
            ru.maxrubtsoff.bankagg.domain.model.CashbackItem(
                "new-$i", data.monthKey, TourSample.BANK, l.text, l.percent ?: TourSample.FILLED_PERCENT, groupKey = l.groupKey
            )
        }
        repo.saveBankMonth(data.monthKey, TourSample.BANK, incoming, append = false)
        assertEquals(before + incoming.size, repo.observeMonth(data.monthKey).first().size)
        assertEquals(listOf(data.monthKey), repo.observeMonths().first())
    }
}
