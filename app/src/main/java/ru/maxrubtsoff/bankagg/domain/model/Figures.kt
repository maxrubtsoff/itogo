package ru.maxrubtsoff.bankagg.domain.model

/**
 * Tells a standalone figure from a phrase (U-73). A figure — a sum, percent,
 * rate, term — is set in the amount font; a phrase or a date stays in text.
 */
object Figures {

    private const val SPACE = "[   ]"
    private const val SIGN = "₽|[$€£¥₸₺₹₩₴₾]|[A-Z]{3}"
    private const val UNIT = "%|$SIGN|мес\\.?|дн\\.?|д\\.|г\\.|год|года|лет|шт\\.?|тыс|млн|млрд"

    private val FIGURE = Regex(
        "[+\\-−]?\\d+(?:$SPACE\\d{3})*(?:[,.]\\d+)?" +
            "(?:$SPACE?(?:$UNIT)(?:$SPACE?(?:$SIGN))?)?"
    )

    /** True when the whole text is one value. */
    fun isFigure(text: String): Boolean = FIGURE.matches(text.trim())
}
