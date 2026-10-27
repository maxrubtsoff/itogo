package ru.maxrubtsoff.bankagg.ui

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.ui.onboarding.TourFrame

/** The tour's light stays whole on the screen (B-48). */
class TourFrameTest {

    private val screen = Rect(0f, 0f, 1000f, 2000f)
    private val safe = Rect(24f, 120f, 976f, 1900f)

    @Test fun framedPartGrowsByThePadding() {
        val r = TourFrame.framed(Rect(100f, 400f, 500f, 600f), 18f, safe)
        assertEquals(Rect(82f, 382f, 518f, 618f), r)
    }

    @Test fun partAtTheEdgeIsPulledInsideTheSafeArea() {
        val r = TourFrame.framed(Rect(0f, 0f, 1000f, 300f), 18f, safe)
        assertEquals(Rect(24f, 120f, 976f, 318f), r)
        assertTrue(r.left >= safe.left && r.top >= safe.top && r.right <= safe.right && r.bottom <= safe.bottom)
    }

    @Test fun partOutsideTheSafeAreaLeavesNothingToShow() {
        val r = TourFrame.framed(Rect(100f, 1950f, 500f, 2100f), 18f, safe)
        assertEquals(0f, r.height, 0f)
    }

    @Test fun dimmedPartPushesOnlyTheTouchedSidesOutOfSight() {
        val sheet = TourFrame.dimmed(Rect(0f, 1200f, 1000f, 2000f), screen, 80f)
        assertEquals(Rect(-80f, 1200f, 1080f, 2080f), sheet)
        val bar = TourFrame.dimmed(Rect(16f, 1800f, 984f, 1950f), screen, 80f)
        assertEquals(Rect(16f, 1800f, 984f, 1950f), bar)
    }

    @Test fun radiusFollowsThePartAndNeverPassesHalfASide() {
        val window = Rect(0f, 0f, 100f, 40f)
        assertEquals(20f, TourFrame.radius(40f, 6f, window), 0f)
        assertEquals(14f, TourFrame.radius(8f, 6f, window), 0f)
    }

    @Test fun cardGoesUnderThePartThenOverItThenToTheBottom() {
        val under = TourFrame.cardTop(Rect(100f, 300f, 500f, 400f), 300f, 14f, safe)
        assertEquals(414f, under, 0f)
        val over = TourFrame.cardTop(Rect(100f, 1700f, 500f, 1800f), 300f, 14f, safe)
        assertEquals(1386f, over, 0f)
        val tall = TourFrame.cardTop(Rect(100f, 200f, 500f, 1850f), 300f, 14f, safe)
        assertEquals(1600f, tall, 0f)
        val none = TourFrame.cardTop(null, 300f, 14f, safe)
        assertEquals(120f + (1780f - 300f) / 2f, none, 0f)
    }

    @Test fun cardAboveBottomWindowNeverOverlapsIt() {
        val window = Rect(0f, 1400f, 1000f, 2000f)
        val top = TourFrame.cardTop(window, 300f, 16f, safe)
        assertTrue(top + 300f <= window.top)
    }

    @Test fun tallWindowKeepsCardInsideSafeTop() {
        val window = Rect(0f, 300f, 1000f, 2000f)
        val top = TourFrame.cardTop(window, 300f, 16f, safe)
        assertTrue(top >= safe.top)
    }
}
