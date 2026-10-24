package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.ShotLine
import ru.maxrubtsoff.bankagg.domain.model.ShotLines

class ShotLinesTest {

    @Test fun dedupe_keepsMaxPerIdentity() {
        val r = ShotLines.dedupe(listOf(
            ShotLine("Здоровье", "health", 3.0, "Медицина 3%"),
            ShotLine("Здоровье", "health", 5.0, "Медицинские услуги 5%")
        ))
        assertEquals(1, r.size)
        assertEquals(5.0, r.first().percent!!, 0.0)
    }

    @Test fun dedupe_brandAndGroupBothKept() {
        val r = ShotLines.dedupe(listOf(
            ShotLine("Lamoda", "clothing", 10.0, "Lamoda 10%"),
            ShotLine("Одежда", "clothing", 5.0, "Одежда 5%")
        ))
        assertEquals(2, r.size)
    }

    @Test fun dedupe_keepsEmptyLines() {
        val r = ShotLines.dedupe(listOf(
            ShotLine("", "other", 7.0, "— 7%"),
            ShotLine("", "other", 3.0, "• 3%")
        ))
        assertEquals(2, r.size)
    }
}
