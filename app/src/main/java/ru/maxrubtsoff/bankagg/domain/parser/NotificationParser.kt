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
     */
    const val VERSION = 2


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
     * "•• 1234", "карта *1234") or a word glued to four digits ("СЧЁТ1234",
     * "MIR-1234"). "Оплата картой 5000 ₽" is not a mask.
     */
    private val MASK = Regex(
        """(?:[*•]+[ \u00A0]?\d{2,}|(?:сч[её]т|карт[аы]|mir|visa|мир)-?\d{4}(?!\d))""",
        RegexOption.IGNORE_CASE
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
        "получатель", "отправлен", "вы перевели", "перевод на карту", "перевод со счет",
        "перевод с карты", "перевод на счет получателя"
    )

    /** Money came into the account. */
    private val IN_MARKS = listOf(
        "зачисление", "зачислен", "поступление", "поступил", "пополнение", "отправитель",
        "вам перевели", "перевод от", "возврат", "начислен", "кэшбэк", "кешбэк"
    )

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

    /** Units that make a number something other than money ("32,01 МБ", "15 мин"). */
    private val UNIT_AFTER = Regex(
        """^[  ]?(мб|гб|кб|тб|байт|mb|gb|kb|tb|%|шт|мин|сек|час|дн|дней|км|кг|мл|балл|бонус)""",
        RegexOption.IGNORE_CASE
    )

    private val CP_STOP = (TRANSFER + INCOME + EXPENSE + listOf(
        "банк", "сбербанк", "альфа", "тинькофф", "счет", "счёт", "карты", "карта",
        "доступно", "остаток", "баланс", "руб"
    )).toSet()

    private data class Hit(val start: Int, val major: String, val currency: String)

    /**
     * Parses one push. [defaultCurrency] is used when an amount carries no
     * currency mark; pass null for banks where guessing would be wrong (BSB:
     * Belarusian rubles must not be read as Russian) — then such a push is
     * not recognised at all.
     */
    fun parse(title: String?, text: String, defaultCurrency: String? = Currencies.RUB): ParsedOp? {
        val blob = "${title ?: ""} $text".trim()
        if (blob.isEmpty()) return null
        val clean = MASK.replace(blob) { " ".repeat(it.value.length) }

        val low = clean.lowercase()
        if (isPromo(low)) return null

        val kind = detectKind(blob)
        val counterparty = counterparty(text)
        // No direction — no operation: a push without an operation sign is an
        // advertisement, a balance notice or something else (B-45, B-46).
        val direction = detectDirection(low, clean, kind, counterparty) ?: return null

        val balanceAt = BALANCE_MARKERS.mapNotNull { m -> low.indexOf(m).takeIf { it >= 0 } }.minOrNull()
        val hits = hits(clean)

        // The operation amount is the first currency-marked number before the
        // balance clause; without marks fall back to the first plain number.
        val op = hits.firstOrNull { balanceAt == null || it.start < balanceAt }
            ?: plainAmount(clean, balanceAt, defaultCurrency)
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

    private fun hits(text: String): List<Hit> {
        val after = AMOUNT_THEN_CCY.findAll(text).map {
            Hit(it.range.first, it.groupValues[1], codeOf(it.groupValues[2]))
        }
        val before = CCY_THEN_AMOUNT.findAll(text).map {
            Hit(it.range.first, it.groupValues[2], codeOf(it.groupValues[1]))
        }
        return (after + before).sortedBy { it.start }.toList()
    }

    private fun plainAmount(text: String, balanceAt: Int?, defaultCurrency: String?): Hit? {
        val ccy = defaultCurrency ?: return null
        val m = Regex(NUM).findAll(text).firstOrNull { m ->
            (balanceAt == null || m.range.first < balanceAt) && !hasUnit(text, m.range.last + 1)
        } ?: return null
        return Hit(m.range.first, m.groupValues[1], ccy)
    }

    /** True when the number is followed by a unit — then it is not money (B-46). */
    private fun hasUnit(text: String, from: Int): Boolean =
        from <= text.length && UNIT_AFTER.containsMatchIn(text.substring(from))

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
        counterparty: String?
    ): OpDirection? {
        val out = OUT_MARKS.mapNotNull { m -> low.indexOf(m).takeIf { it >= 0 } }.minOrNull()
        val income = IN_MARKS.mapNotNull { m -> low.indexOf(m).takeIf { it >= 0 } }.minOrNull()
        if (out != null && (income == null || out <= income)) return OpDirection.OUT
        if (income != null) return OpDirection.IN

        SIGNED_AMOUNT.find(clean)?.let {
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
