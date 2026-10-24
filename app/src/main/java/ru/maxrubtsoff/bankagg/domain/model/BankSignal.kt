package ru.maxrubtsoff.bankagg.domain.model

/**
 * Does a notification from an unknown app read like a bank operation (B-25)?
 * Stricter than the parser on purpose: asking "is this a bank?" about a chat
 * message or a file download is worse than missing a rare bank.
 *
 * Needs an amount written with money next to it (a currency sign or code) and
 * at least one more bank word: an operation, a balance, or a card/account.
 * Apps that are certainly not banks (messengers, mail, system) never qualify.
 */
object BankSignal {

    fun looksLikeBank(pkg: String, title: String?, text: String): Boolean {
        if (isNeverBank(pkg)) return false
        val all = listOfNotNull(title, text).joinToString(" ")
        return hasMoney(all) && hasBankWord(all)
    }

    fun isNeverBank(pkg: String): Boolean =
        pkg in NEVER || NEVER_PREFIXES.any { pkg.startsWith(it) }

    /** A number with a currency sign or code right before or after it. */
    fun hasMoney(text: String): Boolean = MONEY_AFTER.containsMatchIn(text) || MONEY_BEFORE.containsMatchIn(text)

    fun hasBankWord(text: String): Boolean = BANK_WORDS.containsMatchIn(text.lowercase())

    // Digits glued to letters or underscores (file names, codes) don't count as an amount.
    private const val NUMBER = """(?<![\p{L}\d_.,/:-])\d{1,3}(?:[  ,]\d{3})*(?:[.,]\d{1,2})?(?![\d_])|(?<![\p{L}\d_.,/:-])\d+(?:[.,]\d{1,2})?(?![\d_])"""

    private val MARKERS: List<String> = (
        Currencies.all.flatMap { listOfNotNull(it.code, it.symbol) } + listOf("рублей", "рубля", "рубль", "руб.", "руб", "р.", "р", "RUR")
    ).distinct().sortedByDescending { it.length }

    private val MARKER = MARKERS.joinToString("|") { Regex.escape(it) }

    private val MONEY_AFTER = Regex("(?:$NUMBER)\\s?(?:$MARKER)(?![\\p{L}\\d])", RegexOption.IGNORE_CASE)
    private val MONEY_BEFORE = Regex("(?<![\\p{L}\\d])(?:$MARKER)\\s?(?:$NUMBER)", RegexOption.IGNORE_CASE)

    private val BANK_WORDS = Regex(
        "покупк|оплат|списан|зачислен|пополнен|перевод|снят|поступлен|возврат|кэшб[эе]к|кешб[эе]к|плат[её]ж" +
            "|баланс|доступно|остаток|карт[аеыуо]|сч[её]т|[*•·]\\s?\\d{4}"
    )

    private val NEVER = setOf(
        "android",
        "ru.oneme.app",                // MAX
        "org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram",
        "com.whatsapp", "com.whatsapp.w4b",
        "com.vkontakte.android", "com.vk.im",
        "com.viber.voip",
        "com.google.android.gm", "ru.mail.mailapp", "ru.yandex.mail",
        "com.google.android.apps.photos", "com.google.android.youtube"
    )
    private val NEVER_PREFIXES = listOf("com.android.", "com.google.android.apps.messaging", "com.samsung.android.", "com.miui.", "com.xiaomi.")
}
