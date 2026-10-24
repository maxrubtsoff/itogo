package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.MoneyMask

/** U-35: the kopecks of every amount are found, so the UI can draw them smaller and dimmer. */
class MoneyFractionTest {

    private val nb = ' '

    private fun kopecks(text: String) = MoneyMask.fractions(text).map { text.substring(it) }

    @Test fun kopecksOfOneAmount() {
        assertEquals(listOf(",56"), kopecks("1${nb}234,56${nb}₽"))
    }

    @Test fun signedAndSmall() {
        assertEquals(listOf(",05"), kopecks("-0,05${nb}₽"))
        assertEquals(listOf(",30"), kopecks("+12,30${nb}₽"))
    }

    @Test fun wholeRublesHaveNone() {
        assertEquals(emptyList<String>(), kopecks("1${nb}000${nb}₽"))
    }

    @Test fun otherCurrency() {
        assertEquals(listOf(",5"), kopecks("12,5${nb}$"))
    }

    @Test fun severalAmountsInOneLine() {
        assertEquals(listOf(",24", ",92"), kopecks("+18${nb}083,24${nb}₽ · из 1${nb}713,92${nb}₽"))
    }

    @Test fun shortenedSumsAndPercentsAreNotKopecks() {
        assertEquals(emptyList<String>(), kopecks("4,27${nb}млн${nb}₽"))
        assertEquals(emptyList<String>(), kopecks("11,45% · 9 дн."))
    }
}
