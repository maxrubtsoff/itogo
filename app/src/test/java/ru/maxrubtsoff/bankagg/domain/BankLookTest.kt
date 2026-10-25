package ru.maxrubtsoff.bankagg.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.BankDirectory

/**
 * What the user changed about a bank — its picture, letters, colour — is what
 * the app draws, whether the bank is one of the known ones or their own (B-49).
 */
class BankLookTest {

    @After fun clear() = BankDirectory.replace(emptyList())

    @Test fun own_bank_isFoundWithItsPicture() {
        val own = BankBrand("user:1", "Мой банк", 0xFF14387F, logoFile = "/data/bank-logos/user_1_7.png")
        BankDirectory.replace(listOf(own))
        assertEquals(own.logoFile, BankBrand.fromKey("user:1").logoFile)
    }

    @Test fun picture_put_on_a_known_bank_wins_over_its_builtin_look() {
        val custom = BankBrand.SBER.copy(logoFile = "/data/bank-logos/sber_7.png", letters = "МОЙ")
        BankDirectory.replace(listOf(custom))
        val resolved = BankBrand.fromKey(BankBrand.SBER.key)
        assertEquals("/data/bank-logos/sber_7.png", resolved.logoFile)
        assertEquals("МОЙ", resolved.letters)
    }

    @Test fun a_known_bank_the_user_left_alone_keepsItsBuiltinLook() {
        BankDirectory.replace(emptyList())
        assertEquals(BankBrand.SBER, BankBrand.fromKey(BankBrand.SBER.key))
        assertNull(BankBrand.fromKey(BankBrand.SBER.key).logoFile)
    }

    @Test fun an_unknown_key_isTheUnnamedBank() {
        assertEquals("Банк", BankBrand.fromKey("user:missing").title)
    }
}
