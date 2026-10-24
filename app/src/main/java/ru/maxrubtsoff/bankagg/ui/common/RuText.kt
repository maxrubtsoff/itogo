package ru.maxrubtsoff.bankagg.ui.common

/** Russian typography helpers for UI strings. */
object RuText {

    const val NBSP = ' '

    /** " · " with no-break spaces: the dot never starts or ends a line. */
    const val DOT = " · "

    private val SHORT = setOf(
        "в", "к", "с", "у", "о", "и", "а", "на", "по", "за", "до", "от", "из", "не", "во", "со", "ко"
    )

    /**
     * Glues short prepositions and conjunctions ("в", "на", "и"…) to the next
     * word with a no-break space, so they never hang at the end of a line.
     */
    fun nbsp(s: String): String {
        if (s.isEmpty()) return s
        val out = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c.isLetter() && (i == 0 || !s[i - 1].isLetterOrDigit())) {
                var j = i
                while (j < s.length && s[j].isLetter()) j++
                val word = s.substring(i, j)
                out.append(word)
                if (j < s.length && s[j] == ' ' && word.lowercase() in SHORT) {
                    out.append(NBSP)
                    i = j + 1
                } else {
                    i = j
                }
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }
}

/** Shorthand for [RuText.nbsp]. */
fun String.nbsp(): String = RuText.nbsp(this)
