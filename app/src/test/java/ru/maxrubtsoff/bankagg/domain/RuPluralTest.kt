package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.RuPlural

class RuPluralTest {
    @Test fun forms() {
        assertEquals("1 категория", RuPlural.categories(1))
        assertEquals("3 категории", RuPlural.categories(3))
        assertEquals("5 категорий", RuPlural.categories(5))
        assertEquals("11 категорий", RuPlural.categories(11))
        assertEquals("21 категория", RuPlural.categories(21))
        assertEquals("22 категории", RuPlural.categories(22))
        assertEquals("6 скринов", RuPlural.screens(6))
        assertEquals("2 банка", RuPlural.banks(2))
        assertEquals("1 подписка", RuPlural.subscriptions(1))
        assertEquals("6 подписок", RuPlural.subscriptions(6))
        assertEquals("22 подписки", RuPlural.subscriptions(22))
        assertEquals("1 валюта", RuPlural.currencies(1))
        assertEquals("3 валюты", RuPlural.currencies(3))
        assertEquals("11 валют", RuPlural.currencies(11))
        assertEquals("1 скрин ждёт проверки", RuPlural.pendingShots(1))
        assertEquals("3 скрина ждут проверки", RuPlural.pendingShots(3))
        assertEquals("11 скринов ждут проверки", RuPlural.pendingShots(11))
        assertEquals("1 готов", RuPlural.readyShots(1))
        assertEquals("2 не прочитаны", RuPlural.unreadShots(2))
    }

    @Test fun duplicatesSkippedForms() {
        assertEquals("1 дубликат", RuPlural.duplicatesSkipped(1))
        assertEquals("3 дубликата", RuPlural.duplicatesSkipped(3))
        assertEquals("5 дубликатов", RuPlural.duplicatesSkipped(5))
        assertEquals("11 дубликатов", RuPlural.duplicatesSkipped(11))
        assertEquals("21 дубликат", RuPlural.duplicatesSkipped(21))
    }

    @Test fun depositScan() {
        assertEquals("Добавили 2 вклада · 1 уже был", RuPlural.depositScan(2, 1))
        assertEquals("Добавили 1 вклад · 3 уже были", RuPlural.depositScan(1, 3))
        assertEquals("Добавили 3 вклада", RuPlural.depositScan(3, 0))
        assertEquals("3 вклада уже были", RuPlural.depositScan(0, 3))
        assertEquals("1 вклад уже был", RuPlural.depositScan(0, 1))
        assertEquals(null, RuPlural.depositScan(0, 0))
    }
}
