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
    }
}
