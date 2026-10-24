package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CashbackItem
import ru.maxrubtsoff.bankagg.domain.model.CashbackMerge
import ru.maxrubtsoff.bankagg.domain.model.CashbackMerge.Decision.ASK
import ru.maxrubtsoff.bankagg.domain.model.CashbackMerge.Decision.SAVE

class CashbackMergeTest {

    private var n = 0
    private fun item(key: String, name: String, pct: Double) =
        CashbackItem("id${n++}", "2026-10", BankBrand.SBER, name, pct, groupKey = key)

    @Test fun emptyExisting_save() =
        assertEquals(SAVE, CashbackMerge.decide(emptyList(), listOf(item("health", "Здоровье", 5.0))))

    @Test fun sameKeys_save() =
        assertEquals(SAVE, CashbackMerge.decide(
            listOf(item("health", "Здоровье", 3.0)), listOf(item("health", "Здоровье", 5.0))))

    @Test fun differentKeys_ask() =
        assertEquals(ASK, CashbackMerge.decide(
            listOf(item("health", "Здоровье", 3.0)), listOf(item("cafe", "Кафе", 5.0))))

    @Test fun append_updatesSameKeyKeepsIdAndAddsNew() {
        val old = item("health", "Здоровье", 3.0).copy(id = "a")
        val r = CashbackMerge.append(listOf(old), listOf(item("health", "Здоровье", 5.0), item("cafe", "Кафе", 7.0)))
        assertEquals(listOf("a"), r.filter { it.groupKey == "health" }.map { it.id })
        assertEquals(5.0, r.first { it.id == "a" }.percent, 0.0)
        assertEquals(2, r.size)
        assertEquals(listOf("health", "cafe"), r.map { it.groupKey })
    }

    @Test fun append_brandDoesNotOverwriteGroup() {
        val r = CashbackMerge.append(listOf(item("clothing", "Одежда", 5.0)), listOf(item("clothing", "Lamoda", 10.0)))
        assertEquals(2, r.size)
    }

    @Test fun editOntoExistingOffer_replacesTheOtherRow() {
        val cafe = item("cafe", "Кафе", 5.0).copy(id = "cafe")
        val health = item("health", "Здоровье", 3.0).copy(id = "h")
        // The user changes the health row's category to "Кафе".
        val edited = health.copy(category = "Кафе", groupKey = "cafe", percent = 7.0)
        assertEquals(listOf("cafe"), CashbackMerge.replacedByEdit(edited, listOf(cafe, health)))
        assertEquals(emptyList<String>(), CashbackMerge.replacedByEdit(health.copy(percent = 4.0), listOf(cafe, health)))
    }
}
