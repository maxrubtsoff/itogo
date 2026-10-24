package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.ShotKind

/** Real OCR text decides where a shared screenshot goes. */
class ShotKindTest {
    private fun ocr(name: String) = javaClass.getResource("/ocr/$name.txt")!!.readText()

    @Test fun cashbackScreens() {
        listOf("cashback_tbank", "cashback_sber", "cashback_tiles", "cashback_light", "cashback_ozon", "cashback_yandex")
            .forEach { assertEquals(it, ShotKind.CASHBACK, ShotKind.guess(ocr(it))) }
    }

    @Test fun depositScreens() {
        listOf("deposit_tbank_main", "deposit_tbank_events", "deposit_alfa_main", "deposit_alfa_forecast", "deposit_ozon_asset")
            .forEach { assertEquals(it, ShotKind.DEPOSIT, ShotKind.guess(ocr(it))) }
    }

    @Test fun unknownAsksTheUser() {
        assertNull(ShotKind.guess("Фото кота"))
    }
}
