package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CashbackItem
import ru.maxrubtsoff.bankagg.domain.model.CashbackWidgetOrder
import ru.maxrubtsoff.bankagg.domain.model.CashbackWidgetSort
import ru.maxrubtsoff.bankagg.domain.model.CategoryGroups

class CashbackWidgetOrderTest {

    private var n = 0
    private fun item(brand: BankBrand, name: String, pct: Double, key: String = CategoryGroups.OTHER) =
        CashbackItem("i${n++}", "2026-11", brand, name, pct, groupKey = key)

    @Test fun defaultSortIsBank() {
        assertEquals(CashbackWidgetSort.BANK, CashbackWidgetSort.parse(null))
        assertEquals(CashbackWidgetSort.BANK, CashbackWidgetSort.parse("junk"))
        assertEquals(CashbackWidgetSort.NAME, CashbackWidgetSort.parse("NAME"))
    }

    private val shot = listOf(
        item(BankBrand.OZON, "Автоуслуги", 5.0), item(BankBrand.OZON, "Топливо и АЗС", 5.0),
        item(BankBrand.OZON, "Электроника и бытовая техника", 5.0),
        item(BankBrand.ALFA, "Кафе и рестораны", 5.0), item(BankBrand.ALFA, "Пятёрочка", 4.0),
        item(BankBrand.ALFA, "Все покупки", 1.0, key = "base")
    )

    @Test fun flatModesHaveSameRowsAsBankBlocks() {
        val bank = CashbackWidgetOrder.blocks(shot).flatMap { b -> b.rows.map { b.brand to it.category } }.toSet()
        for (sort in listOf(CashbackWidgetSort.BANK, CashbackWidgetSort.SIZE, CashbackWidgetSort.NAME)) {
            val flat = CashbackWidgetOrder.flatRows(shot, sort)
            assertEquals(5, flat.size)
            assertEquals(bank, flat.map { it.brand to it.category }.toSet())
        }
        assertEquals(true, CashbackWidgetOrder.flatRows(shot, CashbackWidgetSort.SIZE).any { it.category == "Автоуслуги" })
    }

    @Test fun nameSortIsCaseInsensitive() {
        val items = listOf(item(BankBrand.ALFA, "яблоки", 5.0), item(BankBrand.ALFA, "Аптеки", 5.0), item(BankBrand.SBER, "авто", 7.0))
        val out = CashbackWidgetOrder.flatRows(items, CashbackWidgetSort.NAME).map { it.category }
        assertEquals(listOf("авто", "Аптеки", "яблоки"), out)
    }

    @Test fun sizeSortByPercentThenName() {
        val items = listOf(item(BankBrand.ALFA, "Б", 5.0), item(BankBrand.ALFA, "В", 7.0), item(BankBrand.SBER, "А", 5.0))
        val out = CashbackWidgetOrder.flatRows(items, CashbackWidgetSort.SIZE).map { it.category }
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

    @Test fun baseItemsAreNotRows() {
        val base = "base"
        val items = listOf(item(BankBrand.ALFA, "Все покупки", 1.5, key = base), item(BankBrand.ALFA, "Кафе", 5.0))
        val b = CashbackWidgetOrder.blocks(items).single()
        assertEquals(listOf("Кафе"), b.rows.map { it.category })
    }
}
