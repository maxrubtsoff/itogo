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
import ru.maxrubtsoff.bankagg.domain.model.HistoryRef
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

    @Test fun revaluationOfTheDealDayShowsFigures() {
        // 1000 $ bought at 80,00 and valued the same day at the CBR 80,74: figures, not the fallback text.
        val e = reval(
            events(
                listOf(pointFx(d0, 0), pointFx(d1, 8_074_000)),
                listOf(buy("USD", d1, 100_000, 8_000_000)),
                listOf(FxRate("USD", d1, 80_740_000))
            )
        )
        assertEquals(74_000L, e.reason!!.legs.single().effectMinor)
        assertEquals("$ 80,00 → 80,74 ₽", e.subtitle)
    }

    @Test fun revaluationOnSecondBuyDayShowsFigures() {
        // 100 $ held at 80,00, 50 $ more bought at 82,00; the CBR says 80,54 both days: only the new part loses.
        val e = reval(
            events(
                listOf(pointFx(d0, 805_400), pointFx(d1, 1_208_100)),
                listOf(buy("USD", d0, 10_000, 800_000), buy("USD", d1, 5_000, 410_000)),
                listOf(FxRate("USD", d0, 80_540_000), FxRate("USD", d1, 80_540_000))
            )
        )
        assertEquals(-7_300L, e.reason!!.legs.single().effectMinor)
        assertEquals("$ 81,03 → 80,54 ₽", e.subtitle)
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
        points: List<CapitalPoint> = emptyList(), rates: List<FxRate> = emptyList(), cash: CashBalance? = null,
        cashChanges: List<ru.maxrubtsoff.bankagg.domain.model.CashChange> = emptyList()
    ) = CapitalEvents.build(tx, emptyList(), ops, deps, cash, points, today, ZoneOffset.UTC, rates, cashChanges)

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
        assertEquals(HistoryKind.FX_BUY, e.kind)
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
        val rows = dayRows(eventsOf(op("1", "a", -100, TransactionKind.EXPENSE), op("2", "a", -300, TransactionKind.EXPENSE)))
        val g = rows.single() as HistoryRow.Group
        assertEquals(2, g.count)
        assertEquals(-400L, g.totalMinor)
        assertEquals("Списания", g.title)
        assertEquals("2 операции", g.subtitle)
        // F-51: its operations open the sheet, so the group opens into them.
        assertEquals(RowKind.OPEN, g.rowKind)
    }

    @Test fun singleSpendStaysSingle() {
        assertTrue(dayRows(eventsOf(op("1", "a", -100, TransactionKind.EXPENSE))).single() is HistoryRow.Single)
    }

    @Test fun spendAndTransferGroupedSeparately() {
        val rows = dayRows(
            eventsOf(
                op("1", "a", -100, TransactionKind.EXPENSE), op("2", "a", -200, TransactionKind.EXPENSE),
                op("3", "a", -300, TransactionKind.TRANSFER, who = "Иван"), op("4", "a", -400, TransactionKind.TRANSFER, who = "Анна")
            )
        )
        assertEquals(kotlin.collections.listOf(HistoryKind.SPEND, HistoryKind.TRANSFER), rows.map { (it as HistoryRow.Group).kind })
    }

    @Test fun incomeGroupedToo() {
        val g = dayRows(eventsOf(op("1", "a", 100, TransactionKind.INCOME), op("2", "a", 200, TransactionKind.INCOME))).single() as HistoryRow.Group
        assertEquals("Поступления · 2", g.title)
        assertEquals(RowKind.OPEN, g.rowKind)
    }

    @Test fun groupIdIsStableByDayAndKind() {
        val g = dayRows(eventsOf(op("1", "a", -100, TransactionKind.EXPENSE), op("2", "a", -300, TransactionKind.EXPENSE))).single() as HistoryRow.Group
        assertEquals("d:${d1.toEpochDay()}:SPEND:ACCOUNTS", g.id)
    }

    // ---- F-41: the day nearest to the picked one ----

    private fun dayRows(events: List<HistoryEvent>) = HistoryRows.days(events, null).flatMap { it.rows }

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
        // F-51: a line with a record of its own opens its sheet.
        assertEquals(RowKind.GO, HistoryRows.kindOf(plain.copy(ref = HistoryRef.Tx("t"))))
        assertEquals(RowKind.OPEN, HistoryRows.kindOf(plain.copy(target = HistoryTarget.Currency("USD"), parts = listOf(plain))))
    }

    @Test fun transferGroupIsSum() {
        val g = dayRows(eventsOf(op("1", "a", -300, TransactionKind.TRANSFER, who = "Иван"), op("2", "a", -400, TransactionKind.TRANSFER, who = "Анна"))).single() as HistoryRow.Group
        assertEquals("Переводы", g.title)
        assertEquals(RowKind.OPEN, g.rowKind)
    }

    @Test fun currencySpendGroupOpens() {
        val usd = { id: String -> txOf("USD").copy(id = id, kind = TransactionKind.EXPENSE) }
        val g = dayRows(CapitalEvents.build(listOf(usd("1"), usd("2")), emptyList(), emptyList(), emptyList(), null, emptyList(), today, ZoneOffset.UTC)).single() as HistoryRow.Group
        assertEquals(RowKind.OPEN, g.rowKind)
    }

    @Test fun operationsPlural() {
        assertEquals(listOf("1 операция", "2 операции", "5 операций", "11 операций", "21 операция", "22 операции"),
            listOf(1, 2, 5, 11, 21, 22).map { RuPlural.operations(it) })
    }

    // ---- F-48: cash edits ----

    private val cashAt = d1.atTime(10, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun cashChangesBecomeEvents() {
        val c = ru.maxrubtsoff.bankagg.domain.model.CashChange(cashAt, Money.ofMinor(9_000), Money.ofMinor(-1_000))
        val e = build(cash = CashBalance(Money.ofMinor(9_000), cashAt), cashChanges = listOf(c)).filter { it.kind == HistoryKind.CASH }
        assertEquals(1, e.size)
        assertEquals(-1_000L, e.single().amountMinor)
        assertEquals(d1, e.single().date)
    }

    @Test fun legacyCashEventOnlyWithoutChanges() {
        val legacy = build(cash = CashBalance(Money.ofMinor(9_000), cashAt)).filter { it.kind == HistoryKind.CASH }
        assertEquals(1, legacy.size)
        assertNull(legacy.single().amountMinor)
    }

    // B-86: operations of a foreign-currency account.
    private val uzs = Account("u", BankBrand.SBER, "Сбер UZS", Money.ofMinor(0), "UZS", 0L)
    private val uzsRate = 7_500L // 0,0075 ₽ for 1 UZS, micro-rubles

    private fun uzsPay(minor: Long = -2_026_300_000L, day: LocalDate = d1) = Transaction(
        "u1", BankBrand.SBER, "u", Money.ofMinor(minor), "UZS", TransactionKind.EXPENSE, null, null,
        day.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    )

    private fun uzsEvents(points: List<CapitalPoint> = emptyList(), rates: List<FxRate> = listOf(FxRate("UZS", d0, uzsRate))) =
        CapitalEvents.build(listOf(uzsPay()), listOf(uzs), emptyList(), emptyList(), null, points, today, ZoneOffset.UTC, rates)

    @Test fun foreign_account_payment_is_in_rubles_at_cbr_rate() {
        val e = uzsEvents().single()
        assertEquals(ru.maxrubtsoff.bankagg.domain.model.FxMath.rubOf(-2_026_300_000L, uzsRate), e.amountMinor)
    }

    @Test fun foreign_account_payment_keeps_foreign_amount_and_shows_it_in_subtitle() {
        val e = uzsEvents().single()
        assertEquals(ru.maxrubtsoff.bankagg.domain.model.ForeignAmount("UZS", -2_026_300_000L), e.foreign)
        assertTrue(e.subtitle!!.contains("UZS") || e.subtitle!!.contains(ru.maxrubtsoff.bankagg.domain.model.Currencies.symbolOf("UZS")))
    }

    @Test fun foreign_account_payment_without_rate_has_no_ruble_sum() {
        assertNull(uzsEvents(rates = emptyList()).single().amountMinor)
    }

    @Test fun foreign_account_payment_is_not_listed_again_as_revaluation() {
        val rub = ru.maxrubtsoff.bankagg.domain.model.FxMath.rubOf(-2_026_300_000L, uzsRate)
        val e = uzsEvents(listOf(pointFx(d0, 50_000_000), pointFx(d1, 50_000_000 + rub)))
        assertTrue(e.none { it.kind == HistoryKind.REVALUATION })
    }

    @Test fun foreign_and_ruble_spends_of_a_day_sum_in_rubles() {
        val rub = op("r", "a", -100_000L, TransactionKind.EXPENSE)
        val list = CapitalEvents.build(
            listOf(rub, uzsPay()), listOf(accA, uzs), emptyList(), emptyList(), null, emptyList(), today,
            ZoneOffset.UTC, listOf(FxRate("UZS", d0, uzsRate))
        )
        val expected = -100_000L + ru.maxrubtsoff.bankagg.domain.model.FxMath.rubOf(-2_026_300_000L, uzsRate)
        assertEquals(expected, list.sumOf { it.amountMinor ?: 0L })
    }

    // B-85: a deposit or a deal dated between two saved points is put in, not earned.
    private fun pointDep(day: LocalDate, v: Long) = CapitalPoint(day, v, mapOf(CapitalPart.DEPOSITS to v))

    @Test fun deposit_opened_between_points_is_not_interest() {
        val a = today.minusDays(20)
        val b = today.minusDays(10)
        val d = dep("x", start = today.minusDays(15))
        val grown = DepositMath.valueOn(d, b)
        val list = build(deps = listOf(d), points = listOf(pointDep(a, 0L), pointDep(b, grown)))
        val interest = list.single { it.kind == HistoryKind.INTEREST }
        assertEquals(grown - d.principal.minor, interest.amountMinor)
    }

    @Test fun topup_between_points_is_not_interest() {
        val a = today.minusDays(20)
        val b = today.minusDays(10)
        val base = Deposit("x", BankBrand.SBER, null, Money.ofMinor(100_000), 1600, today.minusDays(30), today.plusDays(100))
        val d = base.copy(topUps = listOf(DepositTopUp("u", today.minusDays(15), Money.ofMinor(50_000))))
        val list = build(deps = listOf(d), points = listOf(pointDep(a, DepositMath.valueOn(d, a)), pointDep(b, DepositMath.valueOn(d, b))))
        val interest = list.single { it.kind == HistoryKind.INTEREST }.amountMinor!!
        assertEquals(DepositMath.valueOn(d, b) - DepositMath.valueOn(d, a) - 50_000L, interest)
    }

    @Test fun fx_buy_between_points_is_not_revaluation() {
        val a = today.minusDays(20)
        val b = today.minusDays(10)
        val rate = 90_000_000L
        val rub = 9_000_000L
        val list = events(listOf(pointFx(a, 0), pointFx(b, rub)), listOf(buy("USD", today.minusDays(15), 100_000, rub)),
            listOf(FxRate("USD", a, rate), FxRate("USD", b, rate)))
        assertTrue(list.none { it.kind == HistoryKind.REVALUATION })
    }

    @Test fun fx_buy_between_points_revaluation_counts_only_rate_move() {
        val a = today.minusDays(20)
        val b = today.minusDays(10)
        val rub = 9_000_000L
        // 1000 $ bought at 90,00; valued at 92,00 on the second point: only the move after the deal.
        val list = events(listOf(pointFx(a, 0), pointFx(b, 9_200_000)), listOf(buy("USD", today.minusDays(15), 100_000, rub)),
            listOf(FxRate("USD", a, 90_000_000), FxRate("USD", today.minusDays(15), 90_000_000), FxRate("USD", b, 92_000_000)))
        assertEquals(200_000L, list.single { it.kind == HistoryKind.REVALUATION }.amountMinor)
    }
}
