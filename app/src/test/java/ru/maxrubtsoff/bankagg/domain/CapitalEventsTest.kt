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
import ru.maxrubtsoff.bankagg.domain.model.DepositMath
import ru.maxrubtsoff.bankagg.domain.model.DepositTopUp
import ru.maxrubtsoff.bankagg.domain.model.HistoryKind
import ru.maxrubtsoff.bankagg.domain.model.HistoryRow
import ru.maxrubtsoff.bankagg.domain.model.HistoryRows
import ru.maxrubtsoff.bankagg.domain.model.RowKind
import ru.maxrubtsoff.bankagg.domain.model.RuPlural
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
        assertEquals("$ 90,00 → 92,50 ₽", e.subtitle)
        assertTrue(e.parts.isEmpty())
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
        assertTrue(e.subtitle!!.endsWith(" · остаток изменён"))
        assertEquals(2, e.parts.size)
        assertNull(e.parts[1].target)
        assertEquals(500_000L, e.parts[1].amountMinor)
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
        assertEquals("$ +2,00 ₽ · € +4,00 ₽", e.subtitle)
        assertEquals(listOf(HistoryTarget.Currency("USD"), HistoryTarget.Currency("EUR")), e.parts.map { it.target })
        assertEquals(e.amountMinor, e.parts.sumOf { it.amountMinor!! })
        assertEquals("90,00 → 92,00 ₽ · " + Money.ofMinor(100_000).format("USD"), e.parts[0].subtitle)
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

    @Test fun rubleOperationsLeadNowhere() {
        assertNull(build(tx = listOf(txOf("RUB"))).single().target)
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

    @Test fun interestPartsLeadToEachDeposit() {
        val e = build(deps = listOf(dep("x"), dep("y")), points = depPoints(110)).single { it.kind == HistoryKind.INTEREST }
        assertNull(e.target)
        assertEquals(listOf(HistoryTarget.Deposit("x"), HistoryTarget.Deposit("y")), e.parts.map { it.target })
        assertEquals(BankBrand.SBER, e.parts[0].bank)
        assertEquals(e.amountMinor, e.parts.sumOf { it.amountMinor!! })
        assertEquals("2 вклада", e.subtitle)
    }

    @Test fun oneDepositLeadsToIt() {
        val e = build(deps = listOf(dep("x", end = today.plusDays(10))), points = depPoints(110)).single { it.kind == HistoryKind.INTEREST }
        assertTrue(e.parts.isEmpty())
        assertEquals(BankBrand.SBER.title, e.subtitle)
    }

    @Test fun interestSplitsByDepositMath() {
        val big = dep("big").copy(principal = Money.ofMinor(300_000))
        val e = build(deps = listOf(big, dep("small")), points = depPoints(100 + 300)).single { it.kind == HistoryKind.INTEREST }
        val (a, b) = e.parts
        assertEquals(e.amountMinor, a.amountMinor!! + b.amountMinor!!)
        assertTrue(a.amountMinor!! > 2 * b.amountMinor!!)
    }

    @Test fun interestRemainderGoesToLargest() {
        val small = dep("a")
        val big = dep("b").copy(principal = Money.ofMinor(500_000))
        val r = CapitalEvents.splitInterest(101, listOf(small to 1L, big to 1L), d1)
        assertEquals(listOf(50L, 51L), r.map { it.second })
        assertEquals("b", r[1].first.id)
    }

    @Test fun interestZeroWeightsGoToLargest() {
        val r = CapitalEvents.splitInterest(7, listOf(dep("a") to 0L, dep("b").copy(principal = Money.ofMinor(900_000)) to 0L), d1)
        assertEquals(1, r.size)
        assertEquals("b", r[0].first.id)
        assertEquals(7L, r[0].second)
    }

    @Test fun depositOpenedTodayDoesNotCountPrincipal() {
        val fresh = dep("n", start = d1)
        val old = dep("o")
        val e = build(deps = listOf(old, fresh), points = depPoints(100 + 100_000 + 43)).single { it.kind == HistoryKind.INTEREST }
        // all of the interest is the old deposit's: the new one only brought its principal
        assertTrue(e.parts.isEmpty())
        assertEquals(HistoryTarget.Deposit("o"), e.target)
    }

    @Test fun valueOnBeforeStartIsZero() {
        assertEquals(0L, DepositMath.valueOn(dep("x"), d0.minusDays(10)))
        assertTrue(DepositMath.valueOn(dep("x"), d1) >= 100_000L)
    }

    @Test fun revaluationOneLegHasNoParts() {
        val e = reval(events(listOf(pointFx(d0, 9_000_000), pointFx(d1, 9_250_000)), listOf(buy("USD", d0, 100_000, 9_000_000)),
            listOf(FxRate("USD", d0, 90_000_000), FxRate("USD", d1, 92_500_000))))
        assertTrue(e.parts.isEmpty())
        assertEquals(HistoryTarget.Currency("USD"), e.target)
    }

    @Test fun revaluationTextNegativeAndNoLegs() {
        val leg = ru.maxrubtsoff.bankagg.domain.model.RevaluationLeg("USD", 81_920_000, 80_740_000, 120_000, -1)
        val eur = ru.maxrubtsoff.bankagg.domain.model.RevaluationLeg("EUR", 90_000_000, 90_220_000, 100, 1)
        assertEquals("$ 81,92 → 80,74 ₽", RevaluationText.subtitle(ru.maxrubtsoff.bankagg.domain.model.RevaluationReason(listOf(leg), 0)))
        assertEquals("$ −1,18 ₽ · € +0,22 ₽", RevaluationText.subtitle(ru.maxrubtsoff.bankagg.domain.model.RevaluationReason(listOf(leg, eur), 0)))
        assertEquals("$ 81,92 → 80,74 ₽ · остаток изменён", RevaluationText.subtitle(ru.maxrubtsoff.bankagg.domain.model.RevaluationReason(listOf(leg), 5)))
        assertEquals("Курс или остаток изменились", RevaluationText.subtitle(ru.maxrubtsoff.bankagg.domain.model.RevaluationReason(emptyList(), 5)))
        assertEquals("81,92 → 80,74 ₽ · " + Money.ofMinor(120_000).format("USD"), RevaluationText.partSubtitle(leg))
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
        val rows = HistoryRows.days(eventsOf(op("1", "a", -100, TransactionKind.EXPENSE), op("2", "a", -300, TransactionKind.EXPENSE)))
        val g = rows.single() as HistoryRow.Group
        assertEquals(2, g.count)
        assertEquals(-400L, g.totalMinor)
        assertEquals("Списания", g.title)
        assertEquals("2 операции", g.subtitle)
        assertEquals(RowKind.SUM, g.rowKind)
    }

    @Test fun singleSpendStaysSingle() {
        assertTrue(HistoryRows.days(eventsOf(op("1", "a", -100, TransactionKind.EXPENSE))).single() is HistoryRow.Single)
    }

    @Test fun spendAndTransferGroupedSeparately() {
        val rows = HistoryRows.days(
            eventsOf(
                op("1", "a", -100, TransactionKind.EXPENSE), op("2", "a", -200, TransactionKind.EXPENSE),
                op("3", "a", -300, TransactionKind.TRANSFER, who = "Иван"), op("4", "a", -400, TransactionKind.TRANSFER, who = "Анна")
            )
        )
        assertEquals(kotlin.collections.listOf(HistoryKind.SPEND, HistoryKind.TRANSFER), rows.map { (it as HistoryRow.Group).kind })
    }

    @Test fun incomeNeverGrouped() {
        val rows = HistoryRows.days(eventsOf(op("1", "a", 100, TransactionKind.INCOME), op("2", "a", 200, TransactionKind.INCOME)))
        assertEquals(2, rows.size)
        assertTrue(rows.all { it is HistoryRow.Single })
    }

    @Test fun groupIdIsStableByDayAndKind() {
        val g = HistoryRows.days(eventsOf(op("1", "a", -100, TransactionKind.EXPENSE), op("2", "a", -300, TransactionKind.EXPENSE))).single() as HistoryRow.Group
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

    // ---- F-50: kinds of lines ----

    @Test fun kindOfEvent() {
        val plain = ev(d1)
        assertEquals(RowKind.SUM, HistoryRows.kindOf(plain))
        assertEquals(RowKind.GO, HistoryRows.kindOf(plain.copy(target = HistoryTarget.Currency("USD"))))
        assertEquals(RowKind.OPEN, HistoryRows.kindOf(plain.copy(target = HistoryTarget.Currency("USD"), parts = listOf(plain))))
    }

    @Test fun transferGroupIsSum() {
        val g = HistoryRows.days(eventsOf(op("1", "a", -300, TransactionKind.TRANSFER, who = "Иван"), op("2", "a", -400, TransactionKind.TRANSFER, who = "Анна"))).single() as HistoryRow.Group
        assertEquals("Переводы", g.title)
        assertEquals(RowKind.SUM, g.rowKind)
    }

    @Test fun currencySpendGroupOpens() {
        val usd = { id: String -> txOf("USD").copy(id = id, kind = TransactionKind.EXPENSE) }
        val g = HistoryRows.days(CapitalEvents.build(listOf(usd("1"), usd("2")), emptyList(), emptyList(), emptyList(), null, emptyList(), today, ZoneOffset.UTC)).single() as HistoryRow.Group
        assertEquals(RowKind.OPEN, g.rowKind)
    }

    @Test fun operationsPlural() {
        assertEquals(listOf("1 операция", "2 операции", "5 операций", "11 операций", "21 операция", "22 операции"),
            listOf(1, 2, 5, 11, 21, 22).map { RuPlural.operations(it) })
    }
}
