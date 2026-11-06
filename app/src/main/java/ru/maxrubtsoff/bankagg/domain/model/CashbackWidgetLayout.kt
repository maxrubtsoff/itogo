package ru.maxrubtsoff.bankagg.domain.model

/**
 * How the home-screen cashback widget fills its size (B-6, F-13). All in dp,
 * pure, so every size can be checked in tests:
 * - [grid]: columns, column width and how many 20dp lines fit, with the header
 *   and the base line reserved first — nothing is ever cut in half;
 * - [mode]: small (like the home card), medium (more categories), banks (blocks
 *   "logo → its categories" once the widget is really large);
 * - [packDp]: bank blocks into columns without splitting one; what doesn't fit is
 *   counted for a "+N банков" line.
 */
object CashbackWidgetLayout {

    const val PADDING = 14f          // inside the widget, each side
    const val HEADER = 34f           // 32dp header row (sort buttons' touch zone) + gap
    const val LINE = 20f             // a category line (20dp logo)
    const val GAP = 8f               // between lines
    const val BASE_LINE = 28f        // the base rate line + its gap
    const val COLUMN_GAP = 12f
    const val PERCENT_WIDTH = 46f    // "25%" / "1,5%" at 14sp bold, never cut
    const val LOGO = 20f
    const val LOGO_GAP = 8f
    const val NAME_GAP = 6f
    const val ROW = 18f              // a category line inside a bank block (13sp text, no logo)
    const val BLOCK_HEAD = LOGO      // bank block head: 20dp logo
    const val MORE_LINE = 18f        // the "+N банков" line

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

    /** Free height under the header for bank blocks (no base line in this mode). */
    fun banksHeight(heightDp: Float): Float = heightDp - 2 * PADDING - HEADER

    /**
     * Places bank blocks (category counts, in order) into [columns] by real dp
     * height: a block is its head plus n category rows; the least-filled column
     * that takes it whole wins. If something is left out, only the last column
     * keeps room for the "+N" line; the others keep their full height.
     */
    fun packDp(blocks: List<Int>, columns: Int, heightDp: Float): Packing {
        fun place(withMore: Boolean): Pair<List<List<Int>>, Int> {
            val used = FloatArray(columns)
            val out = List(columns) { mutableListOf<Int>() }
            var hidden = 0
            blocks.forEachIndexed { i, n ->
                val size = BLOCK_HEAD + n * (GAP + ROW)
                fun limit(c: Int) = if (withMore && c == columns - 1) heightDp - (GAP + MORE_LINE) else heightDp
                val target = (0 until columns)
                    .filter { c -> used[c] + (if (out[c].isEmpty()) 0f else GAP) + size <= limit(c) + 0.001f }
                    .minByOrNull { used[it] }
                if (target == null) hidden++ else {
                    used[target] += (if (out[target].isEmpty()) 0f else GAP) + size
                    out[target] += i
                }
            }
            return out to hidden
        }
        val (all, hidden) = place(false)
        if (hidden == 0) return Packing(all, 0)
        val (cut, cutHidden) = place(true)
        return Packing(cut, cutHidden)
    }

    /** Flat list: [shown] lines of the grid and, when some are hidden, [hidden] for the "+N категорий" line. */
    data class Flat(val shown: Int, val hidden: Int, val showMore: Boolean)

    /**
     * Fits [total] lines into [grid]. If they don't all fit, the last grid line
     * gives way to the "+N" line (it needs less height than a line); with one
     * line only there is no room for it, so just the first slots show.
     */
    fun flat(grid: Grid, total: Int): Flat {
        if (total <= grid.slots) return Flat(total, 0, false)
        if (grid.lines <= 1) return Flat(grid.slots, total - grid.slots, false)
        val shown = grid.columns * (grid.lines - 1)
        return Flat(shown, total - shown, true)
    }
}
