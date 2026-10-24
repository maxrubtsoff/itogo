package ru.maxrubtsoff.bankagg.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TabMotionTest {

    @Test fun rightTab_comesFromTheRight() {
        assertEquals(1, TabMotion.direction(0, 2))
        assertEquals(1, TabMotion.direction(1, 2))
    }

    @Test fun leftTab_comesFromTheLeft() {
        // Settings → Cashback: Cashback is to the left.
        assertEquals(-1, TabMotion.direction(3, 1))
        assertEquals(-1, TabMotion.direction(2, 0))
    }

    @Test fun nestedScreen_isNotATabSwitch() {
        assertEquals(0, TabMotion.direction(2, -1))
        assertEquals(0, TabMotion.direction(-1, 2))
        assertEquals(0, TabMotion.direction(1, 1))
    }
}
