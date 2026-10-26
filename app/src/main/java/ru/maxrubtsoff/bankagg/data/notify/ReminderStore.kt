package ru.maxrubtsoff.bankagg.data.notify

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.maxrubtsoff.bankagg.domain.model.ReminderSettings
import ru.maxrubtsoff.bankagg.domain.repository.ReminderRepository

/** Reminder switches and the ids already told about, in small preferences. */
class ReminderStore(context: Context) : ReminderRepository {

    private val prefs = context.applicationContext.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    override val settings: Flow<ReminderSettings> = _settings.asStateFlow()

    override suspend fun current(): ReminderSettings = _settings.value

    override suspend fun set(settings: ReminderSettings) {
        prefs.edit()
            .putBoolean(KEY_BANK, settings.newBank)
            .putBoolean(KEY_DEPOSIT, settings.depositEnding)
            .putInt(KEY_DAYS, settings.daysBefore)
            .putBoolean(KEY_CASHBACK, settings.cashbackMonth)
            .putBoolean(KEY_SUB, settings.subscriptions)
            .apply()
        _settings.value = settings
    }

    override suspend fun sent(): Set<String> = ordered().toSet()

    // Old ids are dropped past a few hundred, the oldest first: a deposit or month told about
    // long ago won't come back. The order is kept (a StringSet has none), B-96.
    override suspend fun markSent(ids: Collection<String>) {
        val all = (ordered() - ids.toSet() + ids).takeLast(MAX_SENT)
        prefs.edit().putString(KEY_SENT_ORDER, all.joinToString(SEP)).remove(KEY_SENT).apply()
    }

    /** Oldest first. Ids saved by older versions (a plain set, no order) come before the rest. */
    private fun ordered(): List<String> {
        val legacy = prefs.getStringSet(KEY_SENT, null).orEmpty()
        val stored = prefs.getString(KEY_SENT_ORDER, null).orEmpty().split(SEP).filter { it.isNotEmpty() }
        return (legacy - stored.toSet()).toList() + stored
    }

    private fun read() = ReminderSettings(
        newBank = prefs.getBoolean(KEY_BANK, true),
        depositEnding = prefs.getBoolean(KEY_DEPOSIT, true),
        daysBefore = prefs.getInt(KEY_DAYS, 3),
        cashbackMonth = prefs.getBoolean(KEY_CASHBACK, true),
        subscriptions = prefs.getBoolean(KEY_SUB, true)
    )

    private companion object {
        const val KEY_BANK = "new_bank"
        const val KEY_DEPOSIT = "deposit_ending"
        const val KEY_DAYS = "days_before"
        const val KEY_CASHBACK = "cashback_month"
        const val KEY_SUB = "subscriptions"
        const val KEY_SENT = "sent"
        const val KEY_SENT_ORDER = "sent_order"
        const val SEP = ""
        const val MAX_SENT = 300
    }
}
