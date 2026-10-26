package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.CashBalance
import ru.maxrubtsoff.bankagg.domain.model.CashHistory
import ru.maxrubtsoff.bankagg.domain.model.Money

class CashHistoryTest {
    private fun cash(minor: Long, at: Long = 1L) = CashBalance(Money.ofMinor(minor), at)

    @Test fun sameAmountIsNoChange() {
        assertNull(CashHistory.change(cash(5000), cash(5000, 2)))
    }

    @Test fun firstSetIsWholeAmount() {
        val c = CashHistory.change(null, cash(7000, 9))!!
        assertEquals(7000L, c.delta.minor)
        assertEquals(7000L, c.amount.minor)
        assertEquals(9L, c.atMs)
    }

    @Test fun nextSetIsDifference() {
        assertEquals(-1500L, CashHistory.change(cash(5000), cash(3500, 2))!!.delta.minor)
    }
}
