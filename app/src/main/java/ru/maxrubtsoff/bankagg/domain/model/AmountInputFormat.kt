package ru.maxrubtsoff.bankagg.domain.model

/**
 * Digit grouping while typing an amount: "2000000" shows as "2 000 000", so
 * thousands and millions read at a glance. The typed text itself stays plain
 * digits; only the display gets separators, with cursor positions mapped both
 * ways so editing never trips over a space.
 *
 * Only an all-digit integer part (before the first ',' or '.') is grouped; any
 * other text is shown as typed.
 */
object AmountInputFormat {

    /** The same no-break space [Money.format] groups with. */
    const val SEP = ' '

    fun format(raw: String): String {
        val intLen = intLength(raw) ?: return raw
        val sb = StringBuilder(raw.length + intLen / 3)
        for (i in raw.indices) {
            if (i in 1 until intLen && (intLen - i) % 3 == 0) sb.append(SEP)
            sb.append(raw[i])
        }
        return sb.toString()
    }

    /** Cursor at [offset] in the typed text → cursor in the shown text. */
    fun toShown(raw: String, offset: Int): Int {
        val intLen = intLength(raw) ?: return offset
        return offset + separatorsBefore(intLen, minOf(offset, intLen))
    }

    /** Cursor at [shownOffset] in the shown text → cursor in the typed text. */
    fun toRaw(raw: String, shownOffset: Int): Int {
        val shown = format(raw)
        if (shown.length == raw.length) return shownOffset.coerceIn(0, raw.length)
        val seps = shown.take(shownOffset).count { it == SEP }
        return (shownOffset - seps).coerceIn(0, raw.length)
    }

    /** Separators placed before raw position [offset] (a separator sits before digit j when (intLen − j) % 3 == 0). */
    private fun separatorsBefore(intLen: Int, offset: Int): Int =
        (1 until offset).count { (intLen - it) % 3 == 0 }

    /** Length of the integer part, or null when it isn't plain digits (then nothing is grouped). */
    private fun intLength(raw: String): Int? {
        val cut = raw.indexOfFirst { it == ',' || it == '.' }.let { if (it < 0) raw.length else it }
        return if (raw.take(cut).all { it.isDigit() }) cut else null
    }
}
