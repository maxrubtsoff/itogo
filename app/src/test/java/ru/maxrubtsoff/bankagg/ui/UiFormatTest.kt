package ru.maxrubtsoff.bankagg.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.maxrubtsoff.bankagg.ui.cashback.formatPercent
import ru.maxrubtsoff.bankagg.ui.common.nbsp
import ru.maxrubtsoff.bankagg.ui.deposits.ratePercent

class UiFormatTest {

    @Test fun percent_usesComma() {
        assertEquals("1,5%", formatPercent(1.5))
        assertEquals("5%", formatPercent(5.0))
        assertEquals("0,25%", formatPercent(0.25))
    }

    @Test fun rate_keepsTwoDecimals() {
        assertEquals("19%", ratePercent(1900))
        assertEquals("19,5%", ratePercent(1950))
        assertEquals("19,25%", ratePercent(1925))
        assertEquals("8,05%", ratePercent(805))
    }

    @Test fun nbsp_gluesShortWords() {
        assertEquals("Кэшбэк на\u00A0октябрь", "Кэшбэк на октябрь".nbsp())
        assertEquals("в\u00A0банке и\u00A0дома", "в банке и дома".nbsp())
        // "Сбер" ends with no short word; "с" inside a word is not glued.
        assertEquals("Сбер вклад", "Сбер вклад".nbsp())
        assertEquals("Вклад с\u00A0капитализацией", "Вклад с капитализацией".nbsp())
    }

    @Test fun parseRubles_handlesSpacesAndKopecks() {
        assertEquals(150_000_050L, ru.maxrubtsoff.bankagg.ui.deposits.parseRubles("1 500 000,50")?.minor)
        assertEquals(150_000_000L, ru.maxrubtsoff.bankagg.ui.deposits.parseRubles("1 500 000")?.minor)
        assertEquals(1_050L, ru.maxrubtsoff.bankagg.ui.deposits.parseRubles("10.5")?.minor)
        assertEquals(null, ru.maxrubtsoff.bankagg.ui.deposits.parseRubles("0"))
        assertEquals(null, ru.maxrubtsoff.bankagg.ui.deposits.parseRubles("1.2.3"))
        assertEquals(null, ru.maxrubtsoff.bankagg.ui.deposits.parseRubles(""))
    }
}
