package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.CashbackWidgetLayout
import ru.maxrubtsoff.bankagg.domain.model.CashbackWidgetLayout.Mode

class CashbackWidgetLayoutTest {

    private val L = CashbackWidgetLayout

    /** Everything drawn must fit the height: header, base line, n lines with gaps. */
    private fun usedHeight(lines: Int, base: Boolean) =
        2 * L.PADDING + L.HEADER + (if (base) L.BASE_LINE else 0f) + lines * L.LINE + (lines - 1) * L.GAP

    @Test fun lines_neverOverflow_atAnySize() {
        for (h in 110..700 step 7) for (base in listOf(true, false)) {
            val g = L.grid(300f, h.toFloat(), base)
            if (usedHeight(1, base) <= h) {
                assertTrue("h=$h base=$base lines=${g.lines}", usedHeight(g.lines, base) <= h)
                // and one more line would not fit
                assertTrue(usedHeight(g.lines + 1, base) > h)
            }
        }
    }

    @Test fun columns_percentColumnAlwaysWhole() {
        for (w in 150..500 step 5) {
            val g = L.grid(w.toFloat(), 300f, true)
            // logo + gaps + name + percent = column width; percent keeps its full width
            val parts = L.LOGO + L.LOGO_GAP + g.nameWidth + L.NAME_GAP + L.PERCENT_WIDTH
            assertTrue("w=$w", parts <= g.columnWidth + 0.01f || g.nameWidth == 24f)
            val total = g.columns * g.columnWidth + (g.columns - 1) * L.COLUMN_GAP + 2 * L.PADDING
            assertEquals(w.toFloat(), total, 0.01f)
        }
    }

    @Test fun modes() {
        val small = L.grid(280f, 180f, true)
        assertEquals(1, small.columns)
        assertEquals(Mode.SMALL, L.mode(small, cardRows = small.slots))
        val medium = L.grid(340f, 260f, true)
        assertEquals(Mode.MEDIUM, L.mode(medium, cardRows = 5))
        val large = L.grid(340f, 420f, false)
        assertEquals(Mode.BANKS, L.mode(large, cardRows = 5))
    }

    private fun blockH(n: Int) = L.BLOCK_HEAD + n * (L.GAP + L.ROW)

    @Test fun packDpFitsAllWhenHeightEnough() {
        val blocks = listOf(3, 2, 2, 1, 1)
        // Columns: [3,1] and [2,2,1] -> need the taller one.
        val need = maxOf(blockH(3) + L.GAP + blockH(1), blockH(2) + L.GAP + blockH(2) + L.GAP + blockH(1))
        val p = L.packDp(blocks, 2, need)
        assertEquals(0, p.hidden)
        assertEquals(5, p.columns.sumOf { it.size })
    }

    @Test fun packDpHidesOnlyWhatDoesNotFit() {
        val h = blockH(3) + L.GAP + blockH(1) - 1f
        val p = L.packDp(listOf(3, 3, 1), 2, h)
        assertEquals(1, p.hidden)
        assertEquals(listOf(listOf(0), listOf(1)), p.columns)
    }

    @Test fun packDpReservesMoreLineOnlyInLastColumn() {
        // Column 0 is filled to the very top by one block; the "+N" line is only in column 1.
        val h = blockH(4)
        val p = L.packDp(listOf(4, 5), 2, h)
        assertEquals(1, p.hidden)
        assertEquals(listOf(0), p.columns[0])
    }

    @Test fun packDpNoExtraTail() {
        val blocks = listOf(3, 2, 2, 1, 1)
        for (h in 60..400 step 3) {
            val p = L.packDp(blocks, 2, h.toFloat())
            if (p.hidden > 0) {
                // A hidden block really does not fit: not even after the shown ones in the emptiest column.
                val shown = p.columns.flatten().toSet()
                val minHidden = blocks.indices.filter { it !in shown }.minOf { blockH(blocks[it]) }
                val freeMax = p.columns.mapIndexed { c, col ->
                    val used = col.sumOf { blockH(blocks[it]).toDouble() }.toFloat() + L.GAP * (col.size - 1).coerceAtLeast(0)
                    val limit = if (c == p.columns.lastIndex) h.toFloat() - (L.GAP + L.MORE_LINE) else h.toFloat()
                    limit - used - L.GAP
                }.max()
                assertTrue("h=$h", freeMax < minHidden)
            }
            p.columns.forEachIndexed { c, col ->
                val used = col.sumOf { blockH(blocks[it]).toDouble() }.toFloat() + L.GAP * (col.size - 1).coerceAtLeast(0)
                val limit = if (p.hidden > 0 && c == p.columns.lastIndex) h - (L.GAP + L.MORE_LINE) else h.toFloat()
                assertTrue("h=$h c=$c", used <= limit + 0.01f)
            }
        }
    }

    @Test fun banksHeight_excludesPaddingAndHeader() {
        assertEquals(300f - 2 * L.PADDING - L.HEADER, L.banksHeight(300f), 0.01f)
    }
}
