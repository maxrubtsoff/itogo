package ru.maxrubtsoff.bankagg.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.flow.first
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CapturedEvent
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.Transaction
import ru.maxrubtsoff.bankagg.domain.repository.EventRepository
import ru.maxrubtsoff.bankagg.domain.repository.LedgerRepository
import ru.maxrubtsoff.bankagg.domain.repository.TransactionRunner
import ru.maxrubtsoff.bankagg.domain.usecase.RecordNotification
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleCashRepository
import ru.maxrubtsoff.bankagg.domain.model.BalanceState
import ru.maxrubtsoff.bankagg.domain.model.RunningBalance
import java.time.LocalDateTime
import java.time.ZoneId

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
        override suspend fun byHash(hash: String): CapturedEvent? = stored.firstOrNull { it.dedupHash == hash }
    }

    private class FakeLedger : LedgerRepository {
        val balances = mutableMapOf<String, Long?>()
        val txs = mutableListOf<Transaction>()
        /** accountId, currency, amountMinor of each upsert. */
        val upserts = mutableListOf<Triple<String, String, Long>>()
        /** balanceMinor, postedAt of each upsert, in call order. */
        val upsertTimes = mutableListOf<Pair<Long?, Long>>()
        override suspend fun upsertAccountBalance(
            accountId: String, brand: BankBrand, currency: String, amountMinor: Long, balanceMinor: Long?, postedAt: Long
        ) { balances[accountId] = balanceMinor; upserts += Triple(accountId, currency, amountMinor); upsertTimes += balanceMinor to postedAt }
        override suspend fun setBalance(accountId: String, balanceMinor: Long, at: Long) { balances[accountId] = balanceMinor }
        override suspend fun restoreBalance(accountId: String, balanceMinor: Long, updatedAt: Long, checkedAt: Long?, estimated: Boolean) {}
        override suspend fun sumSince(accountId: String, from: Long) = 0L
        override suspend fun addTransaction(tx: Transaction): Boolean = txs.add(tx)
        override fun observeAccounts(): Flow<List<Account>> = emptyFlow()
        override suspend fun setExcluded(accountId: String, excluded: Boolean) = Unit
        override fun observeTransactions(limit: Int): Flow<List<Transaction>> = emptyFlow()
        override suspend fun clear() {}
        override suspend fun hasTransactions(accountId: String) = false
        override suspend fun deleteTransactionsOf(accountId: String) = Unit
        override suspend fun deleteAccount(accountId: String) = Unit
        override suspend fun resetBalanceBase(accountId: String) = Unit
        val accounts = mutableMapOf<String, Account>()
        override suspend fun account(accountId: String): Account? = accounts[accountId]
        override suspend fun transaction(id: String): Transaction? = txs.firstOrNull { it.id == id }
        override suspend fun deleteTransaction(id: String) { txs.removeAll { it.id == id } }
        override suspend fun updateTransaction(tx: Transaction) { txs.replaceAll { if (it.id == tx.id) tx else it } }
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
        val record = RecordNotification(events, ledger, direct, SampleCashRepository(null))
        assertTrue(record(tbank, "Т-Банк", text, 1_000_000L))
        assertFalse(record(tbank, "Т-Банк", text, 1_000_000L + 90_000L))
        assertEquals(1, ledger.txs.size)
    }

    @Test fun same_text_later_is_a_new_operation() = runTest {
        val events = FakeEvents(); val ledger = FakeLedger()
        val record = RecordNotification(events, ledger, direct, SampleCashRepository(null))
        assertTrue(record(tbank, null, text, 1_000_000L))
        assertTrue(record(tbank, null, text, 1_000_000L + 10 * 60_000L))
        assertEquals(2, ledger.txs.size)
    }

    @Test fun bsb_without_currency_is_logged_but_not_counted() = runTest {
        val events = FakeEvents(); val ledger = FakeLedger()
        val record = RecordNotification(events, ledger, direct, SampleCashRepository(null))
        assertTrue(record("by.bank.bsb.mobile", "BSB", "Оплата 25,40. Остаток 100,00", 1L))
        assertFalse(events.stored.single().parsed)
        assertTrue(ledger.txs.isEmpty())
    }

    @Test fun bsb_byn_goes_to_a_byn_account() = runTest {
        val events = FakeEvents(); val ledger = FakeLedger()
        val record = RecordNotification(events, ledger, direct, SampleCashRepository(null))
        record("by.bank.bsb.mobile", "BSB", "Оплата 25,40 BYN. Остаток 100,00 BYN", 1L)
        assertEquals(10_000L, ledger.balances["bsb:BYN"])
    }

    // B-118: an amount in another currency than the account's balance.

    @Test fun bsb_usd_purchase_with_eur_balance_sets_only_the_eur_balance() = runTest {
        val events = FakeEvents(); val ledger = FakeLedger()
        val record = RecordNotification(events, ledger, direct, SampleCashRepository(null))
        record(
            "by.bank.bsb.mobile", "BSB Bank",
            "4475, Сумма: 1 USD, Успешно, Товары и услуги, 2026-10-30 11:31:53, Остаток: 100.29 EUR, LU3/Luxembourg/PAYPAL", 1L
        )
        assertTrue(events.stored.single().parsed)
        assertTrue(ledger.txs.isEmpty())
        assertEquals(listOf(Triple("bsb:EUR", "EUR", 0L)), ledger.upserts)
        assertEquals(10_029L, ledger.balances["bsb:EUR"])
        assertFalse(ledger.balances.containsKey("bsb:USD"))
    }

    @Test fun rub_balance_with_usd_purchase_is_not_booked_as_rub_or_usd() = runTest {
        val events = FakeEvents(); val ledger = FakeLedger()
        val record = RecordNotification(events, ledger, direct, SampleCashRepository(null))
        record(tbank, "Т-Банк", "Покупка 10 $. Shop. Доступно 1 000 ₽", 1L)
        assertTrue(ledger.txs.isEmpty())
        assertEquals(listOf(Triple("tbank:RUB", "RUB", 0L)), ledger.upserts)
        assertEquals(100_000L, ledger.balances["tbank:RUB"])
    }

    @Test fun usd_purchase_with_usd_balance_is_still_written() = runTest {
        val events = FakeEvents(); val ledger = FakeLedger()
        val record = RecordNotification(events, ledger, direct, SampleCashRepository(null))
        record(tbank, "Т-Банк", "Покупка 10 $. Shop. Доступно 90 $", 1L)
        assertEquals(1, ledger.txs.size)
        assertEquals("tbank:USD", ledger.txs.single().accountId)
        assertEquals(-1_000L, ledger.txs.single().amount.minor)
        assertEquals(listOf(Triple("tbank:USD", "USD", -1_000L)), ledger.upserts)
        assertEquals(9_000L, ledger.balances["tbank:USD"])
    }

    // B-92: without a balance in the push only the notification key tells a repeat from a new operation.

    private val coffee = "Покупка 300 ₽. Кофейня"

    @Test fun equal_pushes_without_balance_and_different_keys_are_two_operations() = runTest {
        val events = FakeEvents(); val ledger = FakeLedger()
        val record = RecordNotification(events, ledger, direct, SampleCashRepository(null))
        assertTrue(record(tbank, null, coffee, 1_000_000L, "key-1"))
        assertTrue(record(tbank, null, coffee, 1_000_000L + 60_000L, "key-2"))
        assertEquals(2, ledger.txs.size)
    }

    @Test fun repeat_without_balance_with_same_key_is_one_operation() = runTest {
        val events = FakeEvents(); val ledger = FakeLedger()
        val record = RecordNotification(events, ledger, direct, SampleCashRepository(null))
        assertTrue(record(tbank, null, coffee, 1_000_000L, "key-1"))
        assertFalse(record(tbank, null, coffee, 1_000_000L + 60_000L, "key-1"))
        assertEquals(1, ledger.txs.size)
    }

    @Test fun unknown_key_keeps_old_behaviour() = runTest {
        val events = FakeEvents(); val ledger = FakeLedger()
        val record = RecordNotification(events, ledger, direct, SampleCashRepository(null))
        assertTrue(record(tbank, null, coffee, 1_000_000L))
        assertFalse(record(tbank, null, coffee, 1_000_000L + 60_000L, "key-2"))
    }

    @Test fun push_with_balance_ignores_the_key() = runTest {
        val events = FakeEvents(); val ledger = FakeLedger()
        val record = RecordNotification(events, ledger, direct, SampleCashRepository(null))
        assertTrue(record(tbank, null, text, 1_000_000L, "key-1"))
        assertFalse(record(tbank, null, text, 1_000_000L + 60_000L, "key-2"))
    }

    // B-123: the operation time from the text, not the arrival, orders operations and picks the balance.

    @Test fun balanceFromLatestOperationTime() = runTest {
        val zone = ZoneId.of("Europe/Moscow")
        fun ms(h: Int, m: Int) = LocalDateTime.of(2026, 11, 3, h, m).atZone(zone).toInstant().toEpochMilli()
        val arrival = ms(17, 43)
        val events = FakeEvents(); val ledger = FakeLedger()
        val record = RecordNotification(events, ledger, direct, SampleCashRepository(null), zone)
        val alfa = "ru.alfabank.mobile.android"
        record(alfa, "Альфа-Банк", "Пополнение на 131 920,50 RUB Баланс 413 684,62 RUB 3 ноября 17:43", arrival)
        record(alfa, "Альфа-Банк", "Покупка 1 045 RUB Баланс 1 764,12 RUB Додо Пицца 3 ноября 17:39", arrival)
        record(alfa, "Альфа-Банк", "Пополнение на 280 000 RUB Баланс 281 764,12 RUB 3 ноября 17:42", arrival)

        assertEquals(setOf(ms(17, 39), ms(17, 42), ms(17, 43)), ledger.txs.map { it.postedAt }.toSet())
        assertEquals(arrival, events.stored.first().postedAt)
        var state: BalanceState? = null
        for ((balance, at) in ledger.upsertTimes) state = RunningBalance.apply(state, 0L, balance, at)
        assertEquals(41_368_462L, state!!.balanceMinor)
    }

    @Test fun lateArrivalWithEarlierOpTimeIsRecordedButKeepsBalance() = runTest {
        val zone = ZoneId.of("Europe/Moscow")
        fun ms(h: Int, m: Int) = LocalDateTime.of(2026, 11, 3, h, m).atZone(zone).toInstant().toEpochMilli()
        val events = FakeEvents(); val ledger = FakeLedger()
        val record = RecordNotification(events, ledger, direct, SampleCashRepository(null), zone)
        val alfa = "ru.alfabank.mobile.android"
        record(alfa, "Альфа-Банк", "Пополнение на 131 920,50 RUB Баланс 413 684,62 RUB 3 ноября 17:43", ms(17, 43))
        record(alfa, "Альфа-Банк", "Покупка 1 045 RUB Баланс 1 764,12 RUB Додо Пицца 3 ноября 17:39", ms(17, 50))

        assertEquals(2, ledger.txs.size)
        assertTrue(ledger.txs.any { it.postedAt == ms(17, 39) })
        var state: BalanceState? = null
        for ((balance, at) in ledger.upsertTimes) state = RunningBalance.apply(state, 0L, balance, at)
        assertEquals(41_368_462L, state!!.balanceMinor)
    }

    // ---- B-126: «сумма — Баланс» is a cash withdrawal when the balance fell by the amount ----

    private val sber = "ru.sberbankmobile"
    private val bare = "70 000 ₽ — Баланс: 9 821,66 ₽ Счёт карты МИР •• 0402"

    private fun sberAccount(ledger: FakeLedger, balance: Long) {
        ledger.accounts["sber:RUB"] = Account("sber:RUB", BankBrand.SBER, "Сбер", ru.maxrubtsoff.bankagg.domain.model.Money.ofMinor(balance), "RUB", 0L, checkedAt = 1L)
    }

    @Test fun recordWithdrawalDebitsAccountAndCreditsCash() = runTest {
        val events = FakeEvents(); val ledger = FakeLedger(); val cash = SampleCashRepository(null)
        sberAccount(ledger, 7_982_166L)
        val record = RecordNotification(events, ledger, direct, cash)
        assertTrue(record(sber, "Сбербанк", bare, 1_000_000L))
        assertEquals(982_166L, ledger.balances["sber:RUB"])
        assertEquals(-7_000_000L, ledger.txs.single().amount.minor)
        assertEquals("Снятие наличных", ledger.txs.single().counterparty)
        assertEquals(7_000_000L, cash.get()!!.amount.minor)
        assertEquals(1, cash.observeChanges().first().size)
        // The same push again: one operation, one cash entry.
        sberAccount(ledger, 982_166L)
        assertFalse(record(sber, "Сбербанк", bare, 1_000_000L + 30_000L))
        assertEquals(1, ledger.txs.size)
        assertEquals(7_000_000L, cash.get()!!.amount.minor)
    }

    @Test fun withdrawalUnknownBalanceIsLoggedButNotCounted() = runTest {
        val events = FakeEvents(); val ledger = FakeLedger(); val cash = SampleCashRepository(null)
        val record = RecordNotification(events, ledger, direct, cash)
        assertTrue(record(sber, "Сбербанк", bare, 1_000_000L))
        assertFalse(events.stored.single().parsed)
        assertTrue(ledger.txs.isEmpty())
        assertTrue(cash.observeChanges().first().isEmpty())
        assertFalse(record(sber, "Сбербанк", bare, 1_000_000L + 30_000L))
    }

    @Test fun withdrawalBalanceNotAddingUpIsNotCounted() = runTest {
        val events = FakeEvents(); val ledger = FakeLedger(); val cash = SampleCashRepository(null)
        sberAccount(ledger, 5_000_000L)
        RecordNotification(events, ledger, direct, cash)(sber, "Сбербанк", bare, 1L)
        assertTrue(ledger.txs.isEmpty())
        assertNull(cash.get())
    }
}
