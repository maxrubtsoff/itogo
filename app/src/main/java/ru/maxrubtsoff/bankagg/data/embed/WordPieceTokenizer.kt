package ru.maxrubtsoff.bankagg.data.embed

/**
 * BERT WordPiece tokenizer matching the HF `BertTokenizer` config of
 * cointegrated/rubert-tiny2: cased (no lower-casing, no accent stripping),
 * whitespace + punctuation pre-split, greedy longest-match with `##` pieces.
 * Pure Kotlin so it is unit-tested on the JVM against reference ids.
 */
class WordPieceTokenizer(private val vocab: Map<String, Int>) {

    private val cls = vocab.getValue("[CLS]")
    private val sep = vocab.getValue("[SEP]")
    private val unk = vocab.getValue("[UNK]")

    /** `[CLS] tokens… [SEP]`, truncated to [maxLen] ids (still ending in `[SEP]`). */
    fun encode(text: String, maxLen: Int = 64): IntArray {
        val ids = ArrayList<Int>()
        ids += cls
        for (word in basicTokens(text)) {
            ids += wordPiece(word)
            if (ids.size >= maxLen - 1) break
        }
        val body = ids.take(maxLen - 1)
        return (body + sep).toIntArray()
    }

    private fun basicTokens(text: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        fun flush() { if (cur.isNotEmpty()) { out += cur.toString(); cur.clear() } }
        for (ch in text) {
            when {
                ch.code == 0 || ch.code == 0xFFFD || (Character.isISOControl(ch) && !ch.isWhitespace()) -> Unit
                ch.isWhitespace() -> flush()
                isPunctuation(ch) -> { flush(); out += ch.toString() }
                else -> cur.append(ch)
            }
        }
        flush()
        return out
    }

    private fun wordPiece(word: String): List<Int> {
        if (word.length > MAX_WORD_CHARS) return listOf(unk)
        val pieces = ArrayList<Int>()
        var start = 0
        while (start < word.length) {
            var end = word.length
            var found: Int? = null
            while (start < end) {
                val sub = (if (start > 0) "##" else "") + word.substring(start, end)
                val id = vocab[sub]
                if (id != null) { found = id; break }
                end--
            }
            if (found == null) return listOf(unk)
            pieces += found
            start = end
        }
        return pieces
    }

    private fun isPunctuation(ch: Char): Boolean {
        val c = ch.code
        if (c in 33..47 || c in 58..64 || c in 91..96 || c in 123..126) return true
        return when (Character.getType(ch).toByte()) {
            Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION,
            Character.START_PUNCTUATION, Character.END_PUNCTUATION,
            Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
            Character.OTHER_PUNCTUATION -> true
            else -> false
        }
    }

    companion object {
        private const val MAX_WORD_CHARS = 100

        /** One token per line, id = line number (the `vocab.txt` format). */
        fun fromVocabLines(lines: Sequence<String>): WordPieceTokenizer =
            WordPieceTokenizer(lines.withIndex().associate { (i, t) -> t to i })
    }
}
