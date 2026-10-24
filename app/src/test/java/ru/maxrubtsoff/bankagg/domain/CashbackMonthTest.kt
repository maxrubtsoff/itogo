package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.CashbackMonth
import java.time.LocalDate

class CashbackMonthTest {

    @Test fun keyOf_formatsYearMonth() {
        assertEquals("2026-10", CashbackMonth.keyOf(LocalDate.of(2026, 10, 20)))
        assertEquals("2026-01", CashbackMonth.keyOf(LocalDate.of(2026, 1, 5)))
    }

    @Test fun nextKey_rollsOverYear() {
        assertEquals("2026-11", CashbackMonth.nextKey("2026-10"))
        assertEquals("2027-01", CashbackMonth.nextKey("2026-12"))
    }

    @Test fun shortLabel_isRussianMonth() {
        assertEquals("Октябрь", CashbackMonth.shortLabel("2026-10"))
    }

    @Test fun label_includesYear() {
        assertTrue(CashbackMonth.label("2026-10").contains("2026"))
    }

    @Test fun shouldOfferNextMonth_onlyNearEnd() {
        assertTrue(CashbackMonth.shouldOfferNextMonth(LocalDate.of(2026, 10, 28)))  // 3 days left
        assertFalse(CashbackMonth.shouldOfferNextMonth(LocalDate.of(2026, 10, 10))) // 21 days left
    }

    @Test fun shouldOfferNextMonth_boundaryIs25th() {
        assertTrue(CashbackMonth.shouldOfferNextMonth(LocalDate.of(2026, 10, 25)))
        assertFalse(CashbackMonth.shouldOfferNextMonth(LocalDate.of(2026, 10, 24)))
    }

    @Test fun keyForOffset_movesMonths() {
        assertEquals("2026-10", CashbackMonth.keyForOffset("2026-10", 0))
        assertEquals("2026-11", CashbackMonth.keyForOffset("2026-10", 1))
        assertEquals("2026-09", CashbackMonth.keyForOffset("2026-10", -1))
        assertEquals("2027-02", CashbackMonth.keyForOffset("2026-10", 4))
        assertEquals("2025-10", CashbackMonth.keyForOffset("2026-10", -12))
    }
}
