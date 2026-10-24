package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.NewBanks

class NewBanksTest {

    private val payment = "Оплата 12,50 BYN. Доступно: 340,00 BYN"
    private val ownBsb = BankBrand("user:1", "BSB Bank", 0xFF14387F)
    private val app = "ru.maxrubtsoff.bankagg"

    @Test fun ownBankWithoutApp_isAskedByName() {
        val d = NewBanks.decide("by.bank.bsb.mobile", "BSB Bank", "Оплата", payment, listOf(ownBsb), emptySet(), app)
        assertEquals(NewBanks.Decision.Ask("user:1"), d)
    }

    @Test fun knownApp_notAdded_suggestsTheKnownBank() {
        val d = NewBanks.decide("ru.alfabank.mobile.android", "Альфа", null, "Покупка 500 ₽. Баланс 1 200 ₽", emptyList(), emptySet(), app)
        assertEquals(NewBanks.Decision.Ask("alfa"), d)
    }

    @Test fun unknownApp_noMatch_asksWithoutGuess() {
        val d = NewBanks.decide("com.some.bank", "Кошелёк", null, "Зачисление 1 000 ₽. Баланс 5 000 ₽", listOf(ownBsb), emptySet(), app)
        assertEquals(NewBanks.Decision.Ask(null), d)
    }

    @Test fun notAnOperation_isSkipped() {
        val d = NewBanks.decide("com.chat", "Чат", "Маша", "Привет, как дела?", emptyList(), emptySet(), app)
        assertEquals(NewBanks.Decision.Skip, d)
    }

    @Test fun myBank_ignored_andOwnApp_areSkipped() {
        assertEquals(NewBanks.Decision.Skip, NewBanks.decide("ru.sberbankmobile", "Сбер", null, payment, listOf(BankBrand.SBER), emptySet(), app))
        assertEquals(NewBanks.Decision.Skip, NewBanks.decide("com.x", "X", null, payment, emptyList(), setOf("com.x"), app))
        assertEquals(NewBanks.Decision.Skip, NewBanks.decide(app, "Агрегатор", null, payment, emptyList(), emptySet(), app))
    }

    @Test fun similarity_ignoresGenericWords() {
        assertTrue(NewBanks.similar("bsb bank", "bsb"))
        assertFalse(NewBanks.similar("мой банк", "другой банк"))
    }
}
