package ru.maxrubtsoff.bankagg.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.BankDirectory
import ru.maxrubtsoff.bankagg.domain.model.CashbackParser

/** B-119: a user's bank removed from "Мои банки" is not guessed from a screenshot. */
class BankDirectoryRecognizeTest {

    private val vk = BankBrand(key = "user:vk-1", title = "Вквкбанк", primaryColor = 0xFF112233, letters = "ВК")

    @After fun reset() = BankDirectory.replace(emptyList())

    @Test fun removed_user_bank_not_guessed_in_cashback() {
        BankDirectory.replace(listOf(vk), activeKeys = emptySet())
        assertNotEquals(vk.key, CashbackParser.guessBank("Вквкбанк кэшбэк 5%")?.key)
        assertFalse(BankBrand.recognizable.any { it.key == vk.key })
    }

    @Test fun active_user_bank_is_guessed() {
        BankDirectory.replace(listOf(vk), activeKeys = setOf(vk.key))
        assertEquals(vk.key, CashbackParser.guessBank("Вквкбанк кэшбэк 5%")?.key)
    }

    @Test fun removed_user_bank_still_found_by_key() {
        BankDirectory.replace(listOf(vk), activeKeys = emptySet())
        assertEquals(vk.key, BankBrand.fromKey(vk.key).key)
        assertTrue(BankBrand.all.any { it.key == vk.key })
    }

    @Test fun undo_restores_recognition() {
        BankDirectory.replace(listOf(vk), activeKeys = emptySet())
        assertNull(CashbackParser.guessBank("Вквкбанк"))
        BankDirectory.replace(listOf(vk), activeKeys = setOf(vk.key))
        assertEquals(vk.key, CashbackParser.guessBank("Вквкбанк")?.key)
    }
}
