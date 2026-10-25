package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.ServiceBrands

/** F-33: well-known services have their own colour, no network. */
class ServiceBrandsTest {

    @Test fun knownService_hasItsOwnColour() {
        assertEquals(0xFFE50914, ServiceBrands.known("Netflix"))
        assertEquals(0xFF1DB954, ServiceBrands.known("Spotify"))
        assertEquals(0xFF2AABEE, ServiceBrands.colorFor("Telegram Premium"))
    }

    @Test fun longerNameWins() {
        assertEquals(0xFFFF6600, ServiceBrands.known("Кинопоиск HD"))
        assertEquals(0xFFFC3F1D, ServiceBrands.known("Яндекс Плюс"))
    }

    @Test fun payeeSpelling_isRecognised() {
        // «OKKO.TV» as the bank writes it.
        assertEquals(ServiceBrands.known("Okko"), ServiceBrands.known("Okko.tv"))
        assertNotNull(ServiceBrands.known("YANDEX*PLUS"))
    }

    @Test fun unknownName_isNotKnown_butStillHasAColour() {
        assertNull(ServiceBrands.known("Мой кружок"))
        assertNull(ServiceBrands.known(""))
        assertNotNull(ServiceBrands.colorFor("Мой кружок"))
    }

    @Test fun colourIsSteadyPerName() {
        assertEquals(ServiceBrands.colorFor("Мой кружок"), ServiceBrands.colorFor("Мой кружок"))
        assertEquals(ServiceBrands.colorFor("Мой кружок"), ServiceBrands.colorFor("  мой кружок "))
    }

    @Test fun caseAndYo_dontMatter() {
        assertEquals(ServiceBrands.known("netflix"), ServiceBrands.known("NETFLIX"))
        assertEquals(ServiceBrands.known("Яндекс Плюс"), ServiceBrands.known("ЯНДЕКС ПЛЮС"))
        assertEquals(ServiceBrands.known("Мтс"), ServiceBrands.known("МТС"))
    }

    @Test fun shortAliasMatchesWholeWordOnly() {
        assertEquals(0xFF0077FF, ServiceBrands.known("VK Музыка"))
        assertNull(ServiceBrands.known("ВкусВилл"))
    }
}
