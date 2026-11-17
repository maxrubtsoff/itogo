package ru.maxrubtsoff.bankagg.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.BankDefinitions
import ru.maxrubtsoff.bankagg.domain.model.BankDirectory

class BankDefinitionsTest {

    private val known = BankBrand.known
    private val own = BankBrand("own:x", "WantToPay (TG)", 0xFF14387F, logoFile = "/d/x.png")

    @After fun clear() = BankDirectory.replace(emptyList())

    @Test fun placeholderIsRecognised() {
        BankDirectory.replace(emptyList())
        assertTrue(BankBrand.fromKey("own:x").isPlaceholder)
        assertFalse(own.isPlaceholder)
        assertFalse(BankBrand.SBER.isPlaceholder)
        assertFalse(BankBrand.UNKNOWN.isPlaceholder)
    }

    @Test fun placeholderIsNeverPersisted() {
        assertTrue(BankDefinitions.persistable(listOf(BankBrand.placeholder("own:x")), known).isEmpty())
    }

    @Test fun placeholderDoesNotOverwriteStoredDefinition() {
        val out = BankDefinitions.merge(listOf(own), listOf(BankBrand.placeholder("own:x")), known)
        assertEquals("WantToPay (TG)", out.single().title)
        assertEquals("/d/x.png", out.single().logoFile)
    }

    @Test fun ownBankIsPersisted() {
        assertEquals(1, BankDefinitions.persistable(listOf(own), known).size)
    }

    @Test fun knownBankWithChangedLogoIsPersisted() {
        assertEquals(1, BankDefinitions.persistable(listOf(BankBrand.SBER.copy(logoFile = "/d/s.png")), known).size)
    }

    @Test fun untouchedKnownBankIsNotPersisted() {
        assertTrue(BankDefinitions.persistable(listOf(BankBrand.SBER), known).isEmpty())
    }

    @Test fun resolvesByKeyAfterReplace() {
        BankDirectory.replace(listOf(own), activeKeys = setOf(own.key))
        val b = BankBrand.fromKey("own:x")
        assertEquals("WantToPay (TG)", b.title)
        assertFalse(b.isPlaceholder)
    }
}
