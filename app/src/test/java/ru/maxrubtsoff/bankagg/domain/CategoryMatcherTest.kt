package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.CategoryMatcher

class CategoryMatcherTest {

    @Test fun normalize_lowercasesAndUnifiesYo() =
        assertEquals("все для дома", CategoryMatcher.normalize("Всё для  ДОМА"))

    @Test fun normalize_dropsPunctuation() =
        assertEquals("аптеки", CategoryMatcher.normalize("аптеки:"))

    @Test fun similarity_identicalIsOne() =
        assertEquals(1.0, CategoryMatcher.similarity("рестораны", "рестораны"), 0.0)

    @Test fun similarity_ocrTypoIsHigh() =
        assertTrue(CategoryMatcher.similarity("ресгораны", "рестораны") > 0.85)

    @Test fun similarity_unrelatedIsLow() =
        assertTrue(CategoryMatcher.similarity("вклады", "рестораны") < 0.5)
}
