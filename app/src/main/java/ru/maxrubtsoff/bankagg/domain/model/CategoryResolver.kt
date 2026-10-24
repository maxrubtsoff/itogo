package ru.maxrubtsoff.bankagg.domain.model

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.maxrubtsoff.bankagg.domain.repository.TextEmbedder

/**
 * Resolves category text to a group: dictionary first ([LexicalResolver]), then
 * the offline similarity model for what the dictionary doesn't know.
 *
 * Two thresholds on the best cosine against a group's name and synonyms, and
 * the best must beat the runner-up by [margin]:
 * - ≥ [high] — the text *is* that category → renamed to the group's name;
 * - ≥ [mid]  — related, but may be a brand/partner → text kept, group lends its icon.
 * Proper names ([LexicalResolver.looksLikeProperName]) are never renamed.
 * Without a model (null or unavailable) only the dictionary is used.
 */
class CategoryResolver(
    private val embedder: TextEmbedder?,
    private val high: Float = HIGH,
    private val mid: Float = MID,
    private val margin: Float = MARGIN
) {
    private val mutex = Mutex()
    private var groupVectors: List<Pair<CategoryGroup, FloatArray>>? = null

    suspend fun resolve(text: String): Resolution {
        val lexical = LexicalResolver.resolve(text)
        if (lexical.kind != Resolution.Kind.AS_IS) return lexical
        // The dictionary already knows this shop's group.
        if (lexical.groupKey != CategoryGroups.OTHER) return lexical
        val ranked = scores(lexical.displayName) ?: return lexical
        val (group, score) = ranked.firstOrNull() ?: return lexical
        // The model scores everything fairly close; only a clear winner counts.
        if (score - (ranked.getOrNull(1)?.second ?: 0f) < margin) return lexical
        return when {
            score >= high && !LexicalResolver.looksLikeProperName(lexical.displayName) ->
                Resolution(Resolution.Kind.GROUP, group.key, group.name)
            score >= mid -> Resolution(Resolution.Kind.AS_IS, group.key, lexical.displayName)
            else -> lexical
        }
    }

    /** Picker suggestions for [query]: dictionary hits first, then the model's nearest groups. */
    suspend fun suggest(query: String, limit: Int = 3): List<CategoryGroup> {
        val q = CategoryMatcher.normalize(query)
        if (q.isEmpty()) return emptyList()
        val lexical = LexicalResolver.resolve(query)
            .takeIf { it.kind == Resolution.Kind.GROUP }?.let { CategoryGroups.byKey(it.groupKey) }
        val contains = CategoryGroups.all.filter { g ->
            g.key != CategoryGroups.OTHER &&
                (listOf(g.name) + g.synonyms).any { CategoryMatcher.normalize(it).contains(q) }
        }
        val model = scores(query.trim()).orEmpty().filter { it.second >= mid }.map { it.first }
        return (listOfNotNull(lexical) + contains + model).distinctBy { it.key }.take(limit)
    }

    /** Groups by descending best cosine to [text], or null without a model. */
    private suspend fun scores(text: String): List<Pair<CategoryGroup, Float>>? {
        val groups = vectors() ?: return null
        val q = embedder?.embed(listOf(text))?.firstOrNull() ?: return null
        return groups
            .groupBy({ it.first }, { dot(q, it.second) })
            .map { (g, s) -> g to s.max() }
            .sortedByDescending { it.second }
    }

    private suspend fun vectors(): List<Pair<CategoryGroup, FloatArray>>? = mutex.withLock {
        groupVectors?.let { return@withLock it }
        val e = embedder ?: return@withLock null
        val texts = CategoryGroups.all
            .filter { it.key != CategoryGroups.OTHER }
            .flatMap { g -> (listOf(g.name) + g.synonyms).map { g to it } }
        val vs = e.embed(texts.map { it.second }) ?: return@withLock null
        texts.map { it.first }.zip(vs).also { groupVectors = it }
    }

    private fun dot(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    /**
     * Calibrated on the real model against test resource cashback_phrases.tsv
     * (CategoryCalibrationTest, sweep in build/calibration.txt). rubert-tiny2
     * scores almost any pair 0.7–0.9 and happily maps brands to categories
     * (Кинопоиск → Развлечения at 0.93, Перекрёсток → Развлечения at 0.91), so
     * renaming is kept near-exact and the dictionary does the real work: with
     * these values 0 of 20 brands are renamed and every rename is correct.
     */
    companion object {
        const val HIGH = 0.95f
        const val MID = 0.78f
        const val MARGIN = 0.06f
    }
}
