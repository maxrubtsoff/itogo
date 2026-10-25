package ru.maxrubtsoff.bankagg.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogoChoiceTest {
    @Test fun lettersModeDropsLogo() = assertNull(LogoChoice.resolve(false, "x", true, "y"))
    @Test fun newPictureWins() = assertEquals("x", LogoChoice.resolve(true, "x", true, "y"))
    @Test fun failedPictureKeepsExisting() = assertEquals("y", LogoChoice.resolve(true, null, true, "y"))
    @Test fun noPickedKeepsExisting() {
        assertEquals("y", LogoChoice.resolve(true, null, false, "y"))
        assertNull(LogoChoice.resolve(true, null, false, null))
    }
}
