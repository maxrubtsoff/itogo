package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CashbackItem
import ru.maxrubtsoff.bankagg.domain.model.CashbackSummary
import ru.maxrubtsoff.bankagg.domain.model.SortOrder

class CashbackSummaryTest {

    private fun item(brand: BankBrand, key: String, name: String, pct: Double) =
        CashbackItem(id = "$brand-$name", monthKey = "2026-10", brand = brand, category = name, percent = pct, groupKey = key)

    @Test fun byCategory_bestBankAndRunnersUp() {
        val items = listOf(
            item(BankBrand.SBER, "groceries", "Супермаркеты", 10.0),
            item(BankBrand.ALFA, "groceries", "Супермаркеты", 5.0),
            item(BankBrand.MTS, "groceries", "Супермаркеты", 3.0),
            item(BankBrand.TBANK, "pharmacy", "Аптеки", 7.0)
        )
        val rows = CashbackSummary.byCategory(items)
        assertEquals(2, rows.size)
        // Супермаркеты (best 10%) sorts above Аптеки (7%)
        val groceries = rows[0]
        assertEquals("groceries", groceries.groupKey)
        assertEquals(10.0, groceries.best, 0.0001)
        assertEquals(listOf(BankBrand.SBER), groceries.bestBanks)
        assertEquals(listOf(BankBrand.ALFA, BankBrand.MTS), groceries.others.map { it.brand })
    }

    @Test fun byCategory_tieKeepsBothBestBanks() {
        val items = listOf(
            item(BankBrand.ALFA, "taxi", "Такси", 5.0),
            item(BankBrand.TBANK, "taxi", "Такси", 5.0)
        )
        val row = CashbackSummary.byCategory(items).single()
        assertEquals(5.0, row.best, 0.0001)
        assertEquals(setOf(BankBrand.ALFA, BankBrand.TBANK), row.bestBanks.toSet())
        assertEquals(emptyList<CashbackSummary.Offer>(), row.others)
    }

    @Test fun byCategory_joinsByGroup_keepsEachBanksName() {
        val items = listOf(
            item(BankBrand.OZON, "travel", "Отели", 5.0),
            item(BankBrand.SBER, "travel", "СберТревел", 5.0),
            item(BankBrand.ALFA, "travel", "Путешествия", 3.0)
        )
        val row = CashbackSummary.byCategory(items).single()
        assertEquals("Путешествия", row.category)
        assertEquals(listOf(BankBrand.OZON, BankBrand.SBER), row.bestBanks)
        assertEquals(listOf("Отели", "СберТревел", "Путешествия"), row.offers.map { it.name })
        assertEquals(listOf(BankBrand.ALFA), row.others.map { it.brand })
    }

    @Test fun byCategory_outsideTheCatalogueKeepsOwnRows() {
        val items = listOf(
            item(BankBrand.OZON, "other", "Hoff Плюс", 10.0),
            item(BankBrand.SBER, "other", "Золотая Рыбка", 5.0)
        )
        assertEquals(listOf("Hoff Плюс", "Золотая Рыбка"), CashbackSummary.byCategory(items).map { it.category })
    }

    @Test fun sort_byPercentOrAlphabet() {
        val rows = CashbackSummary.byCategory(listOf(
            item(BankBrand.OZON, "other", "Hoff Плюс", 10.0),
            item(BankBrand.SBER, "taxi", "Такси", 5.0),
            item(BankBrand.SBER, "pharmacy", "Аптеки", 7.0)
        ))
        fun sorted(o: CashbackSummary.Order, reversed: Boolean = false) =
            CashbackSummary.sort(rows, SortOrder(o, reversed)).map { it.category }
        assertEquals(listOf("Hoff Плюс", "Аптеки", "Такси"), sorted(CashbackSummary.Order.PERCENT))
        assertEquals(listOf("Такси", "Аптеки", "Hoff Плюс"), sorted(CashbackSummary.Order.PERCENT, reversed = true))
        assertEquals(listOf("Аптеки", "Такси", "Hoff Плюс"), sorted(CashbackSummary.Order.ALPHABET))
        assertEquals(listOf("Hoff Плюс", "Такси", "Аптеки"), sorted(CashbackSummary.Order.ALPHABET, reversed = true))
    }

    @Test fun sort_byBankCount_andBadge() {
        val rows = CashbackSummary.byCategory(listOf(
            item(BankBrand.OZON, "taxi", "Такси", 5.0),
            item(BankBrand.YANDEX, "taxi", "Такси", 5.0),
            item(BankBrand.SBER, "taxi", "Такси", 2.0),
            item(BankBrand.SBER, "pharmacy", "Аптеки", 7.0),
            item(BankBrand.SBER, "pharmacy", "Лекарства", 3.0)
        ))
        val sorted = CashbackSummary.sort(rows, SortOrder(CashbackSummary.Order.BANKS))
        assertEquals(listOf("Такси", "Аптеки"), sorted.map { it.category })
        val taxi = sorted.first()
        assertEquals(3, taxi.bankCount)
        assertEquals(1, taxi.moreBanks)        // two share the best 5%, Сбер hides behind them
        val pharmacy = sorted.last()
        assertEquals(1, pharmacy.bankCount)    // one bank's two offers count once
        assertEquals(0, pharmacy.moreBanks)
    }

    @Test fun order_storedWithDirection() {
        val o = SortOrder(CashbackSummary.Order.BANK, reversed = true)
        assertEquals(o, CashbackSummary.Order.decode(CashbackSummary.Order.encode(o)))
        assertEquals(SortOrder(CashbackSummary.Order.ALPHABET), CashbackSummary.Order.decode("abc"))
        assertEquals(SortOrder(CashbackSummary.Order.PERCENT), CashbackSummary.Order.decode(null))
    }
}
