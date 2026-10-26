package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CapitalEvents
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.model.ForeignAmount
import ru.maxrubtsoff.bankagg.domain.model.FxKind
import ru.maxrubtsoff.bankagg.domain.model.FxOperation
import ru.maxrubtsoff.bankagg.domain.model.HistoryEvent
import ru.maxrubtsoff.bankagg.domain.model.HistoryKind
import ru.maxrubtsoff.bankagg.domain.model.HistoryRow
import ru.maxrubtsoff.bankagg.domain.model.HistoryRows
import ru.maxrubtsoff.bankagg.domain.model.HistoryTarget
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.MonthEvents
import ru.maxrubtsoff.bankagg.domain.model.RevaluationLeg
import ru.maxrubtsoff.bankagg.domain.model.RevaluationReason
import ru.maxrubtsoff.bankagg.domain.model.RowKind
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class HistoryRowsTest {
    private val today = LocalDate.of(2026, 10, 25)
    private val oct = YearMonth.of(2026, 10)

    private fun ev(
        day: Int, kind: HistoryKind, part: CapitalPart, amount: Long?, title: String = "t", sub: String? = null,
        target: HistoryTarget? = null, month: Int = 10
    ) = HistoryEvent(LocalDate.of(2026, month, day), part, title, sub, amount, kind = kind, target = target)

    private fun month(events: List<HistoryEvent>, m: YearMonth = oct) = MonthEvents(m, events, 0L)

    private fun rows(events: List<HistoryEvent>, page: CapitalPart? = null) =
        HistoryRows.months(listOf(month(events)), page, today).single().rows

    private fun topup(day: Int, name: String) =
        ev(day, HistoryKind.DEPOSIT_TOPUP, CapitalPart.DEPOSITS, 1000, "Пополнение вклада", name, HistoryTarget.Deposit(name))

    @Test fun dayFoldsAnySameKind() {
        val days = HistoryRows.days(listOf(topup(20, "А"), topup(20, "Б")), null)
        val g = days.single().rows.single() as HistoryRow.Group
        assertEquals("Пополнения вкладов · 2", g.title)
        assertEquals(RowKind.OPEN, g.rowKind)
        assertEquals("А, Б", g.subtitle)
        assertEquals(2, g.sources.size)
    }

    @Test fun dayKeepsOneEventAndNewestDayFirst() {
        val days = HistoryRows.days(listOf(topup(19, "А"), topup(20, "Б")), null)
        assertEquals(listOf(20, 19), days.map { it.date.dayOfMonth })
        assertTrue(days.all { it.rows.single() is HistoryRow.Single })
    }

    @Test fun monthGroupsSortedByAbsSum() {
        val r = rows(listOf(
            ev(1, HistoryKind.INCOME, CapitalPart.ACCOUNTS, 100), ev(2, HistoryKind.INCOME, CapitalPart.ACCOUNTS, 100),
            ev(3, HistoryKind.SPEND, CapitalPart.ACCOUNTS, -5000), ev(4, HistoryKind.SPEND, CapitalPart.ACCOUNTS, -5000),
            ev(5, HistoryKind.TRANSFER, CapitalPart.ACCOUNTS, -900), ev(6, HistoryKind.TRANSFER, CapitalPart.ACCOUNTS, -900)
        )).map { it as HistoryRow.Group }
        assertEquals(listOf(-10000L, -1800L, 200L), r.map { it.totalMinor })
        assertEquals("2 операции", r[0].subtitle)
    }

    @Test fun monthSingleHasDateInSubtitle() {
        val r = rows(listOf(ev(18, HistoryKind.DEPOSIT_CLOSE, CapitalPart.DEPOSITS, -190000, "Вклад закрыт", "ВТБ"))).single() as HistoryRow.Single
        assertEquals("ВТБ · 18 октября", r.event.subtitle)
        assertEquals(1, r.sources.size)
    }

    @Test fun monthGroupChildrenAreDated() {
        val g = rows(listOf(topup(2, "А"), topup(14, "Б"))).single() as HistoryRow.Group
        assertEquals(listOf("А · 2 октября", "Б · 14 октября"), g.children.map { it.subtitle })
        assertEquals("А, Б", g.subtitle)
    }

    private fun interestDay(day: Int, vararg shares: Pair<String, Long>): HistoryEvent {
        val parts = shares.map { (id, a) ->
            HistoryEvent(LocalDate.of(2026, 10, day), CapitalPart.DEPOSITS, id, "Сбер", a, kind = HistoryKind.INTEREST, target = HistoryTarget.Deposit(id), bank = BankBrand.SBER)
        }
        return if (parts.size == 1) parts[0].copy(title = "Проценты", subtitle = shares[0].first, parts = emptyList())
        else ev(day, HistoryKind.INTEREST, CapitalPart.DEPOSITS, shares.sumOf { it.second }, "Проценты", "${parts.size} вклада").copy(parts = parts)
    }

    @Test fun monthInterestOneRowPartsPerDeposit() {
        val days = listOf(interestDay(3, "a" to 10L, "b" to 5L), interestDay(4, "a" to 11L, "b" to 4L), interestDay(5, "a" to 9L, "b" to 6L))
        val g = rows(days).single() as HistoryRow.Group
        assertEquals("Проценты", g.title)
        assertEquals(RowKind.OPEN, g.rowKind)
        assertEquals("2 вклада", g.subtitle)
        assertEquals(listOf(30L, 15L), g.children.map { it.amountMinor })
        assertEquals(45L, g.totalMinor)
        assertEquals(3, g.sources.size)
    }

    @Test fun monthInterestOneDepositGoesToIt() {
        val g = rows(listOf(interestDay(3, "a" to 10L), interestDay(4, "a" to 12L))).single() as HistoryRow.Group
        assertEquals(RowKind.GO, g.rowKind)
        assertEquals(HistoryTarget.Deposit("a"), g.target)
        assertEquals(22L, g.totalMinor)
        assertEquals(2, g.sources.size)
    }

    private fun reval(day: Int, vararg legs: RevaluationLeg, manual: Long = 0L): HistoryEvent {
        val reason = RevaluationReason(legs.toList(), manual)
        return ev(day, HistoryKind.REVALUATION, CapitalPart.CURRENCY, legs.sumOf { it.effectMinor } + manual, "Переоценка валюты").copy(reason = reason)
    }

    private fun leg(cur: String, from: Long, to: Long, effect: Long) = RevaluationLeg(cur, from, to, 100_000, effect)

    @Test fun monthRevaluationPartsPerCurrency() {
        val r = listOf(
            reval(3, leg("USD", 82_400_000, 81_000_000, -1400), leg("EUR", 93_300_000, 93_800_000, 500)),
            reval(9, leg("USD", 81_000_000, 80_740_000, -260), manual = -300L)
        )
        val g = rows(r, CapitalPart.CURRENCY).single() as HistoryRow.Group
        assertEquals("Переоценка", g.title)
        assertEquals(RowKind.OPEN, g.rowKind)
        assertEquals(3, g.children.size)
        val usd = g.children.first { it.target == HistoryTarget.Currency("USD") }
        assertEquals("82,40 → 80,74 ₽ за месяц", usd.subtitle)
        assertEquals(-1660L, usd.amountMinor)
        assertNull(g.children.last().target)
        assertEquals(-300L, g.children.last().amountMinor)
    }

    @Test fun monthRevaluationOneCurrencyGoesThere() {
        val g = rows(listOf(reval(3, leg("USD", 82_400_000, 81_000_000, -1400)))).single() as HistoryRow.Group
        assertEquals(RowKind.GO, g.rowKind)
        assertEquals(HistoryTarget.Currency("USD"), g.target)
        assertEquals("Переоценка валюты", g.title)
    }

    @Test fun titlesDependOnPage() {
        val k = HistoryKind.DEPOSIT_TOPUP
        assertEquals("Пополнения · 3", HistoryRows.groupTitle(k, CapitalPart.DEPOSITS, 3, CapitalPart.DEPOSITS))
        assertEquals("Пополнения вкладов · 3", HistoryRows.groupTitle(k, CapitalPart.DEPOSITS, 3, null))
        assertEquals("Покупки · 2", HistoryRows.groupTitle(HistoryKind.FX_BUY, CapitalPart.CURRENCY, 2, CapitalPart.CURRENCY))
        assertEquals("Продажи валюты · 2", HistoryRows.groupTitle(HistoryKind.FX_SELL, CapitalPart.CURRENCY, 2, null))
        assertEquals("Изменения · 2", HistoryRows.groupTitle(HistoryKind.CASH, CapitalPart.CASH, 2, CapitalPart.CASH))
        assertEquals("Наличные · 2", HistoryRows.groupTitle(HistoryKind.CASH, CapitalPart.CASH, 2, null))
        assertEquals("Переоценка", HistoryRows.groupTitle(HistoryKind.REVALUATION, CapitalPart.CURRENCY, 1, CapitalPart.CURRENCY))
        assertEquals("Переоценка валюты", HistoryRows.groupTitle(HistoryKind.REVALUATION, CapitalPart.CURRENCY, 1, null))
        assertEquals("Поступления в валюте · 2", HistoryRows.groupTitle(HistoryKind.INCOME, CapitalPart.CURRENCY, 2, null))
    }

    @Test fun cashGroupSubtitleIsDates() {
        val g = rows(listOf(
            ev(5, HistoryKind.CASH, CapitalPart.CASH, 1000, "Наличные", "изменили вручную"),
            ev(17, HistoryKind.CASH, CapitalPart.CASH, 4000, "Наличные", "изменили вручную")
        ), CapitalPart.CASH).single() as HistoryRow.Group
        assertEquals("Изменения · 2", g.title)
        assertEquals("5 и 17 октября", g.subtitle)
    }

    @Test fun namesCutAfterTwo() {
        val c = listOf("А", "Б", "В", "Г").map { ev(1, HistoryKind.INCOME, CapitalPart.ACCOUNTS, 1, sub = it) }
        assertEquals("А, Б и ещё 2", HistoryRows.names(c))
    }

    @Test fun monthGroupIdsUniqueAcrossMonths() {
        val a = ev(2, HistoryKind.INCOME, CapitalPart.ACCOUNTS, 1)
        val b = ev(3, HistoryKind.INCOME, CapitalPart.ACCOUNTS, 1)
        val sep = YearMonth.of(2026, 9)
        val ms = HistoryRows.months(
            listOf(month(listOf(a, b)), month(listOf(a.copy(date = LocalDate.of(2026, 9, 2)), b.copy(date = LocalDate.of(2026, 9, 3))), sep)), null, today
        )
        val ids = ms.flatMap { it.rows }.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    // ---- events from CapitalEvents.build ----

    @Test fun fxEventsSignedRubAndForeign() {
        val d = LocalDate.of(2026, 10, 8)
        val e = CapitalEvents.build(
            emptyList(), emptyList(),
            listOf(FxOperation("b", "USD", FxKind.BUY, d, 100_000, 8_074_000, 0L), FxOperation("s", "EUR", FxKind.SELL, d, 50_000, 4_720_000, 0L)),
            emptyList(), null, emptyList(), today, ZoneOffset.UTC
        )
        val buy = e.single { it.kind == HistoryKind.FX_BUY }
        val sell = e.single { it.kind == HistoryKind.FX_SELL }
        assertEquals(8_074_000L, buy.amountMinor)
        assertEquals(ForeignAmount("USD", 100_000), buy.foreign)
        assertEquals(-4_720_000L, sell.amountMinor)
        assertEquals(ForeignAmount("EUR", -50_000), sell.foreign)
    }

    @Test fun closedDepositHasNegativeValue() {
        val d = Deposit("x", BankBrand.SBER, null, Money.ofMinor(100_000), 1600, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1))
        val e = CapitalEvents.build(emptyList(), emptyList(), emptyList(), listOf(d), null, emptyList(), today, ZoneOffset.UTC)
            .single { it.kind == HistoryKind.DEPOSIT_CLOSE }
        assertTrue(e.amountMinor!! < -100_000L)
        assertEquals(BankBrand.SBER, e.bank)
    }
}
