package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CapitalPoint
import ru.maxrubtsoff.bankagg.domain.model.HistoryRef
import ru.maxrubtsoff.bankagg.domain.model.ManualSource
import ru.maxrubtsoff.bankagg.domain.model.ManualUndo
import java.time.LocalDate

/** B-121: the mistaken hand edit of 27.10 (−811 107,46 ₽ on «Валюта»). */
class ManualUndoTest {
    private fun day(d: Int) = LocalDate.of(2026, 10, d)
    private fun p(d: Int, fx: Long, acc: Long = 500_000L) =
        CapitalPoint(day(d), fx + acc, mapOf(CapitalPart.CURRENCY to fx, CapitalPart.ACCOUNTS to acc))

    private val step = -81_110_746L
    private val ref = HistoryRef.Manual(CapitalPart.CURRENCY, day(27), step, ManualSource.ACCOUNT)
    private val high = 162_000_000L
    private val low = high + step
    private val points = (20..26).map { p(it, high) } + p(27, low) + p(28, low)

    @Test fun shiftsDaysBeforeEventByManualPart() {
        val out = ManualUndo.shift(points, ref, ManualUndo.deleteBy(ref)).associateBy { it.date }
        assertEquals((20..26).map { day(it) }.toSet(), out.keys)
        out.values.forEach {
            assertEquals(low, it.parts[CapitalPart.CURRENCY])
            assertEquals(low + 500_000L, it.totalMinor)
            assertEquals(500_000L, it.parts[CapitalPart.ACCOUNTS])
        }
    }

    @Test fun stopsAtOppositeManualStep() {
        val pts = (10..14).map { p(it, 1_000L) } + (15..26).map { p(it, high) } + p(27, low)
        val r = ref.copy(stepFrom = day(15))
        val out = ManualUndo.shift(pts, r, r.manualMinor)
        assertEquals(day(15), out.minOf { it.date })
        assertEquals(day(26), out.maxOf { it.date })
        assertEquals(12, out.size)
    }

    @Test fun otherPartsUntouched() {
        val out = ManualUndo.shift(points, ref, step)
        assertTrue(out.all { it.parts[CapitalPart.ACCOUNTS] == 500_000L })
    }

    @Test fun noPointsBeforeIsEmpty() {
        assertNull(ManualUndo.range(listOf(p(27, low), p(28, low)), ref))
        assertTrue(ManualUndo.shift(listOf(p(27, low)), ref, step).isEmpty())
    }

    @Test fun editShiftsByDifference() {
        assertEquals(-71_110_746L, ManualUndo.editBy(ref, -10_000_000L))
        val out = ManualUndo.shift(points, ref, ManualUndo.editBy(ref, -10_000_000L))
        assertEquals(high - 71_110_746L, out.first().parts[CapitalPart.CURRENCY])
    }
}
