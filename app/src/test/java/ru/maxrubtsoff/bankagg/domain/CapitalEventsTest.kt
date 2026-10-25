package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CapitalEvents
import ru.maxrubtsoff.bankagg.domain.model.CashBalance
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.model.DepositTopUp
import ru.maxrubtsoff.bankagg.domain.model.HistoryKind
import ru.maxrubtsoff.bankagg.domain.model.HistoryRow
import ru.maxrubtsoff.bankagg.domain.model.HistoryTarget
import ru.maxrubtsoff.bankagg.domain.model.Transaction
import ru.maxrubtsoff.bankagg.domain.model.TransactionKind
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CapitalPoint
import ru.maxrubtsoff.bankagg.domain.model.FxKind
import ru.maxrubtsoff.bankagg.domain.model.FxOperation
import ru.maxrubtsoff.bankagg.domain.model.FxRate
import ru.maxrubtsoff.bankagg.domain.model.HistoryEvent
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.RevaluationText
import java.time.LocalDate
import java.time.ZoneOffset

class CapitalEventsTest {

    private val today = LocalDate.of(2026, 10, 25)
    private val d0 = today.minusDays(2)
    private val d1 = today.minusDays(1)

    private fun pointFx(day: LocalDate, fx: Long) =
        CapitalPoint(day, fx, mapOf(CapitalPart.CURRENCY to fx))

    private fun buy(cur: String, day: LocalDate, qty: Long, rub: Long, id: String = cur + day) =
        FxOperation(id, cur, FxKind.BUY, day, qty, rub, 0L)

    private fun events(points: List<CapitalPoint>, ops: List<FxOperation>, rates: List<FxRate>): List<HistoryEvent> =
        CapitalEvents.build(emptyList(), emptyList(), ops, emptyList(), null, points, today, ZoneOffset.UTC, rates)

    private fun reval(list: List<HistoryEvent>) = list.single { it.title == "Переоценка валюты" }

    @Test fun revaluationExplainedByRate() {
        val e = reval(
            events(
                listOf(pointFx(d0, 9_000_000), pointFx(d1, 9_250_000)),
                listOf(buy("USD", d0, 100_000, 9_000_000)),
                listOf(FxRate("USD", d0, 90_000_000), FxRate("USD", d1, 92_500_000))
            )
        )
        val r = e.reason!!
        assertEquals(1, r.legs.size)
        assertEquals(250_000L, r.legs[0].effectMinor)
        assertEquals(0L, r.manualMinor)
        assertEquals("USD 90,00 → 92,50 ₽ · " + Money.ofMinor(100_000).format("USD"), e.subtitle)
    }

    @Test fun revaluationWithManualRemainder() {
        val e = reval(
            events(
                listOf(pointFx(d0, 9_000_000), pointFx(d1, 9_750_000)),
                listOf(buy("USD", d0, 100_000, 9_000_000)),
                listOf(FxRate("USD", d0, 90_000_000), FxRate("USD", d1, 92_500_000))
            )
        )
        assertEquals(500_000L, e.reason!!.manualMinor)
        assertTrue(e.subtitle!!.endsWith("остаток изменили вручную"))
    }

    @Test fun revaluationWithoutRatesFallsBackToManual() {
        val e = reval(events(listOf(pointFx(d0, 100), pointFx(d1, 90_000)), emptyList(), emptyList()))
        assertTrue(e.reason!!.legs.isEmpty())
        assertEquals("Курс или остаток изменились", e.subtitle)
    }

    @Test fun revaluationTwoCurrencies() {
        val e = reval(
            events(
                listOf(pointFx(d0, 18_000_000), pointFx(d1, 18_600_000)),
                listOf(buy("USD", d0, 100_000, 9_000_000), buy("EUR", d0, 100_000, 9_000_000)),
                listOf(
                    FxRate("USD", d0, 90_000_000), FxRate("USD", d1, 92_000_000),
                    FxRate("EUR", d0, 90_000_000), FxRate("EUR", d1, 94_000_000)
                )
            )
        )
        assertEquals(2, e.reason!!.legs.size)
        assertEquals("Курс USD, EUR", e.subtitle)
    }

    @Test fun rateFormat() {
        assertEquals("91,20", RevaluationText.formatRate(91_200_000))
        assertEquals("92,50", RevaluationText.formatRate(92_495_000))
    }

    @Test fun dealtIsNotCountedTwice() {
        // The buy on d1 itself explains the growth; rates unchanged -> no revaluation line.
        val list = events(
            listOf(pointFx(d0, 0), pointFx(d1, 9_000_000)),
            listOf(buy("USD", d1, 100_000, 9_000_000)),
            listOf(FxRate("USD", d0, 90_000_000), FxRate("USD", d1, 90_000_000))
        )
        assertNull(list.firstOrNull { it.title == "Переоценка валюты" })
    }

    // ---- F-44: where a tap leads ----

    private fun dep(id: String, start: LocalDate = d0.minusDays(5), end: LocalDate = today.plusDays(100), up: Boolean = false) =
        Deposit(id, BankBrand.SBER, null, Money.ofMinor(100_000), 1600, start, end,
            topUps = if (up) listOf(DepositTopUp("u$id", d1, Money.ofMinor(5_000))) else emptyList())

    private fun build(
        deps: List<Deposit> = emptyList(), tx: List<Transaction> = emptyList(), ops: List<FxOperation> = emptyList(),
        points: List<CapitalPoint> = emptyList(), rates: List<FxRate> = emptyList(), cash: CashBalance? = null
    ) = CapitalEvents.build(tx, emptyList(), ops, deps, cash, points, today, ZoneOffset.UTC, rates)

    private fun txOf(cur: String) = Transaction(
        "t", BankBrand.SBER, "a", Money.ofMinor(100), cur, TransactionKind.INCOME, null, null,
        d1.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    )

    @Test fun depositEventsPointToDeposit() {
        val list = build(deps = listOf(dep("x", start = d0, end = d1, up = true)))
        for (k in listOf(HistoryKind.DEPOSIT_OPEN, HistoryKind.DEPOSIT_TOPUP, HistoryKind.DEPOSIT_CLOSE)) {
            // close is only listed once the end is before today
            assertEquals(HistoryTarget.Deposit("x"), list.single { it.kind == k }.target)
        }
    }

    @Test fun rubTransactionPointsToAccounts() {
        assertEquals(HistoryTarget.Section(CapitalPart.ACCOUNTS), build(tx = listOf(txOf("RUB"))).single().target)
    }

    @Test fun fxTransactionPointsToCurrency() {
        assertEquals(HistoryTarget.Currency("USD"), build(tx = listOf(txOf("USD"))).single().target)
    }

    @Test fun fxDealPointsToCurrency() {
        val e = build(ops = listOf(buy("EUR", d0, 100, 1000))).single()
        assertEquals(HistoryKind.FX_DEAL, e.kind)
        assertEquals(HistoryTarget.Currency("EUR"), e.target)
    }

    private fun depPoints(v1: Long) = listOf(
        CapitalPoint(d0, 100, mapOf(CapitalPart.DEPOSITS to 100L)),
        CapitalPoint(d1, v1, mapOf(CapitalPart.DEPOSITS to v1))
    )

    @Test fun interestSingleDepositTargetsDeposit() {
        val e = build(deps = listOf(dep("x", end = today.plusDays(10))), points = depPoints(110)).single { it.kind == HistoryKind.INTEREST }
        assertEquals(HistoryTarget.Deposit("x"), e.target)
    }

    @Test fun interestManyDepositsTargetsSection() {
        val e = build(deps = listOf(dep("x"), dep("y")), points = depPoints(110)).single { it.kind == HistoryKind.INTEREST }
        assertEquals(HistoryTarget.Section(CapitalPart.DEPOSITS), e.target)
    }

    @Test fun revaluationTargetsLargestLeg() {
        val e = reval(
            events(
                listOf(pointFx(d0, 18_000_000), pointFx(d1, 18_600_000)),
                listOf(buy("USD", d0, 100_000, 9_000_000), buy("EUR", d0, 100_000, 9_000_000)),
                listOf(
                    FxRate("USD", d0, 90_000_000), FxRate("USD", d1, 92_000_000),
                    FxRate("EUR", d0, 90_000_000), FxRate("EUR", d1, 94_000_000)
                )
            )
        )
        assertEquals(HistoryTarget.Currency("EUR"), e.target)
    }

    @Test fun isAliveFalseForDeletedDeposit() {
        assertTrue(CapitalEvents.isAlive(HistoryTarget.Deposit("x"), listOf(dep("x"))))
        assertFalse(CapitalEvents.isAlive(HistoryTarget.Deposit("x"), emptyList()))
        assertTrue(CapitalEvents.isAlive(HistoryTarget.Section(CapitalPart.CASH), emptyList()))
    }

    // ---- F-39: grouping, own transfers ----

    private val accA = Account("a", BankBrand.SBER, "Сбер", Money.ofMinor(0), "RUB", 0L)
    private val accB = Account("b", BankBrand.SBER, "Тинькофф", Money.ofMinor(0), "RUB", 0L)

    private fun op(id: String, acc: String, minor: Long, kind: TransactionKind, day: LocalDate = d1, who: String? = null) =
        Transaction(id, BankBrand.SBER, acc, Money.ofMinor(minor), "RUB", kind, null, who, day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli())

    private fun eventsOf(vararg t: Transaction) =
        CapitalEvents.build(t.toList(), kotlin.collections.listOf(accA, accB), emptyList(), emptyList(), null, emptyList(), today, ZoneOffset.UTC)

    @Test fun ownTransferPairIsHidden() {
        val e = eventsOf(op("1", "a", -5000, TransactionKind.TRANSFER), op("2", "b", 5000, TransactionKind.TRANSFER))
        assertTrue(e.isEmpty())
    }

    @Test fun transferToStrangerStays() {
        val e = eventsOf(op("1", "a", -5000, TransactionKind.TRANSFER, who = "Иван"))
        assertEquals(1, e.size)
        assertEquals(HistoryKind.TRANSFER, e[0].kind)
    }

    @Test fun differentDayTransferNotPaired() {
        assertEquals(2, listOf(op("1", "a", -5000, TransactionKind.TRANSFER), op("2", "b", 5000, TransactionKind.TRANSFER, day = d0)).size)
    }

    @Test fun differentAmountNotPaired() {
        assertEquals(2, listOf(op("1", "a", -5000, TransactionKind.TRANSFER), op("2", "b", 4000, TransactionKind.TRANSFER)).size)
    }

    @Test fun sameAccountNotPaired() {
        assertEquals(2, listOf(op("1", "a", -5000, TransactionKind.TRANSFER), op("2", "a", 5000, TransactionKind.TRANSFER)).size)
    }

    @Test fun twoEqualPairsPairedOneToOne() {
        val e = eventsOf(
            op("1", "a", -5000, TransactionKind.TRANSFER), op("2", "b", 5000, TransactionKind.TRANSFER),
            op("3", "a", -5000, TransactionKind.TRANSFER), op("4", "b", 5000, TransactionKind.TRANSFER),
            op("5", "a", -5000, TransactionKind.TRANSFER)
        )
        assertEquals(1, e.size)
    }

    @Test fun spendGroupedPerDay() {
        val rows = CapitalEvents.group(eventsOf(op("1", "a", -100, TransactionKind.EXPENSE), op("2", "a", -300, TransactionKind.EXPENSE)))
        val g = rows.single() as HistoryRow.Group
        assertEquals(2, g.count)
        assertEquals(-400L, g.totalMinor)
        assertEquals("Списания · 2", g.title)
    }

    @Test fun singleSpendStaysSingle() {
        assertTrue(CapitalEvents.group(eventsOf(op("1", "a", -100, TransactionKind.EXPENSE))).single() is HistoryRow.Single)
    }

    @Test fun spendAndTransferGroupedSeparately() {
        val rows = CapitalEvents.group(
            eventsOf(
                op("1", "a", -100, TransactionKind.EXPENSE), op("2", "a", -200, TransactionKind.EXPENSE),
                op("3", "a", -300, TransactionKind.TRANSFER, who = "Иван"), op("4", "a", -400, TransactionKind.TRANSFER, who = "Анна")
            )
        )
        assertEquals(kotlin.collections.listOf(HistoryKind.SPEND, HistoryKind.TRANSFER), rows.map { (it as HistoryRow.Group).kind })
    }

    @Test fun incomeNeverGrouped() {
        val rows = CapitalEvents.group(eventsOf(op("1", "a", 100, TransactionKind.INCOME), op("2", "a", 200, TransactionKind.INCOME)))
        assertEquals(2, rows.size)
        assertTrue(rows.all { it is HistoryRow.Single })
    }

    @Test fun groupIdIsStableByDayAndKind() {
        val g = CapitalEvents.group(eventsOf(op("1", "a", -100, TransactionKind.EXPENSE), op("2", "a", -300, TransactionKind.EXPENSE))).single() as HistoryRow.Group
        assertEquals("${d1.toEpochDay()}:SPEND", g.id)
    }

    // ---- F-41: the day nearest to the picked one ----

    private fun ev(day: LocalDate) = HistoryEvent(day, CapitalPart.ACCOUNTS, "t", null, 1L)

    @Test fun nearestDayExactMatch() {
        assertEquals(d1, CapitalEvents.nearestDay(listOf(ev(d0), ev(d1)), d1))
    }

    @Test fun nearestDayPicksClosest() {
        assertEquals(d0, CapitalEvents.nearestDay(listOf(ev(d0.minusDays(10)), ev(d0)), today))
    }

    @Test fun nearestDayTieGoesToEarlier() {
        val mid = today.minusDays(5)
        assertEquals(mid.minusDays(2), CapitalEvents.nearestDay(listOf(ev(mid.plusDays(2)), ev(mid.minusDays(2))), mid))
    }

    @Test fun nearestDayEmptyIsNull() {
        assertNull(CapitalEvents.nearestDay(emptyList(), today))
    }

    @Test fun monthKeyOfNearestDay() {
        val day = CapitalEvents.nearestDay(listOf(ev(LocalDate.of(2026, 8, 31)), ev(LocalDate.of(2026, 7, 1))), LocalDate.of(2026, 9, 2))!!
        assertEquals("2026-08", java.time.YearMonth.from(day).toString())
    }
}
