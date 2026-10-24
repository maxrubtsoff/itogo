package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.Money

class MoneyTest {

    @Test fun ofMajor_storesMinorUnits() {
        assertEquals(10_000L, Money.ofMajor(100.0).minor)
        assertEquals(10_740_981L, Money.ofMajor(107_409.81).minor)
    }

    @Test fun ofMajor_rounds() {
        assertEquals(1L, Money.ofMajor(0.005).minor)   // rounds up
        assertEquals(0L, Money.ofMajor(0.004).minor)   // rounds down
    }

    @Test fun plusAndMinus() {
        val a = Money.ofMajor(100.0)
        val b = Money.ofMajor(50.0)
        assertEquals(150.0, (a + b).toMajor(), 0.0001)
        assertEquals(50.0, (a - b).toMajor(), 0.0001)
    }

    @Test fun negativeIsNegative() {
        assertTrue(Money.ofMinor(-1).isNegative)
        assertFalse(Money.ZERO.isNegative)
    }

    @Test fun format_containsCurrencySymbol() {
        val s = Money.ofMajor(1_000_000.0).format()
        assertTrue("должна быть ₽: $s", s.contains("₽"))
        assertTrue("должны быть цифры: $s", s.any { it.isDigit() })
    }

    @Test fun format_hasNoBreakableSpaces() {
        val s = Money.ofMinor(9_999_999_999L).format()
        assertFalse("обычный пробел в «$s»", s.contains(' '))
        assertEquals("99 999 999,99 ₽", s)
    }

    @Test fun format_wholeAndFraction() {
        assertEquals("1 800 000 ₽", Money.ofMajor(1_800_000.0).format())
        assertEquals("1 234,50 ₽", Money.ofMinor(123_450).format())
        assertEquals("-15 ₽", Money.ofMinor(-1_500).format())
        assertEquals("0 ₽", Money.ZERO.format())
        assertEquals("0,05 ₽", Money.ofMinor(5).format())
    }

    @Test fun format_usesCatalogueSymbolOrCode() {
        assertEquals("1 000 $", Money.ofMajor(1000.0).format("USD"))
        assertEquals("10 Br", Money.ofMajor(10.0).format("BYN"))
        assertEquals("1 000 AUD", Money.ofMajor(1000.0).format("AUD"))
    }

    @Test fun formatNumber_noSymbol() {
        assertEquals("1 800 000", Money.ofMajor(1_800_000.0).formatNumber())
    }
}
