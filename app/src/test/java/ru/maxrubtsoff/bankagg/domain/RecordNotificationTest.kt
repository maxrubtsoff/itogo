package ru.maxrubtsoff.bankagg.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CapturedEvent
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.Transaction
import ru.maxrubtsoff.bankagg.domain.repository.EventRepository
import ru.maxrubtsoff.bankagg.domain.repository.LedgerRepository
import ru.maxrubtsoff.bankagg.domain.repository.TransactionRunner
import ru.maxrubtsoff.bankagg.domain.usecase.RecordNotification

class RecordNotificationTest {

    private class FakeEvents : EventRepository {
        val stored = mutableListOf<CapturedEvent>()
        override suspend fun record(event: CapturedEvent): Boolean {
            if (stored.any { it.dedupHash == event.dedupHash }) return false
            stored += event; return true
        }
        override suspend fun hasSimilar(contentHash: String, from: Long, to: Long, notificationKey: String?) =
            stored.any {
                it.contentHash == contentHash && it.postedAt in from..to &&
                    (notificationKey == null || it.notificationKey == null || it.notificationKey == notificationKey)
            }
        override fun observeRecent(limit: Int): Flow<List<CapturedEvent>> = emptyFlow()
        override suspend fun trim(parsedBefore: Long, unparsedBefore: Long) = 0
        override suspend fun all(): List<CapturedEvent> = stored.toList()
        override suspend fun updateParse(id: Long, parsed: Boolean, dedupHash: String, contentHash: String) = Unit
    }

    private class FakeLedger : LedgerRepository {
        val balances = mutableMapOf<String, Long?>()
        val txs = mutableListOf<Transaction>()
        override suspend fun upsertAccountBalance(
            accountId: String, brand: BankBrand, currency: String, amountMinor: Long, balanceMinor: Long?, postedAt: Long
        ) { balances[accountId] = balanceMinor }
        override suspend fun setBalance(accountId: String, balanceMinor: Long, at: Long) { balances[accountId] = balanceMinor }
        override suspend fun sumSince(accountId: String, from: Long) = 0L
        override suspend fun addTransaction(tx: Transaction): Boolean = txs.add(tx)
        override fun observeAccounts(): Flow<List<Account>> = emptyFlow()
        override suspend fun setExcluded(accountId: String, excluded: Boolean) = Unit
        override fun observeTransactions(limit: Int): Flow<List<Transaction>> = emptyFlow()
        override suspend fun clear() {}
        override suspend fun hasTransactions(accountId: String) = false
        override suspend fun deleteAccount(accountId: String) = Unit
        override suspend fun resetBalanceBase(accountId: String) = Unit
        override suspend fun account(accountId: String): Account? = null
        override suspend fun transaction(id: String): Transaction? = txs.firstOrNull { it.id == id }
        override suspend fun deleteTransaction(id: String) { txs.removeAll { it.id == id } }
        override suspend fun setCountedBalance(accountId: String, balanceMinor: Long, estimated: Boolean) {
            balances[accountId] = balanceMinor
        }
    }

    private val direct = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }

    private val tbank = "com.idamob.tinkoff.android"
    private val text = "Покупка 500 ₽. Магнит. Доступно 1 000 ₽"

    @Test fun repeat_within_two_minutes_is_one_operation() = runTest {
        val events = FakeEvents(); val ledger = FakeLedger()
        val record = RecordNotification(events, ledger, direct)
        assertTrue(record(tbank, "Т-Банк", text, 1_000_000L))
        assertFalse(record(tbank, "Т-Банк", text, 1_000_000L + 90_000L))
        assertEquals(1, ledger.txs.size)
    }

    @Test fun same_text_later_is_a_new_operation() = runTest {
        val events = FakeEvents(); val ledger = FakeLedger()
        val record = RecordNotification(events, ledger, direct)
        assertTrue(record(tbank, null, text, 1_000_000L))
        assertTrue(record(tbank, null, text, 1_000_000L + 10 * 60_000L))
        assertEquals(2, ledger.txs.size)
    }

    @Test fun bsb_without_currency_is_logged_but_not_counted() = runTest {
        val events = FakeEvents(); val ledger = FakeLedger()
        val record = RecordNotification(events, ledger, direct)
        assertTrue(record("by.bank.bsb.mobile", "BSB", "Оплата 25,40. Остаток 100,00", 1L))
        assertFalse(events.stored.single().parsed)
        assertTrue(ledger.txs.isEmpty())
    }

    @Test fun bsb_byn_goes_to_a_byn_account() = runTest {
        val events = FakeEvents(); val ledger = FakeLedger()
        val record = RecordNotification(events, ledger, direct)
        record("by.bank.bsb.mobile", "BSB", "Оплата 25,40 BYN. Остаток 100,00 BYN", 1L)
        assertEquals(10_000L, ledger.balances["bsb:BYN"])
    }

    // B-92: without a balance in the push only the notification key tells a repeat from a new operation.

    private val coffee = "Покупка 300 ₽. Кофейня"

    @Test fun equal_pushes_without_balance_and_different_keys_are_two_operations() = runTest {
        val events = FakeEvents(); val ledger = FakeLedger()
        val record = RecordNotification(events, ledger, direct)
        assertTrue(record(tbank, null, coffee, 1_000_000L, "key-1"))
        assertTrue(record(tbank, null, coffee, 1_000_000L + 60_000L, "key-2"))
        assertEquals(2, ledger.txs.size)
    }

    @Test fun repeat_without_balance_with_same_key_is_one_operation() = runTest {
        val events = FakeEvents(); val ledger = FakeLedger()
        val record = RecordNotification(events, ledger, direct)
        assertTrue(record(tbank, null, coffee, 1_000_000L, "key-1"))
        assertFalse(record(tbank, null, coffee, 1_000_000L + 60_000L, "key-1"))
        assertEquals(1, ledger.txs.size)
    }

    @Test fun unknown_key_keeps_old_behaviour() = runTest {
        val events = FakeEvents(); val ledger = FakeLedger()
        val record = RecordNotification(events, ledger, direct)
        assertTrue(record(tbank, null, coffee, 1_000_000L))
        assertFalse(record(tbank, null, coffee, 1_000_000L + 60_000L, "key-2"))
    }

    @Test fun push_with_balance_ignores_the_key() = runTest {
        val events = FakeEvents(); val ledger = FakeLedger()
        val record = RecordNotification(events, ledger, direct)
        assertTrue(record(tbank, null, text, 1_000_000L, "key-1"))
        assertFalse(record(tbank, null, text, 1_000_000L + 60_000L, "key-2"))
    }
}
