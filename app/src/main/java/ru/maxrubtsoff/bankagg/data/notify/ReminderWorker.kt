package ru.maxrubtsoff.bankagg.data.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import ru.maxrubtsoff.bankagg.App
import ru.maxrubtsoff.bankagg.domain.model.CashbackMonth
import ru.maxrubtsoff.bankagg.domain.model.Reminders
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Once a day, around nine in the morning, offline: deposits closing soon, cashback of a new month (F-24). */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as App).container
        container.checkReminders()
        container.trimJournal()
        return Result.success()
    }

    companion object {
        private const val NAME = "reminders"

        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(9, 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** Shared by the daily worker and a check right after deposits change. */
suspend fun ru.maxrubtsoff.bankagg.di.AppContainer.checkReminders() {
    val settings = reminders.current()
    if (!notifier.allowed()) return
    val today = this.today.date.value
    val deposits = depositRepository.observeAll().first()
    val filled = cashbackRepository.observeMonth(CashbackMonth.keyOf(today)).first().isNotEmpty()
    val hidden = this.settings.amountsHidden.first()
    val subs = subscriptionRepository.observeAll().first()
    val due = Reminders.due(today, deposits, filled, settings, hidden, reminders.sent(), subs)
    due.forEach { notifier.show(it) }
    reminders.markSent(due.map { it.id })
}
