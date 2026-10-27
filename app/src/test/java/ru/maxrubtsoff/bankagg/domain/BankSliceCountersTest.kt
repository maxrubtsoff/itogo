package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankSlice

class BankSliceCountersTest {
    private fun line(a: Int, d: Int, s: Int) = BankSlice(
        brand = null, valueMinor = 0, share = 0f,
        accounts = List(a) { BankSlice.Line("a", 0) },
        deposits = List(d) { BankSlice.Line("d", 0) },
        subscriptionsCount = s
    ).countersLine

    @Test fun declension() {
        assertEquals("2 счёта · 3 вклада · 2 подписки", line(2, 3, 2))
        assertEquals("1 счёт · 1 вклад", line(1, 1, 0))
        assertEquals("5 счётов · 11 вкладов · 21 подписка", line(5, 11, 21))
        assertEquals("12 счётов", line(12, 0, 0))
        assertEquals("", line(0, 0, 0))
    }
}
