package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.maxrubtsoff.bankagg.domain.model.BankDirectory
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand

class BankBrandTest {

    // F-22: nothing is preinstalled; the known table still resolves every key
    // older versions stored, so existing data keeps its banks.
    @Test fun oldKeys_stillResolve() {
        listOf("sber", "tbank", "alfa", "yandex", "mts", "ozon", "alfa_business", "tbank_business", "bsb").forEach {
            assertEquals(it, BankBrand.fromKey(it).key)
            assertTrue(BankBrand.fromKey(it).isKnown)
        }
    }

    @Test fun unknownKey_keepsItsKey() {
        val b = BankBrand.fromKey("user:42")
        assertEquals("user:42", b.key)
    }

    @Test fun directory_resolvesUserBanks() {
        val mine = BankBrand("user:1", "Мой банк", 0xFF123456, packages = setOf("com.my.bank"))
        BankDirectory.replace(listOf(mine))
        try {
            assertEquals("Мой банк", BankBrand.fromKey("user:1").title)
            assertEquals(mine, BankBrand.fromPackage("com.my.bank"))
        } finally {
            BankDirectory.replace(emptyList())
        }
    }

    @Test fun letters_upToThreeInitials() {
        assertEquals("Р", BankBrand.lettersOf("Россельхозбанк"))
        assertEquals("ДР", BankBrand.lettersOf("Банк ДОМ.РФ"))
        assertEquals("КЕР", BankBrand.lettersOf("Кредит Европа Банк Россия"))   // «банк» is skipped
    }

    @Test fun bsb_isInCatalogueAndNeverAssumesRubles() {
        assertEquals(BankBrand.BSB, BankBrand.fromPackage("by.bank.bsb.mobile"))
        assertEquals(null, BankBrand.BSB.defaultCurrency)
        assertEquals("RUB", BankBrand.SBER.defaultCurrency)
    }

    @Test fun unknown_primaryPackage_isSafe() {
        assertEquals("", BankBrand.UNKNOWN.primaryPackage)
    }

    @Test fun fromPackage_mapsBusinessApps() {
        assertEquals(BankBrand.TBANK_BUSINESS, BankBrand.fromPackage("ru.tinkoff.sme"))
        assertEquals(BankBrand.ALFA_BUSINESS, BankBrand.fromPackage("ru.alfabank.oavdo.amc"))
        assertEquals(BankBrand.UNKNOWN, BankBrand.fromPackage("com.whatever.unknown"))
    }

    // Regression: selecting one bank must count as ONE, even though a bank
    // can have several package names. (Was showing 2 for one bank.)
    @Test fun selectedBankCount_oneBankOnePackage() {
        assertEquals(1, BankBrand.selectedBankCount(setOf("ru.sberbankmobile")))
    }

    @Test fun selectedBankCount_oneBankBothPackages_stillOne() {
        assertEquals(1, BankBrand.selectedBankCount(setOf("ru.sberbankmobile", "ru.sberbankmobile_alpha")))
    }

    @Test fun selectedBankCount_twoBanks() {
        assertEquals(
            2,
            BankBrand.selectedBankCount(setOf("ru.sberbankmobile", "com.idamob.tinkoff.android"))
        )
    }

    @Test fun selectedBankCount_empty() {
        assertEquals(0, BankBrand.selectedBankCount(emptySet()))
    }
}
