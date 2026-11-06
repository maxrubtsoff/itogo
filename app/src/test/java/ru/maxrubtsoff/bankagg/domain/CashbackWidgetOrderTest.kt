package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CashbackItem
import ru.maxrubtsoff.bankagg.domain.model.CashbackSpotlight
import ru.maxrubtsoff.bankagg.domain.model.CashbackWidgetOrder
import ru.maxrubtsoff.bankagg.domain.model.CashbackWidgetSort
import ru.maxrubtsoff.bankagg.domain.model.CategoryGroups

class CashbackWidgetOrderTest {

    private fun row(title: String, bank: BankBrand, pct: Double) =
        CashbackSpotlight.Row(title, "", CategoryGroups.OTHER, pct, listOf(bank), 1, pct)

    private var n = 0
    private fun item(brand: BankBrand, name: String, pct: Double, key: String = CategoryGroups.OTHER) =
        CashbackItem("i${n++}", "2026-11", brand, name, pct, groupKey = key)

    @Test fun defaultSortIsBank() {
        assertEquals(CashbackWidgetSort.BANK, CashbackWidgetSort.parse(null))
        assertEquals(CashbackWidgetSort.BANK, CashbackWidgetSort.parse("junk"))
        assertEquals(CashbackWidgetSort.NAME, CashbackWidgetSort.parse("NAME"))
    }

    @Test fun bankSortGroupsRowsOfOneBankTogether() {
        val rows = listOf(
            row("a1", BankBrand.ALFA, 7.0), row("o1", BankBrand.OZON, 5.0), row("s1", BankBrand.SBER, 5.0),
            row("a2", BankBrand.ALFA, 5.0), row("o2", BankBrand.OZON, 5.0)
        )
        val out = CashbackWidgetOrder.rows(rows, CashbackWidgetSort.BANK).map { it.title }
        assertEquals(listOf("a1", "a2", "o1", "o2", "s1"), out)
    }

    @Test fun nameSortIsCaseInsensitive() {
        val rows = listOf(row("яблоки", BankBrand.ALFA, 5.0), row("Аптеки", BankBrand.ALFA, 5.0), row("авто", BankBrand.SBER, 7.0))
        val out = CashbackWidgetOrder.rows(rows, CashbackWidgetSort.NAME).map { it.title }
        assertEquals(listOf("авто", "Аптеки", "яблоки"), out)
    }

    @Test fun sizeSortByPercentThenName() {
        val rows = listOf(row("Б", BankBrand.ALFA, 5.0), row("В", BankBrand.ALFA, 7.0), row("А", BankBrand.SBER, 5.0))
        val out = CashbackWidgetOrder.rows(rows, CashbackWidgetSort.SIZE).map { it.title }
        assertEquals(listOf("В", "А", "Б"), out)
    }

    @Test fun blocksOrderBanksByCountThenNameAndRowsByPercent() {
        val items = listOf(
            item(BankBrand.SBER, "Транспорт", 5.0),
            item(BankBrand.ALFA, "Кафе", 5.0), item(BankBrand.ALFA, "Аптеки", 7.0), item(BankBrand.ALFA, "Авто", 5.0),
            item(BankBrand.OZON, "АЗС", 5.0)
        )
        val b = CashbackWidgetOrder.blocks(items)
        assertEquals(listOf(BankBrand.ALFA, BankBrand.OZON, BankBrand.SBER), b.map { it.brand })
        assertEquals(listOf("Аптеки", "Авто", "Кафе"), b[0].rows.map { it.category })
    }

    @Test fun basePercentSeparatedFromRows() {
        val base = "base"
        val items = listOf(item(BankBrand.ALFA, "Все покупки", 1.5, key = base), item(BankBrand.ALFA, "Кафе", 5.0))
        val b = CashbackWidgetOrder.blocks(items).single()
        assertEquals(1.5, b.basePercent!!, 0.0)
        assertEquals(listOf("Кафе"), b.rows.map { it.category })
        assertNull(CashbackWidgetOrder.blocks(listOf(item(BankBrand.SBER, "АЗС", 5.0))).single().basePercent)
    }
}
