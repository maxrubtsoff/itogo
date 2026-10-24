package ru.maxrubtsoff.bankagg.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CashbackShot
import ru.maxrubtsoff.bankagg.domain.model.CategoryGroups
import ru.maxrubtsoff.bankagg.domain.model.CategoryResolver
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus
import ru.maxrubtsoff.bankagg.domain.repository.OcrEngine
import ru.maxrubtsoff.bankagg.domain.usecase.RecognizeCashbackShot

class RecognizeCashbackShotTest {

    private class FakeOcr(private val text: String?) : OcrEngine {
        override suspend fun isReady() = true
        override suspend fun recognize(imagePath: String) = text
    }

    private fun shot(fileName: String?) =
        CashbackShot(id = "s", monthKey = "2026-10", imagePath = "/x.jpg", fileName = fileName)

    private suspend fun run(text: String?, fileName: String? = null) =
        RecognizeCashbackShot(FakeOcr(text), CategoryResolver(null))(shot(fileName))

    @Test fun linesKeepTheBanksWording_groupOnlySetsTheIcon() = runTest {
        val d = run("Медицинские услуги 5%\nМедицина 3%\nLamoda 10%\nA3C 4%", "Screenshot_20261020_141742_Ozon.jpg")
        assertEquals(ShotStatus.RECOGNIZED, d.status)
        assertEquals(BankBrand.OZON, d.brand)
        // OCR-garbled "A3C" falls back to the group's name.
        assertEquals(listOf("Медицинские услуги", "Медицина", "Lamoda", "АЗС"), d.lines.map { it.text })
        assertEquals(listOf("health", "health", "clothing", "fuel"), d.lines.map { it.groupKey })
        assertEquals(listOf(5.0, 3.0, 10.0, 4.0), d.lines.map { it.percent })
        assertEquals("Медицинские услуги", d.lines.first().sourceText)
    }

    @Test fun sameWordingTwice_isDeduped() = runTest {
        val d = run("Аптеки 3%\nАптеки 5%", "Screenshot_20261020_141742_Ozon.jpg")
        assertEquals(listOf("Аптеки"), d.lines.map { it.text })
        assertEquals(listOf(5.0), d.lines.map { it.percent })
    }

    @Test fun bankFromTextWhenNoFileName() = runTest {
        assertEquals(BankBrand.SBER, run("СберБанк\nАЗС 5%").brand)
    }

    @Test fun unknownBank_needsInput() = runTest {
        val d = run("АЗС 5%")
        assertEquals(ShotStatus.NEEDS_INPUT, d.status)
        assertNull(d.brand)
    }

    @Test fun nothingRead_failed() = runTest {
        assertEquals(ShotStatus.FAILED, run("").status)
    }

    @Test fun noiseLineBecomesEmptyRow() = runTest {
        val d = run("— 7%", "Screenshot_20261020_141742_Ozon.jpg")
        assertEquals(listOf(""), d.lines.map { it.text })
        assertEquals(7.0, d.lines.first().percent!!, 0.0)
    }
}
