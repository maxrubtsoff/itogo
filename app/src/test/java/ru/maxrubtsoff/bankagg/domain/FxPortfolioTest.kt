package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.FxKind
import ru.maxrubtsoff.bankagg.domain.model.FxMath
import ru.maxrubtsoff.bankagg.domain.model.FxOperation
import ru.maxrubtsoff.bankagg.domain.model.FxPortfolio
import ru.maxrubtsoff.bankagg.domain.model.FxRate
import ru.maxrubtsoff.bankagg.domain.model.FxRates
import java.math.BigDecimal
import java.time.LocalDate

class FxPortfolioTest {

    private var n = 0L
    private fun op(kind: FxKind, date: String, units: Long, rubMajor: Long, ccy: String = "USD") =
        FxOperation("op${n}", ccy, kind, LocalDate.parse(date), units * 100, rubMajor * 100, createdAt = n++)

    private fun rate(rub: String, date: String = "2026-10-21", ccy: String = "USD") =
        FxRate(ccy, LocalDate.parse(date), BigDecimal(rub).movePointRight(6).toLong())

    @Test fun example_from_spec_1000_dollars_at_76_50() {
        val buy = op(FxKind.BUY, "2025-10-21", 1000, 76_500)
        assertEquals(0, BigDecimal("76.5").compareTo(buy.rate))
        assertEquals("76,50", FxMath.formatRate(buy.rate))
        val p = FxPortfolio.position("USD", listOf(buy), rate("81.20"))
        assertEquals(100_000L, p.qtyMinor)
        assertEquals(8_120_000L, p.valueMinor)          // 81 200 ₽
        assertEquals(470_000L, p.unrealizedMinor)       // +4 700 ₽
        assertEquals(0, BigDecimal("76.5").compareTo(p.avgRate!!.stripTrailingZeros()))
    }

    @Test fun average_cost_and_realized_profit_on_sale() {
        val ops = listOf(
            op(FxKind.BUY, "2025-01-10", 1000, 75_000),
            op(FxKind.BUY, "2025-06-10", 1000, 85_000),   // avg 80
            op(FxKind.SELL, "2026-03-05", 500, 45_000)    // costOut 40 000 → +5 000
        )
        val p = FxPortfolio.position("USD", ops, rate("90"))
        assertEquals(150_000L, p.qtyMinor)
        assertEquals(12_000_000L, p.costMinor)          // 120 000 ₽ left
        assertEquals(500_000L, p.realizedMinor)         // +5 000 ₽
        assertEquals(13_500_000L, p.valueMinor)
        assertEquals(1_500_000L, p.unrealizedMinor)
        val results = FxPortfolio.results("USD", ops, rate("90"))
        assertEquals(FxPortfolio.OpResult.Sell(500_000L), results[ops[2].id])
        assertEquals(FxPortfolio.OpResult.Buy(1_500_000L), results[ops[0].id])  // 90 000 − 75 000
    }

    @Test fun selling_everything_leaves_no_average_rate() {
        val ops = listOf(op(FxKind.BUY, "2025-01-10", 100, 9_000), op(FxKind.SELL, "2025-02-10", 100, 9_500))
        val p = FxPortfolio.position("USD", ops, rate("90"))
        assertEquals(0L, p.qtyMinor)
        assertNull(p.avgRate)
        assertEquals(50_000L, p.realizedMinor)
    }

    @Test fun no_rate_means_no_value() {
        val p = FxPortfolio.position("USD", listOf(op(FxKind.BUY, "2025-01-10", 10, 900)), null)
        assertNull(p.valueMinor)
        assertNull(p.unrealizedPct)
    }

    @Test fun validate_finds_sale_without_balance() {
        val buy = op(FxKind.BUY, "2026-01-10", 400, 32_000)
        val sell = op(FxKind.SELL, "2026-03-05", 500, 45_000)
        val short = FxPortfolio.validate(listOf(buy, sell))!!
        assertEquals(sell.id, short.sale.id)
        assertEquals(40_000L, short.availableMinor)
        val check = FxPortfolio.checkSave(listOf(buy), sell)
        assertTrue(check is FxPortfolio.Check.Blocked)
        assertEquals("На 05.03 было только 400 $", (check as FxPortfolio.Check.Blocked).message)
    }

    @Test fun deleting_a_needed_purchase_is_blocked() {
        val buy = op(FxKind.BUY, "2026-01-10", 500, 40_000)
        val sell = op(FxKind.SELL, "2026-03-05", 300, 27_000)
        val check = FxPortfolio.checkDelete(listOf(buy, sell), buy.id)
        assertEquals("Без этой покупки не хватит валюты на продажу 05.03", (check as FxPortfolio.Check.Blocked).message)
        assertEquals(FxPortfolio.Check.Ok, FxPortfolio.checkDelete(listOf(buy, sell), sell.id))
    }

    @Test fun moving_a_purchase_after_the_sale_is_blocked() {
        val buy = op(FxKind.BUY, "2026-01-10", 500, 40_000)
        val sell = op(FxKind.SELL, "2026-03-05", 300, 27_000)
        val moved = buy.copy(date = LocalDate.parse("2026-04-01"))
        assertTrue(FxPortfolio.checkSave(listOf(buy, sell), moved) is FxPortfolio.Check.Blocked)
    }

    @Test fun same_day_buy_then_sell_is_fine() {
        val buy = op(FxKind.BUY, "2026-01-10", 100, 9_000)
        val sell = op(FxKind.SELL, "2026-01-10", 100, 9_100)
        assertNull(FxPortfolio.validate(listOf(sell, buy)))
        assertEquals(10_000L, FxPortfolio.availableOn(listOf(buy), "USD", LocalDate.parse("2026-01-10")))
    }

    @Test fun rate_shows_two_or_four_decimals() {
        assertEquals("76,50", FxMath.formatRate(BigDecimal("76.5")))
        assertEquals("76,4321", FxMath.formatRate(FxMath.rateOf(100_000, 7_643_210)))
        assertEquals("1 234,00", FxMath.formatRate(BigDecimal("1234")))
    }

    @Test fun rub_for_amount_rounds_half_up() {
        // 0,01 $ at 81,205 ₽ → 81,205 коп. → 81 коп.; 0,02 $ → 162,41 → 162
        assertEquals(81L, FxMath.rubOf(1, 81_205_000L))
        assertEquals(41L, FxMath.rubOf(1, 40_500_000L))   // 40,5 коп. → 41 (half up)
        assertEquals(8_120_500L, FxMath.rubOf(100_000, 81_205_000L))
    }

    @Test fun latest_rate_on_or_before_today_and_staleness() {
        val rates = listOf(rate("80", "2026-10-17"), rate("81", "2026-10-18"), rate("82", "2026-10-22"))
        val today = LocalDate.parse("2026-10-20")
        assertEquals(LocalDate.parse("2026-10-18"), FxRates.latestOnOrBefore(rates, "USD", today)!!.date)
        assertTrue(!FxRates.isStale(LocalDate.parse("2026-10-18"), today))
        assertTrue(FxRates.isStale(LocalDate.parse("2026-10-16"), today))
    }
}
