package ru.maxrubtsoff.bankagg.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleLedgerRepository
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.Transaction
import ru.maxrubtsoff.bankagg.domain.model.TransactionKind
import ru.maxrubtsoff.bankagg.domain.usecase.DeleteAccount

class DeleteAccountTest {

    private val usd = Account("bsb:USD", BankBrand.SBER, "BSB USD", Money.ofMinor(50_000), "USD", 0L)
    private val rub = Account("sber:RUB", BankBrand.SBER, "Сбер", Money.ofMinor(1_000_000), "RUB", 0L)

    private fun tx(id: String, account: String) = Transaction(
        id, BankBrand.SBER, account, Money.ofMinor(-100), "USD", TransactionKind.EXPENSE, null, null, 1_000L
    )

    @Test fun hasOperationsTrueFalse() = runTest {
        val ledger = SampleLedgerRepository(listOf(usd, rub), listOf(tx("t1", usd.id)))
        val use = DeleteAccount(ledger)
        assertTrue(use.hasOperations(usd.id))
        assertFalse(use.hasOperations(rub.id))
    }

    @Test fun deleteRemovesAccount() = runTest {
        val ledger = SampleLedgerRepository(listOf(usd, rub), emptyList())
        DeleteAccount(ledger).delete(usd.id)
        assertEquals(listOf(rub.id), ledger.observeAccounts().first().map { it.id })
    }

    @Test fun deleteRemovesItsOperationsOnly() = runTest {
        val ledger = SampleLedgerRepository(listOf(usd, rub), listOf(tx("t1", usd.id), tx("t2", rub.id)))
        DeleteAccount(ledger).delete(usd.id)
        assertEquals(listOf("t2"), ledger.observeTransactions().first().map { it.id })
    }

    @Test fun setBalanceOnForeignAccountKeepsCurrency() = runTest {
        val ledger = SampleLedgerRepository(listOf(usd), emptyList())
        ledger.setBalance(usd.id, 123_400L, 5_000L)
        val a = ledger.account(usd.id)!!
        assertEquals("USD", a.currency)
        assertEquals(123_400L, a.balance.minor)
    }
}
