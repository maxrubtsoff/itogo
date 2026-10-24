package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.ScreenshotFileName

class ScreenshotFileNameTest {

    @Test fun ozon() =
        assertEquals(BankBrand.OZON, ScreenshotFileName.guessBank("Screenshot_20261020_141742_Ozon.jpg"))

    @Test fun tbank() =
        assertEquals(BankBrand.TBANK, ScreenshotFileName.guessBank("Screenshot_20261020_141731_T-Bank.jpg"))

    @Test fun yandexPayWithSpace() =
        assertEquals(BankBrand.YANDEX, ScreenshotFileName.guessBank("Screenshot_20261020_141737_Yandex Pay.jpg"))

    @Test fun copySuffixAndPng() {
        assertEquals(BankBrand.TBANK, ScreenshotFileName.guessBank("Screenshot_20261020_141731_T-Bank (1).jpg"))
        assertEquals(BankBrand.OZON, ScreenshotFileName.guessBank("Screenshot_20261020_141742_Ozon.png"))
    }

    @Test fun xiaomiPackage() =
        assertEquals(BankBrand.SBER, ScreenshotFileName.guessBank(
            "Screenshot_2026-10-20-14-17-42-123_${BankBrand.SBER.primaryPackage}.jpg"))

    @Test fun noAppName() {
        assertNull(ScreenshotFileName.guessBank("Screenshot_20261020-141742.png"))
        assertNull(ScreenshotFileName.guessBank("1000012345.jpg"))
        assertNull(ScreenshotFileName.guessBank(null))
    }

    @Test fun unrelatedApp() =
        assertNull(ScreenshotFileName.guessBank("Screenshot_20261020_141742_Chrome.jpg"))
}
