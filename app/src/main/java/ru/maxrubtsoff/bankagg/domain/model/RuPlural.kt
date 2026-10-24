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

    /** "1 подписка", "3 подписки", "5 подписок". */
    fun subscriptions(n: Int): String = "$n " + form(n, "подписка", "подписки", "подписок")

    /** "1 валюта", "3 валюты", "5 валют". */
    fun currencies(n: Int): String = "$n " + form(n, "валюта", "валюты", "валют")

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
