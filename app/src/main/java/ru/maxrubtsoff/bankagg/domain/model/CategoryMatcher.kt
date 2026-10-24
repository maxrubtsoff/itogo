package ru.maxrubtsoff.bankagg.domain.model

import kotlin.math.max

/**
 * Text helpers for category matching: normalisation and an edit-distance
 * similarity that tolerates OCR typos. Pure domain logic, covered by tests;
 * used by [LexicalResolver], [CashbackIdentity] and [ScreenshotFileName].
 */
object CategoryMatcher {

    /** Lowercase, unify ё/е, keep only letters/digits/spaces, collapse spaces. */
    fun normalize(raw: String): String =
        raw.lowercase()
            .replace('ё', 'е')
            .map { if (it.isLetterOrDigit() || it.isWhitespace()) it else ' ' }
            .joinToString("")
            .trim()
            .replace(Regex("\\s+"), " ")

    /** 1.0 for identical strings, down to 0.0, from edit distance. */
    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val longest = max(a.length, b.length)
        if (longest == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / longest
    }

    /** Edit distance (insertions, deletions, substitutions). */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(
                    prev[j] + 1,        // deletion
                    cur[j - 1] + 1,     // insertion
                    prev[j - 1] + cost  // substitution
                )
            }
            val tmp = prev; prev = cur; cur = tmp
        }
        return prev[b.length]
    }
}
