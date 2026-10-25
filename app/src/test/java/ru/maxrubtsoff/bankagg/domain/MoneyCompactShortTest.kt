package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.MoneyCompact
import ru.maxrubtsoff.bankagg.domain.model.MoneyMask

class MoneyCompactShortTest {
    private val n = Money.NBSP

    @Test fun belowTenThousandStaysFull() {
        assertEquals(Money.ofMinor(999_999).format(), MoneyCompact.short(999_999))
    }

    @Test fun tenThousandIsThousands() {
        assertEquals("10${n}тыс${n}₽", MoneyCompact.short(1_000_000))
    }

    @Test fun fortyEightPointThree() {
        assertEquals("48,3${n}тыс${n}₽", MoneyCompact.short(4_830_000))
    }

    @Test fun millionsTwoDigits() {
        assertEquals("1,77${n}млн${n}₽", MoneyCompact.short(177_000_000))
    }

    @Test fun billions() {
        assertEquals("2,5${n}млрд${n}₽", MoneyCompact.short(250_000_000_000L))
    }

    @Test fun roundingHalfUp() {
        // 12 345 ₽ -> 12,345 -> 12,35 (half up)
        assertEquals("12,35${n}тыс${n}₽", MoneyCompact.short(1_234_500))
    }

    @Test fun promotesWhenRoundsToThousand() {
        assertEquals("1${n}млн${n}₽", MoneyCompact.short(99_999_900))
    }

    @Test fun signedPositiveAndNegative() {
        assertEquals("+1,77${n}млн${n}₽", MoneyCompact.short(177_000_000, signed = true))
        assertEquals("−48,3${n}тыс${n}₽", MoneyCompact.short(-4_830_000, signed = true))
    }

    @Test fun zeroIsFull() {
        assertEquals(Money.ZERO.format(), MoneyCompact.short(0, signed = true))
    }

    @Test fun maskCoversCompactText() {
        for (m in listOf(4_830_000L, 177_000_000L, 250_000_000_000L)) {
            val t = MoneyCompact.short(m, signed = true)
            val r = MoneyMask.ranges(t)
            assertEquals(1, r.size)
            assertEquals(0, r[0].first)
            assertEquals(t.length - 1, r[0].last)
        }
        assertTrue(MoneyMask.hasMoney(MoneyCompact.short(4_830_000)))
    }
}
