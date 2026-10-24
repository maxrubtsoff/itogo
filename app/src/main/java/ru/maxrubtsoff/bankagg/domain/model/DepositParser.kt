package ru.maxrubtsoff.bankagg.domain.model

import java.time.LocalDate
import java.time.MonthDay

/**
 * Turns raw OCR text from a deposit screenshot into a [DepositDraft].
 *
 * Pure domain logic (no Android, no Tesseract) so it is fully unit-testable: the
 * OCR engine only produces text; finding amounts, the rate, the dates and the bank
 * happens here. Deliberately conservative — a value it can't read confidently is
 * left null for the user to fill in rather than guessed. Values it works out
 * (a date from "на 13 месяцев до 30 ноября") are marked in [DepositDraft.guessed].
 */
object DepositParser {

    data class Result(
        val brand: BankBrand?,
        val draft: DepositDraft
    )

    // A money amount: grouped thousands ("1 234 567") or a 4+ digit run, optional
    // ",dd" kopecks, optional ₽/руб. Small bare numbers like "19" (a rate) never match.
    // OCR often drops the first group's space ("1185 142,31"), so it may be 4 digits.
    private val AMOUNT = Regex("""(?<![\d,.])(\d{1,4}(?:[   ]\d{3})+|\d{4,})(?:[.,](\d{1,2}))?""")
    // OCR reads the rouble sign as "Р" or "P".
    private val CURRENCY_AFTER = Regex("""^\s{0,2}(₽|Р|P|руб)""")
    private val PERCENT = Regex("""(?<!\d)(\d{1,2}(?:[.,]\d{1,2})?)[\s  ]*%""")
    private val DATE = Regex("""(?<!\d)(\d{1,2})[.\-/](\d{1,2})[.\-/](\d{2,4})(?!\d)""")

    // Textual Russian date: "2 октября 2026". The month is resolved via MONTHS.
    private val TEXT_DATE = Regex("""(?<![\d\p{L}])(\d{1,2})\s+([А-Яа-яЁё]+)\s+(\d{4})(?!\d)""")
    // "до 30 ноября" with no year: an end date in the next twelve months.
    private val UNTIL_NO_YEAR = Regex("""(?i)(?<![\p{L}])до\s+(\d{1,2})\s+([А-Яа-яЁё]+)(?!\s*\d)""")
    // A line that is just "30 ноября".
    private val DAY_MONTH = Regex("""^(\d{1,2})\s+([А-Яа-яЁё]+)$""")
    // "на 13 месяцев", "Срок вклада: 4 месяца".
    private val TERM = Regex("""(?i)(\d{1,2})\s+месяц""")
    // A day-month schedule row: "31 октября 13 716,60 ₽".
    private val SCHEDULE_ROW = Regex("""^(\d{1,2})\s+([А-Яа-яЁё]+)\s+(.+)$""")
    // "1 154 076,38 ₽ +154 076,38 ₽": the balance and what it has grown by.
    private val BALANCE_GAIN = Regex("""\+\s*(\d{1,3}(?:[   ]\d{3})+|\d{4,})(?:[.,](\d{1,2}))?""")

    private val MONTHS = mapOf(
        "января" to 1, "январь" to 1,
        "февраля" to 2, "февраль" to 2,
        "марта" to 3, "март" to 3,
        "апреля" to 4, "апрель" to 4,
        "мая" to 5, "май" to 5,
        "июня" to 6, "июнь" to 6,
        "июля" to 7, "июль" to 7,
        "августа" to 8, "август" to 8,
        "сентября" to 9, "сентябрь" to 9,
        "октября" to 10, "октябрь" to 10,
        "ноября" to 11, "ноябрь" to 11,
        "декабря" to 12, "декабрь" to 12
    )

    // Phrases are matched against the amount's own line first, then the caption
    // line above it (banks often put "Сумма в конце срока" on its own line). Kept
    // specific so an accrual caption like "в конце месяца" is not mistaken for the
    // final payout ("в конце срока"). Income is checked before payout: "Доход в
    // конце срока" is income, not the payout.
    private val INCOME_WORDS = listOf(
        "доход за весь срок", "доход за срок", "доход в конце срок", "заработает", "общий доход"
    )
    private val EXIT_WORDS = listOf(
        "конце срок", "конца срок", "на выход", "к выдач", "к получени",
        "итогов сумм", "сумма к получени", "выплат", "вернет", "погашени", "сумма на вклад"
    )
    private val IN_WORDS = listOf(
        "при открыт", "на входе", "первоначальн", "вложен", "внес", "пополн",
        "сумма вклад", "сумма на вход", "сумма заявк", "открыт", "депозит", "вклад", "сумма", "баланс"
    )
    // On the line above, only a real caption names the amount: a bare "Вклад"
    // heading sits above the current balance, not the sum put in.
    private val IN_CAPTIONS = listOf(
        "при открыт", "на входе", "первоначальн", "вложен", "внес", "пополн",
        "сумма вклад", "сумма на вход", "сумма заявк", "баланс"
    )
    /** Amounts that are never the deposit, its payout or its income. */
    private val IGNORE_WORDS = listOf(
        "начисл", "зачисл", "в конце месяца", "прогноз дохода", "номер", "договор",
        "цена одного", "количеств", "id "
    )
    private val END_WORDS = listOf("до ", "погашен", "окончан", "закрыт", "конце срок", "возврат")
    // A day and month with no year counts as the end only next to these.
    private val END_HINTS = listOf("до ", "закрыт", "погашен", "окончан")

    fun parse(rawText: String, brandHint: BankBrand? = null, today: LocalDate = LocalDate.now()): Result {
        val lines = rawText.lines()
        val brand = brandHint ?: CashbackParser.guessBank(
            lines.filter { !PERCENT.containsMatchIn(it) }.joinToString("\n")
        )

        val rate = lines.firstNotNullOfOrNull { rateOf(it, preferKeyword = true) }
            ?: lines.firstNotNullOfOrNull { rateOf(it, preferKeyword = false) }

        val amounts = amountHits(lines)
        var principal = pick(amounts, Kind.IN)
        var exit = pick(amounts, Kind.EXIT)
        val income = pick(amounts, Kind.INCOME)
        val (start, end, dateGuesses) = dates(lines, today)
        val guessed = dateGuesses.toMutableSet()
        // No captions at all: with two or more amounts the smaller is the deposit,
        // the larger the payout; a single amount is the deposit. Only a guess —
        // on a deposit's main screen the lone big number is often today's balance.
        if (amounts.none { it.kind != null }) {
            val sorted = amounts.map { it.minor }.distinct().sorted()
            principal = sorted.firstOrNull()?.also { guessed += DepositField.PRINCIPAL }
            exit = sorted.takeIf { it.size > 1 }?.last()?.also { guessed += DepositField.EXIT }
        }
        // A payout no bigger than the deposit is a misread.
        if (exit != null && principal != null && exit <= principal) exit = null

        val draft = DepositDraft(
            principal = principal?.let(Money::ofMinor),
            exitAmount = exit?.let(Money::ofMinor),
            rateBp = rate,
            startDate = start,
            endDate = end,
            income = income?.let(Money::ofMinor),
            title = titleOf(lines),
            method = methodOf(lines),
            guessed = guessed
        ).withDerivedAmounts()
        return Result(brand, draft)
    }

    // ---- amounts ----

    private enum class Kind { INCOME, EXIT, IN }

    /** [strong]: the caption is on the amount's own line (beats a caption on the line above). */
    private data class AmountHit(val minor: Long, val kind: Kind?, val strong: Boolean)

    private fun amountHits(lines: List<String>): List<AmountHit> = buildList {
        var prev = ""
        var prev2 = ""
        for (line in lines) {
            val own = CategoryMatcher.normalize(line)
            val lower = line.lowercase()
            val m = AMOUNT.find(line)
            val minor = m?.let(::amountMinor)
            val p1 = CategoryMatcher.normalize(prev)
            val captioned = listOf(own, p1).any { s -> captionOf(s) != null } || INCOME_WORDS.any { CategoryMatcher.normalize(prev2).contains(it) }
            // A long bare number ("::4493", an account) is money only with a rouble sign or kopecks.
            val looksLikeMoney = m != null && (m.groupValues[1].any { !it.isDigit() } || m.groupValues[2].isNotEmpty() ||
                CURRENCY_AFTER.containsMatchIn(line.substring(m.range.last + 1)) || captioned)
            if (minor != null && looksLikeMoney && IGNORE_WORDS.none { lower.contains(it) || own.contains(it) }) {
                val gain = BALANCE_GAIN.find(line, m.range.last + 1)?.let { g -> amountOf(g.groupValues[1], g.groupValues[2]) }
                when {
                    // Balance with its growth: what was put in is the difference.
                    gain != null && gain < minor -> add(AmountHit(minor - gain, Kind.IN, strong = true))
                    else -> {
                        val ownKind = kindOf(own)
                        val p2 = CategoryMatcher.normalize(prev2)
                        val captionAbove = captionOf(p1)
                        when {
                            ownKind != null -> add(AmountHit(minor, ownKind, strong = true))
                            IGNORE_WORDS.any { p1.contains(CategoryMatcher.normalize(it)) } -> Unit
                            captionAbove != null -> add(AmountHit(minor, captionAbove, strong = false))
                            // "Доход в конце срока / До вычета налога / +134 880 ₽".
                            INCOME_WORDS.any { p2.contains(it) } -> add(AmountHit(minor, Kind.INCOME, strong = false))
                            // "Нет пополнений зачислим / в октябре / 15 682,79 ₽".
                            IGNORE_WORDS.any { p2.contains(CategoryMatcher.normalize(it)) } -> Unit
                            else -> add(AmountHit(minor, null, strong = false))
                        }
                    }
                }
            }
            if (line.isNotBlank()) { prev2 = prev; prev = line }
        }
    }

    /** Like [kindOf], but a bare "вклад"/"сумма" doesn't count: only a caption that names the amount. */
    private fun captionOf(norm: String): Kind? = kindOf(norm)?.takeIf { it != Kind.IN || IN_CAPTIONS.any { w -> norm.contains(w) } }

    private fun kindOf(norm: String): Kind? = when {
        INCOME_WORDS.any { norm.contains(it) } -> Kind.INCOME
        EXIT_WORDS.any { norm.contains(it) } -> Kind.EXIT
        IN_WORDS.any { norm.contains(it) } -> Kind.IN
        else -> null
    }

    private fun pick(hits: List<AmountHit>, kind: Kind): Long? =
        (hits.firstOrNull { it.kind == kind && it.strong } ?: hits.firstOrNull { it.kind == kind })?.minor

    private fun amountMinor(m: MatchResult): Long? {
        val raw = m.groupValues[1]
        val digits = raw.filter { it.isDigit() }
        val grouped = digits.length != raw.length          // had a thousands separator
        val frac = m.groupValues.getOrNull(2).orEmpty()
        // A bare 4-digit number with no grouping and no kopecks that looks like a
        // year ("2026 год", a date's year) is not a money amount.
        val rub = digits.toLongOrNull() ?: return null
        if (!grouped && frac.isEmpty() && digits.length == 4 && rub in 1990..2099) return null
        return amountOf(raw, frac)
    }

    private fun amountOf(raw: String, frac: String): Long? {
        val rub = raw.filter { it.isDigit() }.toLongOrNull() ?: return null
        if (rub < 1000L) return null                       // below a plausible deposit
        val kop = when (frac.length) {
            0 -> 0L
            1 -> frac.toLong() * 10
            else -> frac.take(2).toLong()
        }
        return rub * 100 + kop
    }

    private fun rateOf(line: String, preferKeyword: Boolean): Int? {
        if (preferKeyword) {
            val n = CategoryMatcher.normalize(line)
            if (!n.contains("ставк") && !n.contains("годов") && !n.contains("процент")) return null
        }
        val m = PERCENT.find(line) ?: return null
        val v = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        if (v <= 0.0 || v > 100.0) return null
        return Math.round(v * 100).toInt()
    }

    // ---- dates ----

    private data class DateHit(val date: LocalDate, val context: String)

    private fun dates(lines: List<String>, today: LocalDate): Triple<LocalDate?, LocalDate?, Set<DepositField>> {
        val hits = buildList {
            var prev = ""
            for (line in lines) {
                val lower = line.lowercase()
                // "Решение о выпуске … от 26.11.2025", "Дата подписания" date a document, not the deposit.
                if (!lower.contains("решени") && !lower.contains("подписан")) {
                    val ctx = "$prev $lower"
                    DATE.findAll(line).mapNotNull(::numericDateOf).forEach { add(DateHit(it, ctx)) }
                    TEXT_DATE.findAll(line).mapNotNull(::textDateOf).forEach { add(DateHit(it, ctx)) }
                }
                if (line.isNotBlank()) prev = lower
            }
        }
        val distinct = hits.map { it.date }.distinct().sorted()
        var start: LocalDate? = null
        var end: LocalDate? = null
        val guessed = mutableSetOf<DepositField>()
        when {
            distinct.size >= 2 -> { start = distinct.first(); end = distinct.last() }
            distinct.size == 1 -> {
                val h = hits.first()
                if (END_WORDS.any { h.context.contains(it) }) end = h.date else start = h.date
            }
        }
        if (end == null) {
            // "до 30 ноября", "Прогноз на дату закрытия / 30 ноября": the next 30 November.
            end = lines.withIndex().firstNotNullOfOrNull { (i, l) ->
                UNTIL_NO_YEAR.find(l)?.let { m -> nextOccurrence(m.groupValues[1], m.groupValues[2], today) }
                    ?: DAY_MONTH.find(l.trim())?.takeIf { _ ->
                        val ctx = "${lines.getOrElse(i - 1) { "" }} $l".lowercase()
                        END_HINTS.drop(1).any { ctx.contains(it) }
                    }?.let { m -> nextOccurrence(m.groupValues[1], m.groupValues[2], today) }
            }?.also { guessed += DepositField.END }
        }
        val months = lines.firstNotNullOfOrNull { l -> TERM.find(l)?.groupValues?.get(1)?.toIntOrNull() }
        if (months != null && months in 1..120) {
            if (start == null && end != null) { start = end.minusMonths(months.toLong()); guessed += DepositField.START }
            else if (end == null && start != null) { end = start.plusMonths(months.toLong()); guessed += DepositField.END }
        }
        return Triple(start, end, guessed)
    }

    private fun nextOccurrence(day: String, month: String, today: LocalDate): LocalDate? = runCatching {
        val mo = MONTHS[month.lowercase().replace('ё', 'е')] ?: return null
        val md = MonthDay.of(mo, day.toInt())
        val thisYear = md.atYear(today.year)
        if (thisYear.isBefore(today)) md.atYear(today.year + 1) else thisYear
    }.getOrNull()

    private fun numericDateOf(m: MatchResult): LocalDate? = runCatching {
        val d = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toInt()
        val yRaw = m.groupValues[3].toInt()
        val y = if (yRaw < 100) 2000 + yRaw else yRaw
        if (mo !in 1..12 || d !in 1..31 || y !in 2000..2100) return null
        LocalDate.of(y, mo, d)
    }.getOrNull()

    private fun textDateOf(m: MatchResult): LocalDate? = runCatching {
        val d = m.groupValues[1].toInt()
        val mo = MONTHS[m.groupValues[2].lowercase().replace('ё', 'е')] ?: return null
        val y = m.groupValues[3].toInt()
        if (d !in 1..31 || y !in 2000..2100) return null
        LocalDate.of(y, mo, d)
    }.getOrNull()

    // ---- name and accrual ----

    private val TITLE_WORD = Regex("""^[«"]?[А-ЯЁA-Z][\p{L}\-]*[»"]?$""")

    /**
     * The deposit's own name: a short title-case line with "вклад" in it —
     * «Альфа-Вклад Максимальный», «Вклад «Надёжный»» → «Надёжный». A bare
     * "Вклад" heading or a caption ("Сумма при открытии вклада") is not a name.
     */
    private fun titleOf(lines: List<String>): String? = lines.firstNotNullOfOrNull { raw ->
        val line = raw.trim().trimEnd { !it.isLetter() && it != '»' && it != '"' }
        val words = line.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (words.size !in 2..4 || !line.contains("вклад", ignoreCase = true)) return@firstNotNullOfOrNull null
        if (!words.all { TITLE_WORD.matches(it) }) return@firstNotNullOfOrNull null
        val quoted = Regex("""«([^»]+)»""").find(line)?.groupValues?.get(1)
        if (words.first().equals("вклад", ignoreCase = true)) quoted else line
    }

    /** Capitalisation when the bank says so or its accrual schedule grows month to month. */
    private fun methodOf(lines: List<String>): AccrualMethod? {
        val all = CategoryMatcher.normalize(lines.joinToString(" "))
        if (all.contains("капитализ")) return AccrualMethod.CAPITALIZATION
        val schedule = lines.mapNotNull { l ->
            SCHEDULE_ROW.find(l.trim())?.takeIf { MONTHS.containsKey(it.groupValues[2].lowercase()) }
                ?.let { m -> AMOUNT_ANY.find(m.groupValues[3])?.let { a -> amountOfAny(a) } }
        }
        if (schedule.size >= 2 && schedule.zipWithNext().all { (a, b) -> b > a }) return AccrualMethod.CAPITALIZATION
        return null
    }

    // Accrual rows are small ("13 716,60"), so the deposit-sized floor of AMOUNT doesn't apply.
    private val AMOUNT_ANY = Regex("""(\d{1,3}(?:[   ]\d{3})*)[.,](\d{2})""")
    private fun amountOfAny(m: MatchResult): Long? =
        m.groupValues[1].filter { it.isDigit() }.toLongOrNull()?.let { it * 100 + m.groupValues[2].toLong() }
}
