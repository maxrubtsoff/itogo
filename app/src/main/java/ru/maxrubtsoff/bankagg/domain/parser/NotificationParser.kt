package ru.maxrubtsoff.bankagg.domain.parser

import ru.maxrubtsoff.bankagg.domain.model.Currencies
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.TransactionKind

/**
 * On-device notification parser. Extracts amount, currency, kind and the
 * running balance from a bank push. Conservative: when a field can't be read
 * it stays null. The raw text is kept in the event log so an improved parser
 * can be re-run later.
 */
/** Where the money went: out of the account or into it (B-45). */
enum class OpDirection { IN, OUT }

data class ParsedOp(
    val amount: Money,            // signed; negative = money left the account
    val currency: String,
    val kind: TransactionKind,
    /** Read separately from [kind]: the sign of [amount] comes from it. */
    val direction: OpDirection,
    val balance: Money?,         // running balance if present
    val counterparty: String?
)

object NotificationParser {

    /**
     * Bumped whenever the parser starts reading pushes differently: the log is
     * then re-read once and stored operations are fixed ([RepairFromJournal]).
     * 2 — direction apart from the kind, advertisements are not operations
     * (B-45, B-46).
     * 3 — amount only with a currency mark, card/phone masks (B-82…B-84).
     * 4 — Альфа-Бизнес, списание по п/п без знака валюты (B-107).
     */
    const val VERSION = 4


    /**
     * A number as banks write it: "5 000", "107 409,81", "1,234.56", "1.234,56",
     * "500". Groups may be split by a space, NBSP or narrow NBSP. Never starts
     * right after a digit, a card-mask star or a time colon, and never runs into
     * a time ("10:15").
     */
    private const val NUM =
        """(?<![\d*•:.,])((?:\d{1,3}(?:[ \u00A0\u202F]\d{3})+|\d{1,3}(?:,\d{3})+(?=\.\d)|\d{1,3}(?:\.\d{3})+(?=,\d)|\d+)(?:[.,]\d{1,2})?)(?![\d:]|[.,]\d)"""

    /** Currency marks by code. Longer marks first so "бел. руб." wins over "руб". */
    private val CURRENCY_MARKS: List<Pair<String, String>> = buildList {
        add("бел. руб." to "BYN"); add("бел.руб." to "BYN"); add("бел руб" to "BYN")
        add("рублей" to "RUB"); add("рубля" to "RUB"); add("рубль" to "RUB")
        add("руб." to "RUB"); add("руб" to "RUB"); add("rub" to "RUB"); add("rur" to "RUB")
        add("₽" to "RUB"); add("р." to "RUB"); add("р" to "RUB")
        add("$" to "USD"); add("€" to "EUR"); add("¥" to "CNY"); add("юан" to "CNY")
        add("br" to "BYN")
        Currencies.all.forEach { c ->
            add(c.code.lowercase() to c.code)
            c.symbol?.let { s -> if (s.length == 1 && none { it.first == s }) add(s to c.code) }
        }
    }.sortedByDescending { it.first.length }

    private val MARK_ALT = CURRENCY_MARKS.joinToString("|") { Regex.escape(it.first) }

    /** Amount with a currency right after it: "500 ₽", "500р", "10 USD". */
    private val AMOUNT_THEN_CCY = Regex("""$NUM[ \u00A0\u202F]?($MARK_ALT)(?![a-zа-яё])""", RegexOption.IGNORE_CASE)

    /** Currency right before the amount: "$100", "USD 100". */
    private val CCY_THEN_AMOUNT = Regex("""(?<![a-zа-яё])($MARK_ALT)[ \u00A0\u202F]?$NUM""", RegexOption.IGNORE_CASE)

    /**
     * Card and account masks — never an amount: a star/bullet mask ("*1234",
     * "•• 1234", "·· 2026"), a card brand glued to four digits ("ECMC2026",
     * "MIR-1234") or an account/card number ("Счёт 4081 7810"). "Оплата картой
     * 5000 ₽" and "на счёт 5000 ₽" are not masks: a currency mark follows.
     */
    private val MASK = Regex(
        """[*•·∙‧]+[  ]?\d{2,}""" +
            """|(?<![a-zа-яё])(?:ecmc|mc|maes|maestro|mastercard|master|mir|visa|мир)[-*   ·•]?\d{4}(?!\d)""" +
            """|(?:сч[её]т|карт[аы])[  ]?(?:№[  ]?)?-?\d{4,}(?:[  ]\d{4})*(?!\d)(?![   ]?(?:$MARK_ALT)(?![a-zа-яё]))""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Payment order without a currency mark ("Списано 440.00 счет *4863 п/п …
     * Остаток 3079.36"): the amount stands right after the verb and our own
     * account mask right after it. The recipient's "счет 0741" has no star (B-107).
     */
    private val ORDER_AMOUNT = Regex(
        """(?<![а-яё])(?:списано|зачислено)[\s  ]+$NUM[\s  ]+сч[её]т[\s  ]+[*•]\d{4}(?!\d)""",
        RegexOption.IGNORE_CASE
    )

    /** A phone number ("+7 926 263-45-67", "89262634567") — neither a sign nor an amount (B-83). */
    private val PHONE = Regex(
        """(?<![\d+])(?:\+7|8)[  \-(]*\d{3}[  \-)]*\d{3}[  \-]*\d{2}[  \-]*\d{2}(?!\d)"""
    )

    private val NAME_INITIAL = Regex("""\b([А-ЯЁ][а-яё]+\s+[А-ЯЁ]\.)""")
    private val NAME_FULL = Regex("""\b([А-ЯЁ][а-яё]+)\s+([А-ЯЁ][а-яё]+)\b""")

    private val TRANSFER = listOf("перевод", "между счет", "с карты на карту", "себе", "пополнение с карты")
    private val INCOME = listOf("пополнение", "зачисление", "поступление", "возврат", "начислен", "принят", "кэшбэк", "кешбэк")
    private val EXPENSE = listOf("оплата", "покупка", "списание", "снятие", "платеж", "платёж", "комисси")
    private val BALANCE_MARKERS = listOf("доступно", "остаток", "баланс")

    /** Money left the account. Wins over the kind: "Списание … Перевод …" is an expense (B-45). */
    private val OUT_MARKS = listOf(
        "списание", "списан", "снятие", "оплата", "покупка", "платеж", "платёж", "комисси",
        "получатель", "отправлен", "исходящ", "вы перевели", "перевод на карту", "перевод со счет",
        "перевод с карты", "перевод на счет получателя"
    )

    /** Money came into the account. */
    private val IN_MARKS = listOf(
        "зачисление", "зачислен", "поступление", "поступил", "пополнение", "отправитель",
        "вам перевели", "перевод от", "входящ", "перевод получен", "получен перевод", "вам поступил", "возврат", "начислен", "кэшбэк", "кешбэк"
    )

    /** "… от Ивана Иванова" / "от Иван И." — the sender; matched on the original case (B-84). */
    private val IN_FROM = Regex("""(?<![А-Яа-яЁё])от[  ]+[А-ЯЁ][а-яё]""")

    /** "Перевод на 1 100 ₽" — as "Пополнение на …", the money is credited. */
    private val TRANSFER_IN = Regex("""перевод[  ]на[  ]?(?=\d)""", RegexOption.IGNORE_CASE)

    /** An explicit sign at the amount: "+1 100 ₽", "−500 ₽". */
    private val SIGNED_AMOUNT = Regex("""(?<![\d.,])([+\-−–])[  ]?(?=\d)""")

    /**
     * An advertisement is not an operation, even with a number in it (B-46).
     * Checked before anything else: promo texts borrow operation words
     * ("кешбэк", "получите … ₽").
     */
    private val PROMO_MARKS = listOf(
        "розыгрыш", "выиграй", "получите до", "получить до", "акция", "акции", "предложение",
        "оцените", "оформите", "подключите", "ставьте", "успейте", "только до", "промокод",
        "приглашай", "скидка", "скидку"
    )

    private val PROMO_PATTERNS = listOf(
        Regex("""до[  ]?\d+([.,]\d+)?[  ]?%""", RegexOption.IGNORE_CASE),
        Regex("""(кешб|кэшб)[а-яё]*[^.!?]{0,40}в категори""", RegexOption.IGNORE_CASE),
        Regex("""получите[^.!?]{0,20}\d""", RegexOption.IGNORE_CASE)
    )

    private val CP_STOP = (TRANSFER + INCOME + EXPENSE + listOf(
        "банк", "сбербанк", "альфа", "тинькофф", "счет", "счёт", "карты", "карта",
        "доступно", "остаток", "баланс", "руб"
    )).toSet()

    private data class Hit(val start: Int, val major: String, val currency: String)

    /**
     * Parses one push. The currency comes only from the mark next to the
     * amount; a bare number is never an amount. [defaultCurrency] is kept for
     * compatibility (null — a bank where "р"/"руб" must not be read as RUB)
     * and does not change the result.
     */
    fun parse(title: String?, text: String, defaultCurrency: String? = Currencies.RUB): ParsedOp? {
        val blob = "${title ?: ""} $text".trim()
        if (blob.isEmpty()) return null
        val clean = MASK.replace(PHONE.replace(blob) { " ".repeat(it.value.length) }) { " ".repeat(it.value.length) }

        val low = clean.lowercase()
        if (isPromo(low)) return null

        val detected = detectKind(blob)
        val counterparty = counterparty(text)
        // No direction — no operation: a push without an operation sign is an
        // advertisement, a balance notice or something else (B-45, B-46).
        val hits = hits(clean).ifEmpty { orderHit(blob) }
        val direction = detectDirection(low, clean, detected, counterparty, hits) ?: return null
        val kind = when {
            detected != TransactionKind.UNKNOWN -> detected
            direction == OpDirection.OUT -> TransactionKind.EXPENSE
            else -> TransactionKind.INCOME
        }

        val balanceAt = BALANCE_MARKERS.mapNotNull { m -> low.indexOf(m).takeIf { it >= 0 } }.minOrNull()

        // The operation amount is the first currency-marked number before the
        // balance clause; a number without a mark is not an amount.
        val op = hits.firstOrNull { balanceAt == null || it.start < balanceAt }
            ?: return null
        val magnitude = toMinor(op.major) ?: return null
        if (magnitude == 0L) return null

        val balance = balanceAt?.let { at ->
            hits.firstOrNull { it.start > at }?.let { toMinor(it.major) }
                ?: Regex(NUM).find(clean, at)?.let { toMinor(it.groupValues[1]) }
        }

        val signed = if (direction == OpDirection.OUT) -magnitude else magnitude
        return ParsedOp(
            amount = Money.ofMinor(signed),
            currency = op.currency,
            kind = kind,
            direction = direction,
            balance = balance?.let(Money::ofMinor),
            counterparty = counterparty
        )
    }

    private fun orderHit(text: String): List<Hit> =
        ORDER_AMOUNT.find(text)?.let {
            listOf(Hit(it.groups[1]!!.range.first, it.groupValues[1], Currencies.RUB))
        } ?: emptyList()

    private fun hits(text: String): List<Hit> {
        val after = AMOUNT_THEN_CCY.findAll(text).map {
            Hit(it.range.first, it.groupValues[1], codeOf(it.groupValues[2]))
        }
        val before = CCY_THEN_AMOUNT.findAll(text).map {
            Hit(it.range.first, it.groupValues[2], codeOf(it.groupValues[1]))
        }
        return (after + before).sortedBy { it.start }.toList()
    }

    private fun isPromo(low: String): Boolean =
        PROMO_MARKS.any { it in low } || PROMO_PATTERNS.any { it.containsMatchIn(low) }

    /**
     * Direction of the money. An explicit word ("списание", "зачисление") wins
     * over the kind and over the word "перевод"; when two words meet, the first
     * one in the text decides. Without a word the sign at the amount is used,
     * then the credit wording "Перевод на <сумма>", then a transfer addressed to
     * someone (an outgoing one). Nothing of this — null: not an operation.
     */
    private fun detectDirection(
        low: String,
        clean: String,
        kind: TransactionKind,
        counterparty: String?,
        hits: List<Hit>
    ): OpDirection? {
        val out = OUT_MARKS.mapNotNull { m -> low.indexOf(m).takeIf { it >= 0 } }.minOrNull()
        val income = (IN_MARKS.mapNotNull { m -> low.indexOf(m).takeIf { it >= 0 } } +
            listOfNotNull(IN_FROM.find(clean)?.range?.first)).minOrNull()
        if (out != null && (income == null || out <= income)) return OpDirection.OUT
        if (income != null) return OpDirection.IN

        // A sign counts only right before an amount with a currency mark (B-83).
        SIGNED_AMOUNT.findAll(clean).firstOrNull { m -> hits.any { it.start == m.range.last + 1 } }?.let {
            return if (it.groupValues[1] == "+") OpDirection.IN else OpDirection.OUT
        }
        if (TRANSFER_IN.containsMatchIn(low)) return OpDirection.IN
        if (kind == TransactionKind.TRANSFER && counterparty != null) return OpDirection.OUT
        return null
    }

    private fun codeOf(mark: String): String {
        val m = mark.lowercase()
        return CURRENCY_MARKS.first { it.first.lowercase() == m }.second
    }

    /**
     * "107 409,81" / "1,234.56" / "1.234,56" / "500" → minor units. The last
     * separator followed by one or two digits is the decimal point; any other
     * separator groups thousands.
     */
    internal fun toMinor(raw: String): Long? {
        val s = raw.filterNot { it == ' ' || it == '\u00A0' || it == '\u202F' }
        val lastSep = s.indexOfLast { it == ',' || it == '.' }
        val (whole, frac) = if (lastSep >= 0 && s.length - lastSep - 1 in 1..2) {
            s.substring(0, lastSep) to s.substring(lastSep + 1)
        } else s to ""
        val digits = whole.filter { it.isDigit() }
        if (digits.isEmpty() || digits.length > 15) return null
        return digits.toLong() * 100 + frac.padEnd(2, '0').toLong()
    }

    private fun counterparty(text: String): String? {
        NAME_INITIAL.find(text)?.let { return it.groupValues[1].trim() }
        for (m in NAME_FULL.findAll(text)) {
            val a = m.groupValues[1]
            val b = m.groupValues[2]
            if (a.lowercase() in CP_STOP || b.lowercase() in CP_STOP) continue
            return "$a $b"
        }
        return null
    }

    private fun detectKind(text: String): TransactionKind {
        val low = text.lowercase()
        return when {
            TRANSFER.any { it in low } -> TransactionKind.TRANSFER
            INCOME.any { it in low } -> TransactionKind.INCOME
            EXPENSE.any { it in low } -> TransactionKind.EXPENSE
            else -> TransactionKind.UNKNOWN
        }
    }
}
