package ru.maxrubtsoff.bankagg.domain.model

/** What a shared screenshot shows, so it opens in the right place. */
enum class ShotKind {
    CASHBACK, DEPOSIT;

    companion object {
        private val CASHBACK_WORDS = listOf("кешбэк", "кэшбэк", "кешбек", "кэшбек", "категори", "выгода", "баллами", "бонус")
        private val DEPOSIT_WORDS = listOf("вклад", "ставк", "годовых", "срок", "цфа", "погашени", "депозит", "доход", "капитализ")
        private val PERCENT_LINE = Regex("""\d\s*%""")

        /**
         * By the words on the screen; a list of several percents leans to cashback.
         * Null when it can't tell — then the user is asked.
         */
        fun guess(text: String): ShotKind? {
            val n = CategoryMatcher.normalize(text)
            val percentLines = text.lines().count { PERCENT_LINE.containsMatchIn(it) }
            val cashback = CASHBACK_WORDS.count { n.contains(it) } + if (percentLines >= 3) 1 else 0
            val deposit = DEPOSIT_WORDS.count { n.contains(it) }
            return when {
                cashback > deposit -> CASHBACK
                deposit > cashback -> DEPOSIT
                else -> null
            }
        }
    }
}
