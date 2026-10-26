package ru.maxrubtsoff.bankagg

import ru.maxrubtsoff.bankagg.data.notify.ReminderWorker
import ru.maxrubtsoff.bankagg.data.notify.CapitalSnapshotWorker
import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import ru.maxrubtsoff.bankagg.ui.common.ShotImage
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import ru.maxrubtsoff.bankagg.domain.model.StorageStatus
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.di.AppContainer
import ru.maxrubtsoff.bankagg.data.ocr.ShotImageFiles
import java.time.YearMonth

class App : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        instance = this
        // First: opening the database decides whether a missing Keystore key may be made anew (B-91).
        // Closed data (the Keystore busy, the key lost) leaves everything below that needs it waiting.
        container.storage.retry()
        // Saved logos are on screen from the first frame; a stale set refreshes in the background.
        container.bankIcons.start()
        MainScope().launch {
            container.storage.status.first { it is StorageStatus.Ready }
            startDatabaseWork()
        }
        ReminderWorker.schedule(this)
        CapitalSnapshotWorker.schedule(this)
        // Pictures saved before F-26 get sealed with the phone's key.
        MainScope().launch(kotlinx.coroutines.Dispatchers.IO) {
            ShotImageFiles.sealExisting(listOf(java.io.File(filesDir, "cashback_shots"), java.io.File(filesDir, "deposit_shots")))
        }
    }

    private val databaseWorkStarted = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Everything that reads or writes the database; once, when the data is open (at launch or after a retry). */
    private fun startDatabaseWork() {
        if (!databaseWorkStarted.compareAndSet(false, true)) return
        // Banks chosen before F-22 become the user's banks, once.
        val banks = container.banks   // fills the bank directory before anything reads a bank
        MainScope().launch { banks.migrate() }
        // Candidates noticed by the old, looser check that aren't banks go away (B-25).
        MainScope().launch { container.newBankAnswers.sweep() }
        // The capital's history: the past is rebuilt once, today's point follows every change (F-36).
        MainScope().launch(kotlinx.coroutines.Dispatchers.IO) { runCatching { container.trackCapitalHistory() } }
        container.autoBackup.start()
        // Operations the old parser read wrong are re-read from the log, once (B-45, B-46).
        MainScope().launch { container.repairJournal() }
        // Old journal texts go once a day; on start too, the worker may not have run for a while (U-33).
        MainScope().launch { container.trimJournal() }
        // A new month changes the widget's rows: redraw it when the month flips.
        MainScope().launch {
            container.today.date.map { YearMonth.from(it) }.distinctUntilChanged().drop(1)
                .collect { container.refreshWidget() }
        }
    }

    companion object {
        lateinit var instance: App
            private set
    }

    /** Stored screenshots are encrypted (F-26): Coil opens them through [ShotImage.Fetcher]. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(ShotImage.Fetcher.Factory())
            add(ShotImage.Key())
        }
        .build()
}
