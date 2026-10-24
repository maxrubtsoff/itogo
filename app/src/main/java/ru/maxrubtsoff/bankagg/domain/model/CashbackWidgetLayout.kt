package ru.maxrubtsoff.bankagg.domain.model

/**
 * How the home-screen cashback widget fills its size (B-6, F-13). All in dp,
 * pure, so every size can be checked in tests:
 * - [grid]: columns, column width and how many 20dp lines fit, with the header
 *   and the base line reserved first — nothing is ever cut in half;
 * - [mode]: small (like the home card), medium (more categories), banks (blocks
 *   "logo → its categories" once the widget is really large);
 * - [pack]: bank blocks into columns without splitting one; what doesn't fit is
 *   counted for a "+N банков" line.
 */
object CashbackWidgetLayout {

    const val PADDING = 14f          // inside the widget, each side
    const val HEADER = 26f           // title line + its gap
    const val LINE = 20f             // a category line (20dp logo)
    const val GAP = 8f               // between lines
    const val BASE_LINE = 28f        // the base rate line + its gap
    const val COLUMN_GAP = 12f
    const val PERCENT_WIDTH = 46f    // "25%" / "1,5%" at 14sp bold, never cut
    const val LOGO = 20f
    const val LOGO_GAP = 8f
    const val NAME_GAP = 6f

    /** Two columns from this inner width: two readable rows side by side. */
    const val TWO_COLUMNS_FROM = 290f

    /** Bank blocks only on a really large widget: two columns and at least this many lines. */
    const val BANKS_FROM_LINES = 7

    enum class Mode { SMALL, MEDIUM, BANKS }

    data class Grid(val columns: Int, val columnWidth: Float, val lines: Int) {
        val slots: Int get() = columns * lines
        /** Width left for the category name in one column. */
        val nameWidth: Float get() = (columnWidth - LOGO - LOGO_GAP - NAME_GAP - PERCENT_WIDTH).coerceAtLeast(24f)
    }

    fun grid(widthDp: Float, heightDp: Float, baseLine: Boolean): Grid {
        val inner = widthDp - 2 * PADDING
        val columns = if (inner >= TWO_COLUMNS_FROM) 2 else 1
        val colW = if (columns == 2) (inner - COLUMN_GAP) / 2 else inner
        val free = heightDp - 2 * PADDING - HEADER - (if (baseLine) BASE_LINE else 0f)
        // n lines take n·LINE + (n−1)·GAP.
        val lines = ((free + GAP) / (LINE + GAP)).toInt().coerceAtLeast(1)
        return Grid(columns, colW, lines)
    }

    /**
     * Small — what the home card shows fits and little more; medium — room for
     * more categories; banks — two columns and [BANKS_FROM_LINES]+ lines.
     */
    fun mode(grid: Grid, cardRows: Int): Mode = when {
        grid.columns == 2 && grid.lines >= BANKS_FROM_LINES -> Mode.BANKS
        grid.slots > cardRows -> Mode.MEDIUM
        else -> Mode.SMALL
    }

    data class Packing(val columns: List<List<Int>>, val hidden: Int)

    /**
     * Places blocks (sizes in lines, in order) into [columns] of [lines] each:
     * every block goes to the least-filled column it fits in whole, gaps between
     * blocks included. One line is kept for "+N" when something is left out.
     */
    fun pack(blockLines: List<Int>, columns: Int, lines: Int): Packing {
        fun place(limit: Int): Pair<List<List<Int>>, Int> {
            val used = IntArray(columns)
            val out = List(columns) { mutableListOf<Int>() }
            var hidden = 0
            blockLines.forEachIndexed { i, size ->
                val target = (0 until columns)
                    .filter { c -> used[c] + (if (out[c].isEmpty()) 0 else 1) + size <= limit }
                    .minByOrNull { used[it] }
                if (target == null) hidden++ else {
                    used[target] += (if (out[target].isEmpty()) 0 else 1) + size
                    out[target] += i
                }
            }
            return out to hidden
        }
        val (all, hidden) = place(lines)
        if (hidden == 0) return Packing(all, 0)
        // Something is left out: keep the last line of the last column for "+N".
        val (cut, cutHidden) = place(lines - 1)
        return Packing(cut, cutHidden)
    }
}
