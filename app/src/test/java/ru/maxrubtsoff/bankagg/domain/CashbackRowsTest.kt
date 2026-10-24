package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.CashbackRows

/** One category — one row for a bank in a month (B-53). */
class CashbackRowsTest {

    private data class Row(val name: String, val percent: Double?)

    private fun merge(rows: List<Row>) = CashbackRows.merge(rows, { it.name }, { it.percent })

    @Test fun categories_of_other_rows_areTaken() {
        val names = listOf("Рестораны", "Отели", "")
        assertEquals(setOf("рестораны", "отели"), CashbackRows.taken(names))
        // The row being edited doesn't block itself.
        assertEquals(setOf("отели"), CashbackRows.taken(names, exceptIndex = 0))
    }

    @Test fun sameCategory_isTaken_whateverTheCase() {
        val names = listOf("Рестораны", "")
        assertTrue(CashbackRows.isTaken(names, exceptIndex = 1, name = "рестораны"))
        assertTrue(CashbackRows.isTaken(names, exceptIndex = 1, name = "  Рестораны "))
        assertFalse(CashbackRows.isTaken(names, exceptIndex = 1, name = "Кафе"))
        assertFalse(CashbackRows.isTaken(names, exceptIndex = 0, name = "Рестораны"))
    }

    @Test fun aDuplicateFromAScreenshot_becomesOneRow_withTheBiggerPercent() {
        val rows = merge(listOf(Row("Рестораны", 3.0), Row("Отели", 5.0), Row("рестораны", 7.0)))
        assertEquals(listOf(Row("рестораны", 7.0), Row("Отели", 5.0)), rows)
    }

    @Test fun aRowWithoutAPercent_losesToOneThatHasIt() {
        assertEquals(
            listOf(Row("Рестораны", 3.0)),
            merge(listOf(Row("Рестораны", null), Row("Рестораны", 3.0)))
        )
        assertEquals(
            listOf(Row("Рестораны", 3.0)),
            merge(listOf(Row("Рестораны", 3.0), Row("Рестораны", null)))
        )
    }

    @Test fun emptyRows_areKept_theUserIsStillFillingThemIn() {
        val rows = merge(listOf(Row("", null), Row("", null)))
        assertEquals(2, rows.size)
    }

    @Test fun differentCategories_areLeftAlone() {
        val rows = listOf(Row("Рестораны", 3.0), Row("Кафе", 5.0))
        assertEquals(rows, merge(rows))
    }
}
