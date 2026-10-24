package ru.maxrubtsoff.bankagg.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.data.embed.OnnxEmbedderCore
import ru.maxrubtsoff.bankagg.data.embed.WordPieceTokenizer
import kotlin.math.sqrt

class OnnxEmbedderCoreTest {

    @Test fun embedsNormalisedVectorsWithMeaningfulSimilarity() {
        val tok = TestAssets.vocab.bufferedReader().useLines { WordPieceTokenizer.fromVocabLines(it) }
        OnnxEmbedderCore(TestAssets.model.readBytes(), tok).use { core ->
            val v = core.embed(listOf("Здоровье", "Медицинские услуги", "АЗС"))
            assertEquals(3, v.size)
            v.forEach { e ->
                assertEquals(312, e.size)
                assertEquals(1.0, sqrt(e.sumOf { (it * it).toDouble() }), 1e-3)
            }
            fun cos(a: FloatArray, b: FloatArray) = a.indices.sumOf { (a[it] * b[it]).toDouble() }
            assertTrue(cos(v[0], v[1]) > cos(v[0], v[2]))
        }
    }
}
