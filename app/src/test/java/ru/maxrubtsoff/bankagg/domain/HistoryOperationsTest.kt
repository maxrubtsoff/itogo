package ru.maxrubtsoff.bankagg.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleCapitalHistory
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleCashRepository
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleDepositRepository
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleFxRepository
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleLedgerRepository
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleRatesRepository
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleSettings
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CapitalPoint
import ru.maxrubtsoff.bankagg.domain.model.CapturedEvent
import ru.maxrubtsoff.bankagg.domain.model.CashBalance
import ru.maxrubtsoff.bankagg.domain.model.CashChange
import ru.maxrubtsoff.bankagg.domain.model.Features
import ru.maxrubtsoff.bankagg.domain.model.FxKind
import ru.maxrubtsoff.bankagg.domain.model.FxOperation
import ru.maxrubtsoff.bankagg.domain.model.HistoryEvent
import ru.maxrubtsoff.bankagg.domain.model.HistoryRef
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.OperationForm
import ru.maxrubtsoff.bankagg.domain.model.Transaction
import ru.maxrubtsoff.bankagg.domain.model.TransactionKind
import ru.maxrubtsoff.bankagg.domain.repository.EventRepository
import ru.maxrubtsoff.bankagg.domain.repository.Today
import ru.maxrubtsoff.bankagg.domain.repository.TransactionRunner
import ru.maxrubtsoff.bankagg.domain.usecase.GetCapital
import ru.maxrubtsoff.bankagg.domain.usecase.HistoryOperations
import java.time.LocalDate
import java.time.ZoneOffset

/** F-51: deleting, restoring and correcting history records, on in-memory repositories. */
class HistoryOperationsTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 10, 20)
    private fun ms(d: LocalDate, hour: Int = 12) = d.atStartOfDay(zone).toInstant().toEpochMilli() + hour * 3_600_000L
    private val d14 = LocalDate.of(2026, 10, 14)
    private val d17 = LocalDate.of(2026, 10, 17)
    private val d18 = LocalDate.of(2026, 10, 18)
    private val d19 = LocalDate.of(2026, 10, 19)

    private class Events(val stored: List<CapturedEvent> = emptyList()) : EventRepository {
        override suspend fun record(event: CapturedEvent) = false
        override suspend fun hasSimilar(contentHash: String, from: Long, to: Long, notificationKey: String?) = false
        override fun observeRecent(limit: Int): Flow<List<CapturedEvent>> = emptyFlow()
        override suspend fun trim(parsedBefore: Long, unparsedBefore: Long) = 0
        override suspend fun byHash(hash: String) = stored.firstOrNull { it.dedupHash == hash }
        override suspend fun all() = stored
        override suspend fun updateParse(id: Long, parsed: Boolean, dedupHash: String, contentHash: String) = Unit
    }

    private inner class World(
        accounts: List<Account> = emptyList(),
        txs: List<Transaction> = emptyList(),
        startCash: CashBalance? = null,
        fxOps: List<FxOperation> = emptyList(),
        events: List<CapturedEvent> = emptyList()
    ) {
        val ledger = SampleLedgerRepository(accounts, txs)
        val cash = SampleCashRepository(startCash)
        val fx = SampleFxRepository(fxOps)
        val history = SampleCapitalHistory()
        val ops: HistoryOperations

        init {
            val rates = SampleRatesRepository(emptyList())
            val deposits = SampleDepositRepository(emptyList())
            val settings = SampleSettings(Features.ALL)
            val day = object : Today { override val date = MutableStateFlow(today) }
            val getCapital = GetCapital(ledger, deposits, fx, rates, cash, settings, day)
            val direct = object : TransactionRunner { override suspend fun <T> run(block: suspend () -> T): T = block() }
            ops = HistoryOperations(ledger, Events(events), cash, fx, deposits, history, rates, settings, getCapital, direct, day, zone)
        }

        suspend fun seed(vararg p: CapitalPoint) = history.upsertAll(p.toList())
        suspend fun days() = history.days().associateBy { it.date }
        suspend fun balance(id: String) = ledger.account(id)!!.balance.minor
    }

    private fun account(id: String, balance: Long, checkedAt: Long) =
        Account(id, BankBrand.SBER, id, Money.ofMinor(balance), "RUB", 0L, checkedAt = checkedAt, estimated = true)

    private fun tx(amount: Long, at: Long) = Transaction(
        "t", BankBrand.SBER, "a", Money.ofMinor(amount), "RUB",
        if (amount >= 0) TransactionKind.INCOME else TransactionKind.EXPENSE, null, null, at
    )

    private fun point(d: LocalDate, accounts: Long, cash: Long = 0L) =
        CapitalPoint(d, accounts + cash, mapOf(CapitalPart.ACCOUNTS to accounts, CapitalPart.CASH to cash))

    private fun accountsWorld() = World(listOf(account("a", 15_000, ms(d14))), listOf(tx(5_000, ms(d18))))

    @Test fun `deleting an operation and taking it back restores the balance and the chart`() = runTest {
        val w = accountsWorld()
        w.seed(point(d14, 10_000), point(d17, 10_000), point(d19, 15_000))
        val ref = HistoryRef.Tx("t")

        val removed = w.ops.delete(listOf(ref))
        assertEquals(10_000, w.balance("a"))
        assertNull(w.ledger.transaction("t"))
        var days = w.days()
        assertEquals(10_000L, days.getValue(d19).parts[CapitalPart.ACCOUNTS])
        assertEquals(10_000L, days.getValue(d19).totalMinor)
        assertEquals(10_000L, days.getValue(d17).totalMinor)

        w.ops.restore(removed)
        assertEquals(15_000, w.balance("a"))
        assertNotNull(w.ledger.transaction("t"))
        days = w.days()
        assertEquals(15_000L, days.getValue(d19).totalMinor)
        assertEquals(10_000L, days.getValue(d17).totalMinor)
        assertEquals(10_000L, days.getValue(d14).totalMinor)
    }

    @Test fun `an operation inside the banks balance leaves the balance and the chart alone`() = runTest {
        val w = World(listOf(account("a", 15_000, ms(d18)).copy(estimated = false)), listOf(tx(5_000, ms(d18))))
        w.seed(point(d17, 15_000), point(d19, 15_000))
        val p = w.ops.preview(listOf(HistoryRef.Tx("t")))
        assertEquals(p.partWas, p.partNow)
        assertNull(p.from)
        w.ops.delete(listOf(HistoryRef.Tx("t")))
        assertEquals(15_000, w.balance("a"))
        assertEquals(15_000L, w.days().getValue(d19).totalMinor)
    }

    @Test fun `moving an operation to another account moves the balances, not the capital`() = runTest {
        val w = World(listOf(account("a", 15_000, ms(d14)), account("b", 7_000, ms(d14))), listOf(tx(5_000, ms(d18))))
        w.seed(point(d17, 17_000), point(d19, 22_000))
        assertNull(w.ops.save(HistoryRef.Tx("t"), OperationForm(true, 5_000, d18, accountId = "b")))
        assertEquals(10_000, w.balance("a"))
        assertEquals(12_000, w.balance("b"))
        assertEquals("b", w.ledger.transaction("t")!!.accountId)
        assertEquals(22_000L, w.days().getValue(d19).totalMinor)
    }

    @Test fun `correcting the sum and the direction follows the balance and the chart`() = runTest {
        val w = accountsWorld()
        w.seed(point(d17, 10_000), point(d19, 15_000))
        assertNull(w.ops.save(HistoryRef.Tx("t"), OperationForm(true, 8_000, d18, accountId = "a")))
        assertEquals(18_000, w.balance("a"))
        assertEquals(18_000L, w.days().getValue(d19).totalMinor)
        // Income turned into an expense: from +8 000 to -8 000.
        assertNull(w.ops.save(HistoryRef.Tx("t"), OperationForm(false, 8_000, d18, accountId = "a")))
        assertEquals(2_000, w.balance("a"))
        assertEquals(TransactionKind.EXPENSE, w.ledger.transaction("t")!!.kind)
        assertEquals(2_000L, w.days().getValue(d19).totalMinor)
    }

    @Test fun `a form with no sum or a future date is refused`() = runTest {
        val w = accountsWorld()
        assertEquals("Введите сумму", w.ops.save(HistoryRef.Tx("t"), OperationForm(true, 0, d18)))
        assertNotNull(w.ops.save(HistoryRef.Tx("t"), OperationForm(true, 100, today.plusDays(1))))
        assertEquals(15_000, w.balance("a"))
    }

    private val take = CashChange(ms(d18, 10), Money.ofMinor(7_000), Money.ofMinor(-3_000))
    private val back = CashChange(ms(d18, 15), Money.ofMinor(10_000), Money.ofMinor(3_000))

    /** Cash of 10 000 made by two edits on one day: took 3 000, gave 3 000 back. */
    private suspend fun cashWorld(): World {
        val w = World(startCash = CashBalance(Money.ofMinor(10_000), 0L))
        w.cash.addChange(take)
        w.cash.addChange(back)
        return w
    }

    @Test fun `correcting a cash edit removes the old one and adds the new`() = runTest {
        val w = cashWorld()
        w.seed(point(d17, 0, 10_000), point(d19, 0, 10_000))
        assertNull(w.ops.save(HistoryRef.Cash(take.atMs), OperationForm(false, 2_000, d18)))
        val changes = w.cash.observeChanges().first().sortedBy { it.atMs }
        assertEquals(listOf(-2_000L, 3_000L), changes.map { it.delta.minor })
        // Took 2 000 instead of 3 000: 1 000 more in the cash, on the chart from the day of the edit.
        assertEquals(11_000L, w.cash.get()!!.amount.minor)
        assertEquals(11_000L, w.days().getValue(d19).parts[CapitalPart.CASH])
        assertEquals(10_000L, w.days().getValue(d17).parts[CapitalPart.CASH])
    }

    @Test fun `took and gave back is deleted as a pair and comes back whole`() = runTest {
        val w = cashWorld()
        w.seed(point(d17, 0, 10_000), point(d19, 0, 10_000))
        val refs = listOf(HistoryRef.Cash(take.atMs), HistoryRef.Cash(back.atMs))
        val p = w.ops.preview(refs)
        assertEquals(CapitalPart.CASH, p.part)
        assertEquals(p.partWas, p.partNow)
        val removed = w.ops.delete(refs)
        assertTrue(w.cash.observeChanges().first().isEmpty())
        assertEquals(10_000L, w.cash.get()!!.amount.minor)
        w.ops.restore(removed)
        assertEquals(2, w.cash.observeChanges().first().size)
        assertEquals(10_000L, w.cash.get()!!.amount.minor)
        assertEquals(10_000L, w.days().getValue(d19).parts[CapitalPart.CASH])
    }

    @Test fun `one cash edit taken out and put back leaves the same amount`() = runTest {
        val w = World(startCash = CashBalance(Money.ofMinor(7_000), 0L))
        w.cash.addChange(take.copy(delta = Money.ofMinor(3_000)))
        assertEquals(10_000L, w.cash.get()!!.amount.minor)
        val ref = HistoryRef.Cash(take.atMs)
        val removed = w.ops.delete(listOf(ref))
        assertEquals(7_000L, w.cash.get()!!.amount.minor)
        w.ops.restore(removed)
        assertEquals(10_000L, w.cash.get()!!.amount.minor)
        assertEquals(1, w.cash.observeChanges().first().size)
    }

    @Test fun `a cash edit that would take the amount below zero stays`() = runTest {
        // The amount is 1 235 while the log holds an edit of +15 000: taking it out would leave -13 765.
        val w = World(startCash = CashBalance(Money.ofMinor(0), 0L))
        w.cash.addChange(CashChange(ms(d18, 10), Money.ofMinor(1_500_000), Money.ofMinor(1_500_000)))
        w.cash.set(CashBalance(Money.ofMinor(123_500), ms(d18, 12)))
        val ref = HistoryRef.Cash(ms(d18, 10))
        assertNotNull(w.ops.check(listOf(ref)))
        val removed = w.ops.delete(listOf(ref))
        assertEquals(123_500L, w.cash.get()!!.amount.minor)
        assertEquals(1, w.cash.observeChanges().first().count { it.atMs == ms(d18, 10) })
        w.ops.restore(removed)
        assertEquals(123_500L, w.cash.get()!!.amount.minor)
        // Correcting it down to a sum the amount can bear is refused the same way.
        assertNotNull(w.ops.save(ref, OperationForm(false, 1_500_000, d18)))
    }

    @Test fun `the detail of an operation carries the banks text with the sum marked`() = runTest {
        val text = "Поступление 5 000 ₽ на счёт"
        val w = World(
            listOf(account("a", 15_000, ms(d14))), listOf(tx(500_000, ms(d18))),
            events = listOf(CapturedEvent(1, "pkg", BankBrand.SBER, null, text, ms(d18), "t"))
        )
        val event = HistoryEvent(d18, CapitalPart.ACCOUNTS, "Пополнение", null, 500_000, ref = HistoryRef.Tx("t"))
        val d = w.ops.detail(event)
        assertEquals(text, d.note)
        assertEquals("5 000 ₽", text.substring(d.noteMark!!))
        assertEquals(true, d.form!!.income)
        assertEquals(500_000L, d.form!!.amountMinor)
    }

    private val buy = FxOperation("b1", "USD", FxKind.BUY, d14, 10_000, 800_000, 0L)
    private val sell = FxOperation("s1", "USD", FxKind.SELL, d17, 5_000, 410_000, 0L)

    @Test fun `a purchase that a later sale rests on is not deleted`() = runTest {
        val w = World(fxOps = listOf(buy, sell))
        assertNotNull(w.ops.check(listOf(HistoryRef.Fx("b1"))))
        assertNull(w.ops.check(listOf(HistoryRef.Fx("s1"))))
        // The use case holds the rule itself, not only the sheet that asks first.
        w.ops.delete(listOf(HistoryRef.Fx("b1")))
        assertEquals(2, w.fx.all().size)
        // The sale itself may go, and comes back.
        val gone = w.ops.delete(listOf(HistoryRef.Fx("s1")))
        assertEquals(listOf("b1"), w.fx.all().map { it.id })
        w.ops.restore(gone)
        assertEquals(2, w.fx.all().size)
    }

    @Test fun `a sale larger than the stock is refused on save`() = runTest {
        val w = World(fxOps = listOf(buy, sell))
        val err = w.ops.save(HistoryRef.Fx("s1"), OperationForm(false, 20_000, d17, rubMinor = 410_000))
        assertNotNull(err)
        assertEquals(5_000L, w.fx.get("s1")!!.amountMinor)
    }
}
