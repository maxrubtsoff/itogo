package ru.maxrubtsoff.bankagg.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.data.embed.OnnxEmbedderCore
import ru.maxrubtsoff.bankagg.data.embed.WordPieceTokenizer
import ru.maxrubtsoff.bankagg.domain.model.CategoryResolver
import ru.maxrubtsoff.bankagg.domain.model.Resolution
import ru.maxrubtsoff.bankagg.domain.repository.TextEmbedder
import java.io.File

/**
 * Runs the real bundled model over real bank phrasings (test resource
 * cashback_phrases.tsv). Hard rules: a brand/partner (ASIS) is never renamed
 * into a group, and a phrase renamed into a group lands in the right one.
 * Also writes a threshold sweep to build/calibration.txt for tuning.
 */
class CategoryCalibrationTest {

    private data class Row(val phrase: String, val expect: String, val key: String)

    private val rows: List<Row> = javaClass.classLoader!!.getResource("cashback_phrases.tsv")!!
        .readText().lines()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.split('\t').let { p -> Row(p[0], p[1], p[2]) } }

    /** Memoising adapter: the sweep re-asks the same texts many times. */
    private class Cached(private val core: OnnxEmbedderCore) : TextEmbedder {
        private val memo = HashMap<String, FloatArray>()
        override suspend fun embed(texts: List<String>): List<FloatArray> {
            val missing = texts.filter { it !in memo }.distinct()
            if (missing.isNotEmpty()) core.embed(missing).forEachIndexed { i, v -> memo[missing[i]] = v }
            return texts.map { memo.getValue(it) }
        }
    }

    private data class Score(val violations: List<String>, val caught: Int, val expectedGroups: Int)

    private suspend fun score(resolver: CategoryResolver): Score {
        val violations = mutableListOf<String>()
        var caught = 0
        for (r in rows) {
            val res = resolver.resolve(r.phrase)
            when (r.expect) {
                "ASIS" -> if (res.kind == Resolution.Kind.GROUP) violations += "${r.phrase} → ${res.displayName}"
                "GROUP" -> if (res.kind == Resolution.Kind.GROUP) {
                    if (res.groupKey == r.key) caught++ else violations += "${r.phrase} → ${res.groupKey} (ждали ${r.key})"
                }
            }
        }
        return Score(violations, caught, rows.count { it.expect == "GROUP" })
    }

    @Test fun realModel_neverRenamesBrands_andRenamesCorrectly() = runBlocking {
        val tok = TestAssets.vocab.bufferedReader().useLines { WordPieceTokenizer.fromVocabLines(it) }
        OnnxEmbedderCore(TestAssets.model.readBytes(), tok).use { core ->
            val emb = Cached(core)
            val report = StringBuilder()

            val defaults = CategoryResolver(emb)
            for (r in rows) {
                val res = defaults.resolve(r.phrase)
                report.appendLine("${r.expect}\t${r.phrase}\t→ ${res.kind} ${res.groupKey} «${res.displayName}»")
            }
            report.appendLine()
            for (h in (70..95 step 2).map { it / 100f }) for (m in (50..90 step 4).map { it / 100f }) {
                if (m > h) continue
                val s = score(CategoryResolver(emb, high = h, mid = m))
                report.appendLine("high=$h mid=$m caught=${s.caught}/${s.expectedGroups} violations=${s.violations.size} ${s.violations.take(3)}")
            }
            File("build/calibration.txt").writeText(report.toString())

            val s = score(defaults)
            println("calibration: caught ${s.caught}/${s.expectedGroups}")
            assertTrue("violations: ${s.violations}", s.violations.isEmpty())
        }
    }
}
