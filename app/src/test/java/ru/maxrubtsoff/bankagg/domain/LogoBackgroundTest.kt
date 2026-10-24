package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.LogoBackground

class LogoBackgroundTest {

    private val white = 0xFFFFFFFF.toInt()
    private val offWhite = 0xFFF4F6F2.toInt()
    private val red = 0xFFC8102E.toInt()

    @Test fun plainField_isDetected() {
        assertEquals(white, LogoBackground.detect(listOf(white, offWhite, white, offWhite)))
    }

    @Test fun busyCorners_noBackground() {
        assertNull(LogoBackground.detect(listOf(white, red, white, white)))
    }

    @Test fun clear_makesOnlyTheFieldTransparent() {
        val out = LogoBackground.clear(intArrayOf(white, red, offWhite), white)
        assertArrayEquals(intArrayOf(0, red, 0), out)
    }
}
