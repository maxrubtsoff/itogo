package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.AmountInputFormat

class AmountInputFormatTest {
    private val s = AmountInputFormat.SEP

    @Test fun groupsIntegerPart() {
        assertEquals("", AmountInputFormat.format(""))
        assertEquals("1", AmountInputFormat.format("1"))
        assertEquals("999", AmountInputFormat.format("999"))
        assertEquals("1${s}000", AmountInputFormat.format("1000"))
        assertEquals("2${s}000${s}000", AmountInputFormat.format("2000000"))
        assertEquals("12${s}345", AmountInputFormat.format("12345"))
    }

    @Test fun leavesFractionAlone() {
        assertEquals("1${s}234${s}567,89", AmountInputFormat.format("1234567,89"))
        assertEquals("1${s}000.5", AmountInputFormat.format("1000.5"))
        assertEquals("1${s}000,", AmountInputFormat.format("1000,"))
        assertEquals(",5", AmountInputFormat.format(",5"))
        assertEquals("16,51", AmountInputFormat.format("16,51"))
    }

    @Test fun leavesUnexpectedTextAsTyped() {
        assertEquals("1 000", AmountInputFormat.format("1 000"))
        assertEquals("abc", AmountInputFormat.format("abc"))
    }

    @Test fun cursorMapsBothWays() {
        // "1000000" shows as "1 000 000".
        val raw = "1000000"
        val expected = listOf(0, 1, 3, 4, 5, 7, 8, 9)
        expected.forEachIndexed { o, shown -> assertEquals("raw $o", shown, AmountInputFormat.toShown(raw, o)) }
        // Every shown position maps back inside the raw text, and round-trips for raw positions.
        for (sh in 0..AmountInputFormat.format(raw).length) {
            val r = AmountInputFormat.toRaw(raw, sh)
            assert(r in 0..raw.length) { "shown $sh → $r" }
        }
        expected.forEachIndexed { o, shown -> assertEquals(o, AmountInputFormat.toRaw(raw, shown)) }
        // Right after a separator, the cursor sits before the next digit.
        assertEquals(1, AmountInputFormat.toRaw(raw, 2))
    }

    @Test fun cursorInFraction() {
        val raw = "1234,5"   // "1 234,5"
        assertEquals(6, AmountInputFormat.toShown(raw, 5))
        assertEquals(7, AmountInputFormat.toShown(raw, 6))
        assertEquals(6, AmountInputFormat.toRaw(raw, 7))
    }
}
