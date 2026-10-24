package ru.maxrubtsoff.bankagg.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.maxrubtsoff.bankagg.data.embed.WordPieceTokenizer

/**
 * Reference ids come from the HF tokenizer of cointegrated/rubert-tiny2
 * (printed by tools/embedder/export_rubert_tiny2.py):
 *   'Медицинские услуги'   [2, 81859, 23991, 3]
 *   'Связь, интернет и ТВ' [2, 58241, 16, 16234, 320, 13409, 3]
 *   'Lamoda'               [2, 14035, 4959, 3]
 */
class WordPieceTokenizerTest {

    private val tok = TestAssets.vocab.bufferedReader().useLines { WordPieceTokenizer.fromVocabLines(it) }

    @Test fun cyrillicWords() =
        assertArrayEquals(intArrayOf(2, 81859, 23991, 3), tok.encode("Медицинские услуги"))

    @Test fun punctuationIsSeparateToken() =
        assertArrayEquals(intArrayOf(2, 58241, 16, 16234, 320, 13409, 3), tok.encode("Связь, интернет и ТВ"))

    @Test fun subwordPieces() =
        assertArrayEquals(intArrayOf(2, 14035, 4959, 3), tok.encode("Lamoda"))

    @Test fun truncatesToMaxLenEndingWithSep() {
        val ids = tok.encode("слово ".repeat(200), maxLen = 64)
        assertEquals(64, ids.size)
        assertEquals(2, ids.first())
        assertEquals(3, ids.last())
    }
}
