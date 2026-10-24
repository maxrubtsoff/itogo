package ru.maxrubtsoff.bankagg

import ru.maxrubtsoff.bankagg.data.notify.ReminderWorker
import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import ru.maxrubtsoff.bankagg.ui.common.ShotImage
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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
        // Saved logos are on screen from the first frame; a stale set refreshes in the background.
        container.bankIcons.start()
        // Banks chosen before F-22 become the user's banks, once.
        val banks = container.banks   // fills the bank directory before anything reads a bank
        MainScope().launch { banks.migrate() }
        // Candidates noticed by the old, looser check that aren't banks go away (B-25).
        MainScope().launch { container.newBankAnswers.sweep() }
        ReminderWorker.schedule(this)
        container.autoBackup.start()
        // Pictures saved before F-26 get sealed with the phone's key.
        MainScope().launch(kotlinx.coroutines.Dispatchers.IO) {
            ShotImageFiles.sealExisting(listOf(java.io.File(filesDir, "cashback_shots"), java.io.File(filesDir, "deposit_shots")))
        }
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
