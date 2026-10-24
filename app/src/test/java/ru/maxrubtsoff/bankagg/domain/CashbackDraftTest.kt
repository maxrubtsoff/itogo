package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.CashbackDraft

class CashbackDraftTest {

    @Test fun percent_parsesCommaAndDot() {
        assertEquals(5.5, CashbackDraft.percent("5,5")!!, 0.0)
        assertEquals(7.0, CashbackDraft.percent("7")!!, 0.0)
    }

    @Test fun percent_rejectsBlankZeroAndOver100() {
        assertNull(CashbackDraft.percent(""))
        assertNull(CashbackDraft.percent("0"))
        assertNull(CashbackDraft.percent("150"))
        assertNull(CashbackDraft.percent(","))
    }

    @Test fun complete_needsNameAndPercent() {
        assertTrue(CashbackDraft.isComplete("Здоровье", "5"))
        assertFalse(CashbackDraft.isComplete("", "7"))
        assertFalse(CashbackDraft.isComplete("Здоровье", ""))
        assertFalse(CashbackDraft.isComplete("   ", "3"))
    }
}
