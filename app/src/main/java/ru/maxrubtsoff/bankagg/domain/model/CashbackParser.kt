package ru.maxrubtsoff.bankagg.domain.model

/**
 * Turns raw OCR text from a cashback screenshot into structured draft lines.
 *
 * Pure domain logic (no Android, no Tesseract) so it is fully unit-testable: the
 * OCR engine is only responsible for producing text; everything downstream —
 * finding percents, naming categories, guessing the bank — happens here and is
 * covered by tests.
 *
 * It is deliberately conservative: a line without a plausible percent is ignored.
 * Naming the category (group, synonyms, model) is the resolver's job — this only
 * splits each line into its text and percent.
 */
object CashbackParser {

    /** One draft row extracted from the photo: the category text and its percent. */
    data class Line(
        val text: String,        // the line minus the percent token, trimmed
        val percent: Double,
        val rawText: String      // the original OCR line, for display/debugging
    )

    data class Result(
        val brand: BankBrand?,       // guessed bank, or null if unsure
        val lines: List<Line>
    )

    // 7%, 7 %, 7,5%, 7.5 % — a 1..2 digit integer part, optional fraction, then %.
    // (?<!\d) stops "250%" from matching as "50%": the number must not have a
    // digit immediately before it. Percent range is re-checked in parseLine.
    private val SPACES = Regex("""\s+""")

    private val PERCENT = Regex("""(?<!\d)(\d{1,2}(?:[.,]\d{1,2})?)\s*%""")

    /** Parse the whole OCR text: every line with a plausible percent, in order. */
    fun parse(rawText: String, brandHint: BankBrand? = null): Result {
        val all = rawText.lines().filter { it.isNotBlank() }
        // Only look for the bank in lines without a percent: an offer row like
        // "Ozon 5%" or "Яндекс Маркет 3%" names a partner, not the bank.
        val brand = brandHint ?: guessBank(all.filter { !PERCENT.containsMatchIn(it) }.joinToString("\n"))
        val out = ArrayList<Line>()
        var section = ""          // the last heading above the rows ("Кешбэк в Яндекс Такси")
        var lastWasRow = false
        var i = 0
        while (i < all.size) {
            val raw = all[i]
            // Two tiles side by side: "1% ы 5% ы" over "За все покупки Транспорт".
            val tiles = tileRow(raw, all.getOrNull(i + 1))
            if (tiles != null) {
                out += tiles
                lastWasRow = false
                i += 2
                continue
            }
            var line = parseLine(raw)
            if (line == null) {
                // A long name wraps: "5% Электроника и бытовая -" / "техника".
                val t = raw.trim()
                if (lastWasRow && t.firstOrNull()?.isLowerCase() == true && out.isNotEmpty()) {
                    val prev = out.last()
                    out[out.size - 1] = prev.copy(text = cleanCategory(prev.text.trimEnd('-', ' ') + " " + t))
                } else {
                    section = raw
                }
                lastWasRow = false
                i++
                continue
            }
            lastWasRow = true
            // Tiles put the percent above the name: "1%" / "За все покупки".
            if (line.text.isBlank() && i + 1 < all.size && !PERCENT.containsMatchIn(all[i + 1])) {
                line = line.copy(text = cleanCategory(all[i + 1]), rawText = "${raw.trim()} ${all[i + 1].trim()}")
                i++
            }
            out += taxiTariff(line, section) ?: line
            i++
        }
        return Result(brand, out)
    }

    /**
     * A line of percents only, one per tile, and the line of names under it:
     * names are split where a capitalised word starts. Null unless the counts agree.
     */
    private fun tileRow(raw: String, next: String?): List<Line>? {
        val percents = PERCENT.findAll(raw).toList()
        if (percents.size < 2 || next == null || PERCENT.containsMatchIn(next)) return null
        if (raw.replace(PERCENT, " ").count { it.isLetter() } > percents.size * 2) return null
        val names = mutableListOf<MutableList<String>>()
        next.split(SPACES).filter { w -> w.any { it.isLetter() } }.forEach { w ->
            if (w.first().isUpperCase() || names.isEmpty()) names += mutableListOf(w) else names.last() += w
        }
        if (names.size != percents.size) return null
        return percents.zip(names).mapNotNull { (m, words) ->
            val p = m.groupValues[1].replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it <= 100 } ?: return@mapNotNull null
            Line(cleanCategory(words.joinToString(" ")), p, "${m.value} ${words.joinToString(" ")}")
        }
    }


    /**
     * Rows under a taxi heading ("Кешбэк в Яндекс Такси") are its tariffs — one
     * offer: "Комфорт+ 5%" → "Яндекс Такси 5%". A row that names a category of
     * its own ("Такси и каршеринг") is left alone.
     */
    private fun taxiTariff(line: Line, section: String): Line? {
        val head = CategoryMatcher.normalize(section)
        if (!head.contains("такси") || !head.contains("кешбэк") && !head.contains("кэшбэк")) return null
        if (CategoryMatcher.normalize(line.text).contains("такси")) return null
        return line.copy(text = if (head.contains("яндекс")) "Яндекс Такси" else "Такси")
    }

    private val MONTH_STEMS = listOf(
        "январ", "феврал", "март", "апрел", "ма[йяе]", "июн", "июл", "август", "сентябр", "октябр", "ноябр", "декабр"
    ).map { Regex("""(?<![\p{L}])$it\p{L}*""", RegexOption.IGNORE_CASE) }

    /** Months named in the text ("в октябре", "на октябрь", "В сентябре"), 1..12, in order of appearance. */
    fun monthsMentioned(rawText: String): List<Int> {
        val found = MONTH_STEMS.mapIndexedNotNull { i, re -> re.find(rawText)?.let { it.range.first to i + 1 } }
        return found.sortedBy { it.first }.map { it.second }
    }

    /** A single OCR line → a draft, or null if it has no usable percent. */
    fun parseLine(raw: String): Line? {
        val m = PERCENT.find(raw) ?: return null
        // "-10% на путешествия" is a discount, not cashback.
        if (m.range.first > 0 && raw[m.range.first - 1] in "-−–") return null
        val percent = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        if (percent <= 0.0 || percent > 100.0) return null

        val textPart = raw.replace(m.value, " ").trim().replace(SPACES, " ")
        return Line(text = cleanCategory(textPart), percent = percent, rawText = raw.trim())
    }

    /**
     * Strip OCR noise that clings to the edges of a category name. The small
     * category icon on the left and the arrow/badge on the right get read as
     * stray characters (". Яндекс Лавка /", "э Комфорт @", "э Унта @"), which
     * otherwise end up shown verbatim as the category text.
     *
     * We drop leading and trailing symbol runs and a lone one-letter token at
     * either edge (an icon misread as a single letter), but keep a trailing "+"
     * ("Комфорт+") and any punctuation inside the name ("Кафе, бары").
     */
    private fun cleanCategory(text: String): String {
        var s = text.trim()
        // Edge symbols — dots, slashes, @, arrows — are never the start/end of a real name.
        s = s.trimStart { !it.isLetterOrDigit() }
        s = s.trimEnd { !it.isLetterOrDigit() && it != '+' }
        // A single letter standing alone at an edge is an icon misread, not a word.
        val words = s.split(' ').filter { it.isNotEmpty() }
        if (words.size > 1) {
            val dropFirst = words.first().length == 1 && words.first()[0].isLetter()
            val dropLast = words.last().length == 1 && words.last()[0].isLetter()
            val from = if (dropFirst) 1 else 0
            val to = words.size - if (dropLast) 1 else 0
            if (from < to) s = words.subList(from, to).joinToString(" ")
        }
        return s.trim()
    }

    /** Pick the bank whose title/keywords appear in the text, if any is clear. */
    fun guessBank(text: String): BankBrand? {
        val t = CategoryMatcher.normalize(text)
        if (t.isEmpty()) return null
        // Longest keyword first: "альфа бизнес" must win over "альфа",
        // "т бизнес" over "т банк".
        return KEYWORDS.firstOrNull { (kw, _) -> t.contains(kw) }?.second
    }

    // Not cached: the user's own banks join the list as they're added (F-22).
    private val KEYWORDS: List<Pair<String, BankBrand>> get() =
        BankBrand.all
            .flatMap { b -> bankKeywords(b).filter { it.isNotEmpty() }.map { it to b } }
            .sortedByDescending { it.first.length }

    /** Normalised keywords that identify a bank in OCR text. */
    private fun bankKeywords(b: BankBrand): List<String> {
        val title = CategoryMatcher.normalize(b.title)
        return (listOf(title) + b.aliases).map { CategoryMatcher.normalize(it) }
    }
}
