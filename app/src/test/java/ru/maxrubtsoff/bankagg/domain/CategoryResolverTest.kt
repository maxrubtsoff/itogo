package ru.maxrubtsoff.bankagg.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.CategoryGroups
import ru.maxrubtsoff.bankagg.domain.model.CategoryResolver
import ru.maxrubtsoff.bankagg.domain.model.Resolution
import ru.maxrubtsoff.bankagg.domain.repository.TextEmbedder
import kotlin.math.sqrt

class CategoryResolverTest {

    /** Texts it knows map to fixed vectors; anything else is the zero vector (cos 0). */
    private class FakeEmbedder(private val known: Map<String, FloatArray>, private val available: Boolean = true) : TextEmbedder {
        var calls = 0
        override suspend fun embed(texts: List<String>): List<FloatArray>? {
            calls++
            if (!available) return null
            return texts.map { known[it] ?: FloatArray(DIM) }
        }
    }

    private companion object {
        const val DIM = 4
        const val HIGH = 0.9f
        const val MID = 0.6f
        fun e(i: Int) = FloatArray(DIM).also { it[i] = 1f }
        /** A unit vector with cosine [c] to e(i) (rest along e(DIM-1)). */
        fun near(i: Int, c: Float) = FloatArray(DIM).also { it[i] = c; it[DIM - 1] = sqrt(1 - c * c) }
    }

    private val vectors = mapOf(
        "Здоровье" to e(0),
        "Одежда" to e(1),
        "Приём специалистов" to e(0),
        "Сдать анализы" to near(0, (HIGH + MID) / 2),
        "Lamoda" to e(1),
        "Нечто странное" to e(2),
        // Equally close to health and clothing: no clear winner.
        "Ни то ни сё" to FloatArray(DIM).also { it[0] = 0.7f; it[1] = 0.7f; it[3] = 0.1414f }
    )

    private fun resolver(available: Boolean = true) =
        CategoryResolver(FakeEmbedder(vectors, available), high = HIGH, mid = MID)

    @Test fun lexicalWinsWithoutModel() = runTest {
        val fake = FakeEmbedder(vectors)
        val r = CategoryResolver(fake, high = HIGH, mid = MID).resolve("Медицинские услуги")
        assertEquals(Resolution(Resolution.Kind.GROUP, "health", "Здоровье"), r)
        assertEquals(0, fake.calls)
    }

    @Test fun highSimilarity_renames() = runTest {
        assertEquals(Resolution(Resolution.Kind.GROUP, "health", "Здоровье"), resolver().resolve("Приём специалистов"))
    }

    @Test fun midSimilarity_keepsTextTakesGroup() = runTest {
        assertEquals(Resolution(Resolution.Kind.AS_IS, "health", "Сдать анализы"), resolver().resolve("Сдать анализы"))
    }

    @Test fun properName_neverRenamed() = runTest {
        assertEquals(Resolution(Resolution.Kind.AS_IS, "clothing", "Lamoda"), resolver().resolve("Lamoda"))
    }

    @Test fun lowSimilarity_other() = runTest {
        assertEquals(Resolution(Resolution.Kind.AS_IS, CategoryGroups.OTHER, "Нечто странное"), resolver().resolve("Нечто странное"))
    }

    @Test fun smallMargin_noGroup() = runTest {
        assertEquals(Resolution(Resolution.Kind.AS_IS, CategoryGroups.OTHER, "Ни то ни сё"), resolver().resolve("Ни то ни сё"))
    }

    @Test fun embedderUnavailable_fallsBackToLexical() = runTest {
        val r = resolver(available = false)
        assertEquals(Resolution(Resolution.Kind.GROUP, "health", "Здоровье"), r.resolve("Медицинские услуги"))
        assertEquals(Resolution(Resolution.Kind.AS_IS, CategoryGroups.OTHER, "Приём специалистов"), r.resolve("Приём специалистов"))
    }

    @Test fun nullEmbedder_fallsBackToLexical() = runTest {
        assertEquals(Resolution.Kind.AS_IS, CategoryResolver(null).resolve("Приём специалистов").kind)
    }

    @Test fun suggest_lexicalFirstThenModel() = runTest {
        val s = resolver().suggest("медиц", limit = 3)
        assertEquals("health", s.first().key)
        assertTrue(s.size <= 3)
        assertEquals(s.size, s.map { it.key }.toSet().size)
    }

    @Test fun suggest_usesModelForUnknownText() = runTest {
        assertEquals("clothing", resolver().suggest("Lamoda").first().key)
    }
}
