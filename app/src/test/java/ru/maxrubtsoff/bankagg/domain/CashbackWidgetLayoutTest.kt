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

    @Test fun pack_blocksStayWhole_leastFilledColumn() {
        val p = L.pack(listOf(4, 3, 2, 2), columns = 2, lines = 8)
        assertEquals(0, p.hidden)
        assertEquals(listOf(listOf(0, 3), listOf(1, 2)), p.columns)
    }

    @Test fun pack_leftoversCountedAndLineReserved() {
        val p = L.pack(listOf(5, 5, 4, 3), columns = 2, lines = 8)
        assertEquals(2, p.hidden)
        // Neither 4 nor 3 fits next to a 5-line block; one line stays for "+2 банка".
        assertEquals(listOf(listOf(0), listOf(1)), p.columns)
    }
}
