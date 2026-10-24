package ru.maxrubtsoff.bankagg.domain.model

/**
 * Dictionary-only resolution of category text to a [CategoryGroup]: group
 * names and synonyms, tolerant of OCR typos, with long bank phrasings split
 * into parts ("Связь, интернет и ТВ" → "Связь и интернет"). Pure and
 * deterministic; the similarity model ([CategoryResolver]) only runs when
 * this step returns [Resolution.Kind.AS_IS].
 */
object LexicalResolver {

    /** Below this many letters the text is treated as OCR noise. */
    private const val MIN_LETTERS = 3

    /** Edit-distance ratio that still counts as the same phrase (OCR typos), for 7+ letters. */
    private const val TYPO_SIMILARITY = 0.85

    /** 5–6 letters: one wrong letter is still a typo. Shorter words ("кафе"/"кофе") must match exactly. */
    private val SHORT_TYPO_LENGTH = 5..6

    /**
     * Tesseract (rus+eng) often reads a Cyrillic letter as its Latin twin
     * ("Aптеки", "A3C"). Folded only in words made entirely of such letters
     * plus Cyrillic, so real Latin names ("Lamoda", "Ozon") are left alone.
     */
    private val LOOK_ALIKES: Map<Char, Char> = mapOf(
        'a' to 'а', 'b' to 'в', 'c' to 'с', 'e' to 'е', 'h' to 'н', 'k' to 'к', 'm' to 'м',
        'o' to 'о', 'p' to 'р', 't' to 'т', 'x' to 'х', 'y' to 'у', '3' to 'з', '0' to 'о'
    )

    private val PARTS = Regex("""\s*(?:,|/|&|\+|\sи\s)\s*""")

    /** (normalised phrase, group) for every group name and synonym. */
    private val phrases: List<Pair<String, CategoryGroup>> =
        CategoryGroups.all.flatMap { g ->
            (listOf(g.name) + g.synonyms).map { CategoryMatcher.normalize(it) to g }
        }

    fun resolve(text: String): Resolution {
        val clean = text.trim()
        // Count letters after folding look-alikes, so "A3C" (= АЗС) isn't taken for noise.
        if (fold(CategoryMatcher.normalize(clean)).count { it.isLetter() } < MIN_LETTERS) {
            return Resolution(Resolution.Kind.EMPTY, CategoryGroups.OTHER, "")
        }
        // A known shop keeps its name; its group only lends the icon.
        CategoryGroups.brandHint(clean)?.let { return Resolution(Resolution.Kind.AS_IS, it, clean) }
        val group = matchPhrase(CategoryMatcher.normalize(clean))
            ?: clean.split(PARTS).asSequence()
                .map { CategoryMatcher.normalize(it) }
                .filter { it.isNotEmpty() }
                .firstNotNullOfOrNull { matchPhrase(it) }
        return if (group != null) {
            Resolution(Resolution.Kind.GROUP, group.key, group.name)
        } else {
            Resolution(Resolution.Kind.AS_IS, CategoryGroups.OTHER, clean)
        }
    }

    /**
     * Heuristic for brand/partner names, which must never be renamed into a
     * group: no Cyrillic at all ("Lamoda"), quoted («Пятёрочка»), or a
     * capitalised word after the first one ("Детский Мир").
     */
    /**
     * Text a bank wrote cleanly enough to show as is: Cyrillic letters and
     * spaces/punctuation only (no Latin look-alikes or digits left by OCR).
     */
    fun isCleanWording(text: String): Boolean {
        val t = text.trim()
        return t.count { it.isLetter() } >= MIN_LETTERS &&
            t.all { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' || it == ' ' || it == '-' || it == ',' }
    }

    fun looksLikeProperName(text: String): Boolean {
        val t = text.trim()
        if (t.none { it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }) return true
        if (t.any { it == '«' || it == '»' || it == '"' }) return true
        return t.split(Regex("\\s+")).drop(1).any { w -> w.firstOrNull()?.isUpperCase() == true }
    }

    private fun matchPhrase(q: String): CategoryGroup? {
        if (q.isEmpty()) return null
        return matchExactOrTypo(q) ?: fold(q).takeIf { it != q }?.let { matchExactOrTypo(it) }
    }

    private fun matchExactOrTypo(q: String): CategoryGroup? {
        phrases.firstOrNull { it.first == q }?.let { return it.second }
        return phrases
            .filter { isTypo(q, it.first) }
            .maxByOrNull { CategoryMatcher.similarity(q, it.first) }
            ?.second
    }

    private fun isTypo(q: String, phrase: String): Boolean {
        val longest = maxOf(q.length, phrase.length)
        return when {
            longest >= 7 -> CategoryMatcher.similarity(q, phrase) >= TYPO_SIMILARITY
            longest in SHORT_TYPO_LENGTH -> CategoryMatcher.levenshtein(q, phrase) <= 1
            else -> false
        }
    }

    /** Swap Latin look-alikes for Cyrillic in words that consist only of look-alikes and Cyrillic. */
    private fun fold(q: String): String = q.split(' ').joinToString(" ") { w ->
        val foldable = w.any { it.isLetter() } &&
            w.all { it in LOOK_ALIKES || it in 'а'..'я' || it == 'ё' || (it.isDigit() && it in LOOK_ALIKES) }
        if (foldable) w.map { LOOK_ALIKES[it] ?: it }.joinToString("") else w
    }
}
