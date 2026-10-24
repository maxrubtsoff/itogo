package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.CapitalBar

class CapitalBarTest {

    private val eps = 1e-4f

    @Test fun tinyPart_getsTheMinimum() {
        // Accounts 11 114 of 7.35 mln.
        val w = CapitalBar.widths(listOf(11_114f, 4_522_209f, 819_739f, 2_000_000f))
        assertEquals(CapitalBar.MIN_SHARE, w[0], eps)
        assertEquals(1f, w.sum(), eps)
        // The others keep their ratio.
        assertEquals(4_522_209f / 2_000_000f, w[1] / w[3], 1e-3f)
    }

    @Test fun bigEnoughParts_stayAsTheyAre() {
        val w = CapitalBar.widths(listOf(1f, 1f, 2f))
        assertEquals(0.25f, w[0], eps)
        assertEquals(0.5f, w[2], eps)
    }

    @Test fun zeroStaysZero_onePartTakesAll() {
        assertEquals(listOf(0f, 1f, 0f), CapitalBar.widths(listOf(0f, 5f, 0f)))
        assertEquals(listOf(0f, 0f), CapitalBar.widths(listOf(0f, 0f)))
    }

    @Test fun liftingCascades() {
        // Two tiny parts and one that drops under the minimum once they're lifted.
        val w = CapitalBar.widths(listOf(0.001f, 0.001f, 0.041f, 0.957f), minShare = 0.04f)
        w.filter { it > 0f }.forEach { assertTrue("$w", it >= 0.04f - eps) }
        assertEquals(1f, w.sum(), eps)
    }

    @Test fun allTiny_splitEvenly() {
        val w = CapitalBar.widths(listOf(1f, 1f, 1f), minShare = 0.5f)
        w.forEach { assertEquals(1f / 3, it, eps) }
    }
}
