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

    @Test fun transparentCornersMeanNoBackground() {
        assertNull(LogoBackground.detect(listOf(0, 0, 0, 0)))
    }

    @Test fun opaqueBlackLogoSurvivesOnTransparentSheet() {
        val px = intArrayOf(0, 0, 0xFF000000.toInt(), 0)
        val bg = LogoBackground.detect(listOf(px[0], px[1], px[3], px[0]))
        val out = if (bg == null) px else LogoBackground.clear(px, bg)
        assertEquals(0xFF000000.toInt(), out[2])
    }

    @Test fun whiteBackgroundStillCleared() {
        assertEquals(white, LogoBackground.detect(listOf(white, white, white, white)))
        assertArrayEquals(intArrayOf(0, red), LogoBackground.clear(intArrayOf(white, red), white))
    }

    @Test fun semiTransparentIsNotNearOpaque() {
        assertEquals(false, LogoBackground.near(0x80FFFFFF.toInt(), white))
    }

    @Test fun clear_makesOnlyTheFieldTransparent() {
        val out = LogoBackground.clear(intArrayOf(white, red, offWhite), white)
        assertArrayEquals(intArrayOf(0, red, 0), out)
    }
}
