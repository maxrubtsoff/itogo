package ru.maxrubtsoff.bankagg.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

/** The room for the keyboard under a sheet (B-58, B-110). */
class ImeRoomTest {
    @Test fun releasedWithoutKeyboardIsBarsOnly() = assertEquals(40, ImeRoom.height(800, 0, 40, false, true))
    @Test fun keyboardTallerThanHeldGrows() = assertEquals(900, ImeRoom.height(800, 900, 40, false, false))
    @Test fun leavingDropsHeld() = assertEquals(300, ImeRoom.height(800, 300, 40, true, false))
    @Test fun neverBelowBars() = assertEquals(40, ImeRoom.height(0, 0, 40, false, false))
    @Test fun heldStandsBeforeKeyboard() = assertEquals(800, ImeRoom.height(800, 0, 40, false, false))
}
