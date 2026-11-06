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

    @Test fun chainOfStepsEachReachesBackToTheOppositeOne() {
        val a = HistoryRef.Manual(CapitalPart.CURRENCY, day(10), 1_000L, ManualSource.ACCOUNT)
        val b = HistoryRef.Manual(CapitalPart.CURRENCY, day(15), -1_000L, ManualSource.ACCOUNT, stepFrom = day(10))
        val c = HistoryRef.Manual(CapitalPart.CURRENCY, day(20), 1_000L, ManualSource.ACCOUNT, stepFrom = day(15))
        val pts = (5..22).map { p(it, 0L) }
        assertEquals(day(5)..day(9), ManualUndo.range(pts, a))
        assertEquals(day(10)..day(14), ManualUndo.range(pts, b))
        assertEquals(day(15)..day(19), ManualUndo.range(pts, c))
    }

    @Test fun editShiftsByDifference() {
        assertEquals(-71_110_746L, ManualUndo.editBy(ref, -10_000_000L))
        val out = ManualUndo.shift(points, ref, ManualUndo.editBy(ref, -10_000_000L))
        assertEquals(high - 71_110_746L, out.first().parts[CapitalPart.CURRENCY])
    }

    // Mistake +800k on 10th, made good by −800k on 20th: the plateau [10, 20) is the error.
    private val m = 80_000_000L
    private val plateau = (5..9).map { p(it, m) } + (10..19).map { p(it, 2 * m) } + (20..22).map { p(it, m) }
    private val first = HistoryRef.Manual(CapitalPart.CURRENCY, day(10), m, ManualSource.ACCOUNT, stepTo = day(20))
    private val second = HistoryRef.Manual(CapitalPart.CURRENCY, day(20), -m, ManualSource.ACCOUNT, stepFrom = day(10))

    private fun flat(changed: List<CapitalPoint>) =
        (plateau.associateBy { it.date } + changed.associateBy { it.date }).values.map { it.parts[CapitalPart.CURRENCY] }.toSet()

    @Test fun deletingCompensatedFirstRemovesPlateau() {
        val out = ManualUndo.shift(plateau, first, ManualUndo.deleteBy(first))
        assertEquals(day(10)..day(19), out.minOf { it.date }..out.maxOf { it.date })
        assertEquals(setOf(m), flat(out))
    }

    @Test fun deletingCompensationRemovesPlateau() {
        val out = ManualUndo.shift(plateau, second, ManualUndo.deleteBy(second))
        assertEquals(setOf(m), flat(out))
    }

    @Test fun editingFirstMovesPlateauByDifference() {
        val out = ManualUndo.shift(plateau, first, ManualUndo.editBy(first, m / 2))
        assertEquals(setOf(m + m / 2), out.map { it.parts[CapitalPart.CURRENCY] }.toSet())
    }

    @Test fun asymmetricPairLeavesOnlyTheDifferenceStep() {
        val base = 100_000_000L
        val pts = (5..9).map { p(it, base) } + (10..19).map { p(it, base + 80_000_000L) } + (20..22).map { p(it, base - 1_100_000L) }
        val a = HistoryRef.Manual(CapitalPart.CURRENCY, day(10), 80_000_000L, ManualSource.ACCOUNT, stepTo = day(20))
        val out = ManualUndo.shift(pts, a, ManualUndo.deleteBy(a)).associateBy { it.date }
        val after = (pts.associateBy { it.date } + out).toSortedMap().values.map { it.parts[CapitalPart.CURRENCY] }
        assertEquals(List(15) { base } + List(3) { base - 1_100_000L }, after)
    }
}
