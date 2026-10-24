package ru.maxrubtsoff.bankagg.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.ui.common.CLOSE_MS
import ru.maxrubtsoff.bankagg.ui.common.Motion
import ru.maxrubtsoff.bankagg.ui.common.OPEN_MS

/** U-55 (P8–P9): one motion scale, and «Remove animations» makes everything instant. */
class MotionScaleTest {

    @Test fun stepsGoFromFastToSlow() {
        assertTrue(Motion.FAST < Motion.NORMAL)
        assertTrue(Motion.NORMAL < Motion.SLOW)
    }

    @Test fun animationsOffMeansInstant() {
        for (ms in listOf(Motion.FAST, Motion.NORMAL, Motion.SLOW)) {
            assertEquals(0, Motion.duration(ms, enabled = false))
            assertEquals(ms, Motion.duration(ms, enabled = true))
        }
    }

    @Test fun goingAwayIsAStepFasterThanComingIn() {
        assertEquals(Motion.NORMAL, OPEN_MS)
        assertEquals(Motion.FAST, CLOSE_MS)
    }
}
