package ru.maxrubtsoff.bankagg.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CapturedEvent
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.Transaction
import ru.maxrubtsoff.bankagg.domain.model.TransactionKind
import ru.maxrubtsoff.bankagg.domain.repository.EventRepository
import ru.maxrubtsoff.bankagg.domain.repository.LedgerRepository
import ru.maxrubtsoff.bankagg.domain.repository.TransactionRunner
import ru.maxrubtsoff.bankagg.domain.usecase.RecordNotification
import ru.maxrubtsoff.bankagg.domain.usecase.RepairFromJournal

/**
 * The log keeps the raw text of every notification, so an improved parser fixes
 * what the old one got wrong — without the user editing anything (B-45, B-46).
 */
class RepairFromJournalTest {

    private val alfa = "ru.alfabank.mobile.android"
    private val brand = BankBrand.fromPackage(alfa)
    private val account = "${brand.key}:RUB"

    private class FakeEvents(events: List<CapturedEvent>) : EventRepository {
        val stored = events.toMutableList()
        override suspend fun record(event: CapturedEvent) = stored.add(event)
        override suspend fun hasSimilar(contentHash: String, from: Long, to: Long, notificationKey: String?) = false
        override fun observeRecent(limit: Int): Flow<List<CapturedEvent>> = emptyFlow()
        override suspend fun trim(parsedBefore: Long, unparsedBefore: Long) = 0
        override suspend fun all(): List<CapturedEvent> = stored.toList()
        override suspend fun updateParse(id: Long, parsed: Boolean, dedupHash: String, contentHash: String) {
            val i = stored.indexOfFirst { it.id == id }
            stored[i] = stored[i].copy(parsed = parsed, dedupHash = dedupHash, contentHash = contentHash)
        }
    }

    private class FakeLedger(
        val accounts: MutableMap<String, Account> = mutableMapOf(),
        val txs: MutableList<Transaction> = mutableListOf()
    ) : LedgerRepository {
        override suspend fun upsertAccountBalance(
            accountId: String, brand: BankBrand, currency: String,
            amountMinor: Long, balanceMinor: Long?, postedAt: Long
        ) = Unit
        override suspend fun setBalance(accountId: String, balanceMinor: Long, at: Long) = Unit
        override suspend fun sumSince(accountId: String, from: Long) =
            txs.filter { it.accountId == accountId && it.postedAt > from }.sumOf { it.amount.minor }
        override suspend fun addTransaction(tx: Transaction): Boolean = txs.add(tx)
        override fun observeAccounts(): Flow<List<Account>> = emptyFlow()
        override suspend fun setExcluded(accountId: String, excluded: Boolean) = Unit
        override fun observeTransactions(limit: Int): Flow<List<Transaction>> = emptyFlow()
        override suspend fun clear() {}
        override suspend fun account(accountId: String): Account? = accounts[accountId]
        override suspend fun transaction(id: String): Transaction? = txs.firstOrNull { it.id == id }
        override suspend fun deleteTransaction(id: String) { txs.removeAll { it.id == id } }
        override suspend fun setCountedBalance(accountId: String, balanceMinor: Long, estimated: Boolean) {
            accounts[accountId]?.let {
                accounts[accountId] = it.copy(balance = Money.ofMinor(balanceMinor), estimated = estimated)
            }
        }
    }

    private val direct = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }

    /** An event as the old parser stored it, with the operation it then produced. */
    private fun stored(
        id: Long,
        text: String,
        postedAt: Long,
        oldAmountMinor: Long?,
        oldBalanceMinor: Long? = null
    ): Pair<CapturedEvent, Transaction?> {
        val content = contentHash(text, oldAmountMinor, oldBalanceMinor)
        val hash = "$content|$postedAt"
        val event = CapturedEvent(
            id = id, packageName = alfa, brand = brand, title = brand.title, text = text,
            postedAt = postedAt, dedupHash = hash, parsed = oldAmountMinor != null, contentHash = content
        )
        val tx = oldAmountMinor?.let {
            Transaction(
                id = hash, brand = brand, accountId = "${brand.key}:RUB", amount = Money.ofMinor(it),
                currency = "RUB", kind = TransactionKind.UNKNOWN, category = null,
                counterparty = null, postedAt = postedAt
            )
        }
        return event to tx
    }

    private fun contentHash(text: String, amountMinor: Long?, balanceMinor: Long?) =
        RecordNotification.contentHash(alfa, text, amountMinor, balanceMinor)

    @Test fun outgoing_transfer_recorded_as_income_getsFixed() = runTest {
        val (event, tx) = stored(
            id = 1,
            text = "Списание со счета 4*1833; Перевод на сумму 5 000,00 RUB; Получатель: 798*0842",
            postedAt = 2_000L,
            oldAmountMinor = 500_000L
        )
        val ledger = FakeLedger(
            accounts = mutableMapOf(
                account to Account(
                    id = account, brand = brand, title = brand.title,
                    balance = Money.ofMinor(10_326_255L), currency = "RUB", updatedAt = 2_000L,
                    checkedAt = 1_000L, estimated = true
                )
            ),
            txs = mutableListOf(tx!!)
        )
        val events = FakeEvents(listOf(event))

        val fixed = RepairFromJournal(events, ledger, direct)()

        assertEquals(1, fixed)
        assertEquals(-500_000L, ledger.txs.single().amount.minor)
        // 98 262,55 checked + (−5 000) counted on top, instead of +5 000.
        assertEquals(9_326_255L, ledger.accounts[account]!!.balance.minor)
        assertTrue(ledger.accounts[account]!!.estimated)
        assertTrue(events.stored.single().parsed)
    }

    @Test fun advertisement_recorded_as_income_isRemoved() = runTest {
        val (event, tx) = stored(
            id = 7,
            text = "Всё просто. Ставьте бочонки на карточки. Закроете одну — и вы в розыгрыше 100 000 рублей",
            postedAt = 2_000L,
            oldAmountMinor = 10_000_000L
        )
        val ledger = FakeLedger(
            accounts = mutableMapOf(
                account to Account(
                    id = account, brand = brand, title = brand.title,
                    balance = Money.ofMinor(10_651_936L), currency = "RUB", updatedAt = 2_000L,
                    checkedAt = 1_000L, estimated = true
                )
            ),
            txs = mutableListOf(tx!!)
        )
        val events = FakeEvents(listOf(event))

        assertEquals(1, RepairFromJournal(events, ledger, direct)())

        assertTrue(ledger.txs.isEmpty())
        assertEquals(651_936L, ledger.accounts[account]!!.balance.minor)
        assertFalse(ledger.accounts[account]!!.estimated)
        assertFalse(events.stored.single().parsed)
    }

    @Test fun operation_the_parser_still_reads_the_same_isLeftAlone() = runTest {
        val (event, tx) = stored(
            id = 3,
            text = "Покупка 2 000,50 RUR в YANDEX.TAXI. Баланс: 9 999,50 RUR",
            postedAt = 5_000L,
            oldAmountMinor = -200_050L,
            oldBalanceMinor = 999_950L
        )
        val ledger = FakeLedger(txs = mutableListOf(tx!!))
        val events = FakeEvents(listOf(event))

        assertEquals(0, RepairFromJournal(events, ledger, direct)())
        assertEquals(tx, ledger.txs.single())
    }

    @Test fun account_without_a_checked_balance_keepsItsBalance() = runTest {
        val (event, tx) = stored(
            id = 4,
            text = "Перевод 3 000 ₽ Ивану Петрову",
            postedAt = 2_000L,
            oldAmountMinor = 300_000L
        )
        val ledger = FakeLedger(
            accounts = mutableMapOf(
                account to Account(
                    id = account, brand = brand, title = brand.title, balance = Money.ofMinor(0L),
                    currency = "RUB", updatedAt = 2_000L, checkedAt = null, estimated = false
                )
            ),
            txs = mutableListOf(tx!!)
        )
        val events = FakeEvents(listOf(event))

        assertEquals(1, RepairFromJournal(events, ledger, direct)())
        assertEquals(-300_000L, ledger.txs.single().amount.minor)
        assertEquals(0L, ledger.accounts[account]!!.balance.minor)
        assertNull(ledger.accounts[account]!!.checkedAt)
    }
}
