package ru.maxrubtsoff.bankagg.domain.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** What the app may tell about by itself (F-24); all on by default. */
data class ReminderSettings(
    val newBank: Boolean = true,
    val depositEnding: Boolean = true,
    /** Days before a deposit closes: 1, 3 or 7. */
    val daysBefore: Int = 3,
    val cashbackMonth: Boolean = true
) {
    val any: Boolean get() = newBank || depositEnding || cashbackMonth

    companion object {
        val DAY_CHOICES = listOf(1, 3, 7)
    }
}

/** One message to show; [id] makes sure the same event is told about once. */
data class Reminder(
    val id: String,
    val kind: Kind,
    val title: String,
    val text: String,
    val depositId: String? = null,
    val bank: BankBrand? = null,
    /** The subscription's own look for the big icon (F-33). */
    val decor: BrandDecor? = null
) {
    enum class Kind { DEPOSIT_ENDING, CASHBACK_MONTH, SUBSCRIPTION }
}

/**
 * The kinds of reminder the app can send, and which of them make sense for the
 * sections the user keeps (U-52): without subscriptions there is nothing to
 * remind about, without cashback no new cashback month.
 */
enum class ReminderKind(val label: String, val short: String) {
    NEW_BANK("Новый банк", "новые банки"),
    DEPOSIT_ENDING("Вклад заканчивается", "вклады"),
    CASHBACK_MONTH("Новый месяц кэшбэка", "кэшбэк"),
    SUBSCRIPTION("Списание подписки", "подписки")
}

object ReminderKinds {

    fun of(features: Features): List<ReminderKind> = buildList {
        if (features.collectsNotifications) add(ReminderKind.NEW_BANK)
        if (features.has(Feature.DEPOSITS)) add(ReminderKind.DEPOSIT_ENDING)
        if (features.showCashback) add(ReminderKind.CASHBACK_MONTH)
        if (features.showSubscriptions) add(ReminderKind.SUBSCRIPTION)
    }

    /** «Новые банки, вклады, кэшбэк» — the line under «Уведомления» in settings. */
    fun subtitle(features: Features): String {
        val kinds = of(features)
        if (kinds.isEmpty()) return "Выключены"
        return kinds.joinToString(", ") { it.short }.replaceFirstChar { it.uppercase() }
    }
}

object Reminders {

    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))

    /**
     * What is due today and not told yet. Deposits: [ReminderSettings.daysBefore]
     * days before the end and on the day itself. Cashback: in the first days of
     * a month, only while that month has no cashback yet. With [hidden] amounts
     * the text carries no sums.
     */
    fun due(
        today: LocalDate,
        deposits: List<Deposit>,
        cashbackFilled: Boolean,
        settings: ReminderSettings,
        hidden: Boolean,
        sent: Set<String>,
        subscriptions: List<Subscription> = emptyList(),
        /** Nothing is reminded about a section the user doesn't keep (U-52). */
        features: Features = Features.ALL
    ): List<Reminder> {
        val out = mutableListOf<Reminder>()
        val kinds = ReminderKinds.of(features)
        // F-27: the day before a payment, for subscriptions with the switch on.
        if (ReminderKind.SUBSCRIPTION in kinds) subscriptions
            .filter { it.remind && it.status == SubscriptionStatus.ACTIVE }.forEach { s ->
            val date = Subscriptions.upcoming(s, today)
            if (ChronoUnit.DAYS.between(today, date) != 1L) return@forEach
            val id = "sub:${s.id}:$date"
            if (id in sent) return@forEach
            val price = if (hidden) "•••• ₽" else Money.ofMinor(s.priceMinor).format()
            out += Reminder(id, Reminder.Kind.SUBSCRIPTION, "Завтра спишется ${s.title}", listOfNotNull(price, s.brand?.title).joinToString(" · "), bank = s.brand, decor = s.look)
        }
        if (settings.depositEnding && ReminderKind.DEPOSIT_ENDING in kinds) deposits.forEach { d ->
            val left = ChronoUnit.DAYS.between(today, d.endDate)
            val (id, title) = when (left) {
                settings.daysBefore.toLong() -> "dep:${d.id}:before" to "Вклад закрывается через ${RuPlural.days(left.toInt())}"
                0L -> "dep:${d.id}:day" to "Вклад закрывается сегодня"
                else -> return@forEach
            }
            if (id in sent) return@forEach
            val payout = if (hidden) "•••• ₽" else DepositMath.project(d, today).payoutAmount.format()
            out += Reminder(id, Reminder.Kind.DEPOSIT_ENDING, title, "${d.brand.title}, ${d.endDate.format(DAY_MONTH)} · к выдаче $payout", depositId = d.id, bank = d.brand)
        }
        if (settings.cashbackMonth && ReminderKind.CASHBACK_MONTH in kinds &&
            !cashbackFilled && today.dayOfMonth <= CASHBACK_WINDOW_DAYS
        ) {
            val id = "cb:${today.year}-${today.monthValue}"
            if (id !in sent) {
                val month = today.month.getDisplayName(java.time.format.TextStyle.FULL_STANDALONE, Locale("ru")).replaceFirstChar { it.uppercase() }
                out += Reminder(id, Reminder.Kind.CASHBACK_MONTH, "$month начался", "Кэшбэк на месяц ещё не добавлен")
            }
        }
        return out
    }

    /** The first days of a month: a reminder still makes sense if the phone was off on the 1st. */
    const val CASHBACK_WINDOW_DAYS = 3
}
