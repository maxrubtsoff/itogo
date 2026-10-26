package ru.maxrubtsoff.bankagg.data.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ru.maxrubtsoff.bankagg.App
import ru.maxrubtsoff.bankagg.domain.model.StorageStatus
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Once a day, in the evening, offline: writes the day's point of the capital — it catches interest and the rate (F-36). */
class CapitalSnapshotWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as App).container
        if (!container.isReady && container.storage.retry() != StorageStatus.Ready) return Result.retry()
        runCatching { container.recordCapitalDay.now() }
        return Result.success()
    }

    companion object {
        private const val NAME = "capital-day"

        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(21, 0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<CapitalSnapshotWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
