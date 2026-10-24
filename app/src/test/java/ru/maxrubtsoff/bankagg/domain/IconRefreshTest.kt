package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.IconRefresh

class IconRefreshTest {

    private val hour = 60L * 60 * 1000
    private val banks = setOf("sber", "tbank")

    @Test fun nothingSaved_refreshes() {
        assertTrue(IconRefresh.isStale(null, 0, null, banks))
    }

    @Test fun sameDay_keepsSavedIcons() {
        assertFalse(IconRefresh.isStale(0, 5 * hour, banks, banks))
        assertFalse(IconRefresh.isStale(0, 23 * hour, banks, banks))
    }

    @Test fun nextDay_refreshes() {
        assertTrue(IconRefresh.isStale(0, 24 * hour, banks, banks))
    }

    @Test fun bankInstalledOrRemoved_refreshesAtOnce() {
        assertTrue(IconRefresh.isStale(0, hour, banks, banks + "ozon"))
        assertTrue(IconRefresh.isStale(0, hour, banks, setOf("sber")))
    }
}
