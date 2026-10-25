package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.CapitalEvents
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
}
