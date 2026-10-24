package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.usecase.MyBanksScope

class MyBanksScopeTest {

    private val tbank = BankBrand("tbank", "Т-Банк", 0xFFFFDD2D)
    private val sber = BankBrand("sber", "Сбербанк", 0xFF21A038)

    @Test fun removedBank_isHidden_andAddingItBack_bringsItBack() {
        val items = listOf(sber, tbank, sber)
        assertEquals(listOf(sber, sber), MyBanksScope.only(items, setOf("sber")) { it })
        assertEquals(items, MyBanksScope.only(items, setOf("sber", "tbank")) { it })
    }

    @Test fun recordsWithoutABank_stayVisible() {
        assertTrue(MyBanksScope.keeps(BankBrand.UNKNOWN, emptySet()))
        assertFalse(MyBanksScope.keeps(tbank, emptySet()))
    }
}
