package ru.maxrubtsoff.bankagg.domain.model

/** Russian plural forms for UI counts. */
object RuPlural {

    /** "1 категория", "3 категории", "5 категорий". */
    fun categories(n: Int): String = "$n " + form(n, "категория", "категории", "категорий")

    /** "1 день", "3 дня", "5 дней". */
    fun days(n: Int): String = "$n " + form(n, "день", "дня", "дней")

    /** "1 месяц", "3 месяца", "5 месяцев". */
    fun months(n: Int): String = "$n " + form(n, "месяц", "месяца", "месяцев")

    /** "1 вклад", "3 вклада", "5 вкладов". */
    fun deposits(n: Int): String = "$n " + form(n, "вклад", "вклада", "вкладов")

    /** "1 требует проверки", "3 требуют проверки". */
    fun needsCheck(n: Int): String = "$n " + form(n, "требует", "требуют", "требуют") + " проверки"

    fun screens(n: Int): String = "$n " + form(n, "скрин", "скрина", "скринов")

    /** "1 скрин ждёт проверки", "3 скрина ждут проверки". */
    fun pendingShots(n: Int): String {
        val one = n % 10 == 1 && n % 100 != 11
        return screens(n) + (if (one) " ждёт" else " ждут") + " проверки"
    }

    /** "1 готов", "2 готовы". */
    fun readyShots(n: Int): String = "$n " + if (n % 10 == 1 && n % 100 != 11) "готов" else "готовы"

    /** "1 не прочитан", "2 не прочитаны". */
    fun unreadShots(n: Int): String = "$n " + if (n % 10 == 1 && n % 100 != 11) "не прочитан" else "не прочитаны"

    /** "1 дубликат", "3 дубликата", "5 дубликатов". */
    fun duplicatesSkipped(n: Int): String = "$n " + form(n, "дубликат", "дубликата", "дубликатов")

    /** "1 подписка", "3 подписки", "5 подписок". */
    fun subscriptions(n: Int): String = "$n " + form(n, "подписка", "подписки", "подписок")

    /** "1 валюта", "3 валюты", "5 валют". */
    fun currencies(n: Int): String = "$n " + form(n, "валюта", "валюты", "валют")

    /** "1 событие", "3 события", "5 событий". */
    fun events(n: Int): String = "$n " + form(n, "событие", "события", "событий")

    fun operations(n: Int): String = "$n " + form(n, "операция", "операции", "операций")

    fun banks(n: Int): String = "$n " + form(n, "банк", "банка", "банков")

    /** «30 секунд», «1 минута», «1 час» — how long the lock keypad rests (B-27). */
    fun pause(ms: Long): String {
        val s = (ms / 1000).toInt()
        return when {
            s < 60 -> "$s " + form(s, "секунда", "секунды", "секунд")
            s < 3600 -> (s / 60).let { "$it " + form(it, "минута", "минуты", "минут") }
            else -> (s / 3600).let { "$it " + form(it, "час", "часа", "часов") }
        }
    }

    /** U-68, after a deposit scan: "Добавили 2 вклада · 1 уже был", "3 вклада уже были"; null when nothing happened. */
    fun depositScan(added: Int, skipped: Int): String? {
        val was = if (skipped % 10 == 1 && skipped % 100 != 11) "уже был" else "уже были"
        return when {
            added > 0 && skipped > 0 -> "Добавили ${deposits(added)} · $skipped $was"
            added > 0 -> "Добавили ${deposits(added)}"
            skipped > 0 -> "${deposits(skipped)} $was"
            else -> null
        }
    }

    private fun form(n: Int, one: String, few: String, many: String): String {
        val mod100 = n % 100
        val mod10 = n % 10
        return when {
            mod100 in 11..14 -> many
            mod10 == 1 -> one
            mod10 in 2..4 -> few
            else -> many
        }
    }
}
