package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CbrBanks
import java.io.File

class CbrBanksTest {

    private val entries = CbrBanks.parse(
        """
        # comment
        1481	Сбербанк
        3349	Россельхозбанк
        2766	ОТП Банк
        3311	Кредит Европа Банк (Россия)
        """.trimIndent()
    )

    @Test fun parse_skipsComments() {
        assertEquals(4, entries.size)
        assertEquals(CbrBanks.Entry(2766, "ОТП Банк"), entries[2])
    }

    @Test fun search_byWordStart_knownFirst() {
        assertEquals(listOf(BankBrand.RSHB), CbrBanks.search("Россе", entries))
        val otp = CbrBanks.search("отп", entries).single()
        assertEquals("cbr:2766", otp.key)
        assertEquals("ОТП Банк", otp.title)
        // A word inside the name counts too.
        assertEquals("cbr:3311", CbrBanks.search("европа", entries).single().key)
    }

    @Test fun search_findsKnownByAlias_andCbrEntryBecomesKnownBank() {
        assertEquals(BankBrand.SBER, CbrBanks.search("сбер", entries).first())
        assertEquals(1, CbrBanks.search("сбербанк", entries).size)   // not twice
    }

    @Test fun search_emptyQuery_nothing() {
        assertTrue(CbrBanks.search("  ", entries).isEmpty())
    }

    @Test fun colour_isStable() {
        assertEquals(CbrBanks.colorFor("ОТП Банк"), CbrBanks.colorFor("отп банк"))
    }

    @Test fun bundledList_isReadable() {
        val list = CbrBanks.parse(File("src/main/assets/banks/cbr.tsv").readText())
        assertTrue(list.size > 300)
        assertTrue(list.any { it.regNumber == 1481 })
    }
}
