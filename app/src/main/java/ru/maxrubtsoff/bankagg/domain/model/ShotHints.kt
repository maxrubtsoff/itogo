package ru.maxrubtsoff.bankagg.domain.model

/**
 * The quiet line of a screenshot's head when something was not read (U-53): it
 * says what to look at, in a few words, without blame. Null — all was read.
 */
object ShotHints {

    /** Cashback: rows whose percent is missing, or whose category was not named. */
    fun cashback(lines: List<ShotLine>): String? {
        val noPercent = lines.count { it.percent == null && it.text.isNotBlank() }
        val noName = lines.count { it.text.isBlank() }
        return when {
            noPercent == 1 && noName == 0 -> "Процент одной категории не прочитался"
            noPercent > 1 && noName == 0 -> "Проценты $noPercent категорий не прочитались"
            noName == 1 && noPercent == 0 -> "Одна категория не прочиталась"
            noName + noPercent > 0 -> "Часть категорий не прочиталась"
            else -> null
        }
    }

    /** Deposit: the fields still empty, in the order of the form. */
    fun deposit(d: DepositDraft): String? {
        val missing = listOfNotNull(
            "сумма".takeIf { d.principal == null },
            "ставка".takeIf { d.rateBp == null && d.exitAmount == null },
            "дата открытия".takeIf { d.startDate == null },
            "дата закрытия".takeIf { d.endDate == null }
        )
        return if (missing.isEmpty()) null else "Не прочиталось: " + missing.joinToString(", ")
    }
}
