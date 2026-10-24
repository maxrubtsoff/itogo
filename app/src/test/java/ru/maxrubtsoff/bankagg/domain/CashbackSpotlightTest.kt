package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CashbackItem
import ru.maxrubtsoff.bankagg.domain.model.CashbackSpotlight
import ru.maxrubtsoff.bankagg.domain.model.CategoryGroups
import java.time.LocalDate

class CashbackSpotlightTest {

    private val oct10 = LocalDate.of(2026, 10, 10)
    private var n = 0
    private fun item(brand: BankBrand, key: String, name: String, pct: Double) =
        CashbackItem("i${n++}", "2026-10", brand, name, pct, groupKey = key)

    @Test fun everyday_categories_outrank_rare_ones() {
        val items = listOf(
            item(BankBrand.SBER, "groceries", "Супермаркеты", 5.0),   // 5.0
            item(BankBrand.TBANK, "travel", "Путешествия", 10.0),     // 3.0
            item(BankBrand.ALFA, "cafe", "Кафе", 5.0),                // 4.5
            item(BankBrand.ALFA, "pharmacy", "Аптеки", 5.0),          // 4.0
            item(BankBrand.MTS, "taxi", "Такси", 5.0),                // 3.5
            item(BankBrand.OZON, "sport", "Спорт", 7.0)               // 2.8
        )
        val s = CashbackSpotlight.build(items, hasNextMonth = true, today = oct10)
        assertEquals(listOf("Супермаркеты", "Кафе", "Аптеки", "Такси"), s.rows.map { it.title })
        // Travel 10% is the month's top rate and missed the top four → "максимум".
        assertEquals("Путешествия", s.maximum!!.title)
    }

    @Test fun maximum_can_be_a_brand_and_needs_ten_percent() {
        val items = listOf(
            item(BankBrand.SBER, "groceries", "Супермаркеты", 5.0),
            item(BankBrand.TBANK, CategoryGroups.OTHER, "Золотая Рыбка", 15.0)
        )
        val s = CashbackSpotlight.build(items, true, oct10)
        // Outside the catalogue a shop keeps its name and weighs 0.2: 15 × 0.2 = 3 — still in the top 4.
        assertTrue(s.rows.any { it.title == "Золотая Рыбка" })
        assertNull(s.maximum)

        val small = CashbackSpotlight.build(listOf(item(BankBrand.SBER, "travel", "Путешествия", 9.0)), true, oct10)
        assertNull(small.maximum)
    }

    @Test fun catch_all_is_the_base_not_a_row() {
        val items = listOf(
            item(BankBrand.TBANK, "base", "Базовый кэшбэк", 1.5),
            item(BankBrand.SBER, "all", "Все покупки", 1.0),
            item(BankBrand.SBER, "groceries", "Супермаркеты", 5.0)
        )
        val s = CashbackSpotlight.build(items, true, oct10)
        assertEquals(listOf("Супермаркеты"), s.rows.map { it.title })
        assertEquals(
            listOf(CashbackSpotlight.BaseGroup(1.5, listOf(BankBrand.TBANK)), CashbackSpotlight.BaseGroup(1.0, listOf(BankBrand.SBER))),
            s.base
        )
    }

    @Test fun same_base_percent_is_one_group_and_a_bank_counts_once() {
        val items = listOf(
            item(BankBrand.TBANK, "all", "Все покупки", 1.0),
            item(BankBrand.ALFA, "all", "За все покупки", 1.0),
            item(BankBrand.SBER, "base", "Базовый кэшбэк", 0.5),
            item(BankBrand.SBER, "all", "Все покупки", 1.0)    // Сбер's best base is 1%
        )
        val s = CashbackSpotlight.build(items, true, oct10)
        assertEquals(listOf(CashbackSpotlight.BaseGroup(1.0, listOf(BankBrand.SBER, BankBrand.ALFA, BankBrand.TBANK))), s.base)   // known banks, largest first
        assertTrue(s.rows.isEmpty())
    }

    @Test fun widget_rows_go_past_the_top_four() {
        val items = listOf("groceries", "cafe", "fuel", "pharmacy", "online", "taxi", "travel")
            .mapIndexed { i, key -> item(BankBrand.SBER, key, key, 5.0 + i) }
        val s = CashbackSpotlight.build(items, true, oct10)
        assertEquals(4, s.rows.size)
        assertEquals(7, s.allRows.size)
        assertEquals(s.rows, s.allRows.take(4))
        assertEquals(items.size, s.allRows.map { it.title }.toSet().size)
    }

    @Test fun ties_keep_up_to_three_banks() {
        val items = listOf(BankBrand.SBER, BankBrand.TBANK, BankBrand.ALFA, BankBrand.MTS)
            .map { item(it, "groceries", "Супермаркеты", 5.0) }
        val row = CashbackSpotlight.build(items, true, oct10).rows.single()
        assertEquals(3, row.banks.size)
        assertEquals(1, row.extraBanks)
    }

    @Test fun rows_join_by_group_whatever_banks_call_it() {
        val items = listOf(
            item(BankBrand.OZON, "travel", "Отели", 5.0),
            item(BankBrand.SBER, "travel", "СберТревел", 5.0),
            item(BankBrand.TBANK, "clothing", "Lamoda", 7.0)
        )
        val s = CashbackSpotlight.build(items, true, oct10)
        assertEquals(listOf("Одежда", "Путешествия"), s.rows.map { it.title }.sorted())
        assertEquals(setOf(BankBrand.OZON, BankBrand.SBER), s.rows.first { it.title == "Путешествия" }.banks.toSet())
    }

    @Test fun hints_for_missing_month_and_next_month() {
        val empty = CashbackSpotlight.build(emptyList(), hasNextMonth = false, today = oct10)
        assertTrue(empty.missingThisMonth)
        assertFalse(empty.offerNextMonth)
        val late = CashbackSpotlight.build(emptyList(), hasNextMonth = false, today = LocalDate.of(2026, 10, 25))
        assertTrue(late.offerNextMonth)
        assertEquals("2026-11", late.nextMonthKey)
        assertFalse(CashbackSpotlight.build(emptyList(), hasNextMonth = true, today = LocalDate.of(2026, 10, 25)).offerNextMonth)
    }
}
