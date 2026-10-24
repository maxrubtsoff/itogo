package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.BrandDecor
import ru.maxrubtsoff.bankagg.domain.model.ServiceBrands

/** F-33: the look a name gets by itself, shared by banks and subscriptions. */
class BrandDecorTest {

    @Test fun defaultFor_takesLettersFromTheName() {
        assertEquals("ЯП", BrandDecor.defaultFor("Яндекс Плюс").letters)
        assertEquals("N", BrandDecor.defaultFor("Netflix").letters)
    }

    @Test fun defaultFor_knownService_hasItsColour() {
        assertEquals(ServiceBrands.known("Netflix"), BrandDecor.defaultFor("Netflix").colorArgb)
    }

    @Test fun defaultFor_unknown_hasASteadyColour() {
        assertEquals(BrandDecor.defaultFor("Мой кружок"), BrandDecor.defaultFor("Мой кружок"))
    }

    @Test fun defaultFor_leavesTextColourAndPictureToTheUser() {
        val d = BrandDecor.defaultFor("Spotify")
        assertNull(d.textColorArgb)
        assertNull(d.logoFile)
    }

    @Test fun bank_roundTripsThroughDecor() {
        val own = BankBrand("user:1", "Свой", 0xFF14387F, letters = "СВ", textColor = 0xFFFFFFFF, logoFile = "/x/y.png")
        val d = own.decor()
        assertEquals(BrandDecor(0xFF14387F, "СВ", 0xFFFFFFFF, "/x/y.png"), d)
        val edited = own.withDecor("Новый", d.copy(letters = "Н", logoFile = null))
        assertEquals("Новый", edited.title)
        assertEquals("Н", edited.letters)
        assertNull(edited.logoFile)
        assertEquals("user:1", edited.key)
    }
}
