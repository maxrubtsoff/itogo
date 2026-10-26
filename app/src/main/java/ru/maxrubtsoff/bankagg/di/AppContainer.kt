package ru.maxrubtsoff.bankagg.di

import java.io.File
import ru.maxrubtsoff.bankagg.data.repository.SubscriptionRepositoryImpl
import ru.maxrubtsoff.bankagg.domain.repository.SubscriptionRepository
import ru.maxrubtsoff.bankagg.data.security.PhoneKeys
import ru.maxrubtsoff.bankagg.domain.usecase.CheckPin
import ru.maxrubtsoff.bankagg.data.notify.ReminderStore
import ru.maxrubtsoff.bankagg.data.notify.AppNotifier
import ru.maxrubtsoff.bankagg.data.settings.CandidateStore
import ru.maxrubtsoff.bankagg.domain.usecase.NewBankAnswers
import ru.maxrubtsoff.bankagg.data.icons.LogoMaker
import ru.maxrubtsoff.bankagg.data.settings.BankStore
import ru.maxrubtsoff.bankagg.data.icons.BankIconCache
import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.ui.common.UndoCenter
import ru.maxrubtsoff.bankagg.ui.widget.CashbackWidget
import ru.maxrubtsoff.bankagg.data.backup.AutoBackup
import ru.maxrubtsoff.bankagg.data.backup.BackupManager
import ru.maxrubtsoff.bankagg.data.backup.BackupPassword
import ru.maxrubtsoff.bankagg.data.embed.OnnxTextEmbedder
import ru.maxrubtsoff.bankagg.data.local.AppDatabase
import ru.maxrubtsoff.bankagg.data.local.StorageGate
import ru.maxrubtsoff.bankagg.data.local.RoomTransactionRunner
import ru.maxrubtsoff.bankagg.data.ocr.ShotImageStore
import ru.maxrubtsoff.bankagg.data.ocr.TesseractOcrEngine
import ru.maxrubtsoff.bankagg.data.repository.CashbackRepositoryImpl
import ru.maxrubtsoff.bankagg.data.repository.CashbackShotRepositoryImpl
import ru.maxrubtsoff.bankagg.data.repository.DepositRepositoryImpl
import ru.maxrubtsoff.bankagg.data.repository.DepositShotRepositoryImpl
import ru.maxrubtsoff.bankagg.data.repository.EventRepositoryImpl
import ru.maxrubtsoff.bankagg.data.repository.LedgerRepositoryImpl
import ru.maxrubtsoff.bankagg.data.repository.CashRepositoryImpl
import ru.maxrubtsoff.bankagg.data.repository.FxRepositoryImpl
import ru.maxrubtsoff.bankagg.data.repository.RatesRepositoryImpl
import ru.maxrubtsoff.bankagg.data.rates.CbrRatesSource
import ru.maxrubtsoff.bankagg.domain.repository.CashRepository
import ru.maxrubtsoff.bankagg.domain.repository.FxRepository
import ru.maxrubtsoff.bankagg.domain.repository.RatesRepository
import ru.maxrubtsoff.bankagg.domain.usecase.GetCapital
import ru.maxrubtsoff.bankagg.domain.usecase.BuildCapitalHistory
import ru.maxrubtsoff.bankagg.domain.usecase.RecordCapitalDay
import ru.maxrubtsoff.bankagg.data.repository.CapitalHistoryRepositoryImpl
import ru.maxrubtsoff.bankagg.domain.repository.CapitalHistoryRepository
import ru.maxrubtsoff.bankagg.domain.usecase.MyBanksScope
import ru.maxrubtsoff.bankagg.domain.usecase.RefreshRates
import ru.maxrubtsoff.bankagg.data.settings.SettingsStore
import ru.maxrubtsoff.bankagg.data.time.SystemToday
import ru.maxrubtsoff.bankagg.domain.repository.CashbackRepository
import ru.maxrubtsoff.bankagg.domain.repository.CashbackShotRepository
import ru.maxrubtsoff.bankagg.domain.repository.DepositRepository
import ru.maxrubtsoff.bankagg.domain.repository.DepositShotRepository
import ru.maxrubtsoff.bankagg.domain.repository.EventRepository
import ru.maxrubtsoff.bankagg.domain.repository.LedgerRepository
import ru.maxrubtsoff.bankagg.domain.repository.OcrEngine
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository
import ru.maxrubtsoff.bankagg.domain.repository.TextEmbedder
import ru.maxrubtsoff.bankagg.domain.model.CategoryResolver
import ru.maxrubtsoff.bankagg.domain.model.Features
import ru.maxrubtsoff.bankagg.domain.model.JournalRetention
import ru.maxrubtsoff.bankagg.domain.usecase.ClassifyScreenshot
import ru.maxrubtsoff.bankagg.domain.usecase.RecognizeCashbackShot
import ru.maxrubtsoff.bankagg.domain.usecase.RecognizeDepositShot
import ru.maxrubtsoff.bankagg.domain.parser.NotificationParser
import ru.maxrubtsoff.bankagg.domain.usecase.RecordNotification
import ru.maxrubtsoff.bankagg.domain.usecase.RepairFromJournal

/** Manual DI container. Data stays local; the only network call is CBR rates ([CbrRatesSource]). */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

    /** Opens the data; while it is closed (B-91) nothing below that needs the database may be touched. */
    val storage by lazy { StorageGate(appContext) }
    val isReady: Boolean get() = storage.isReady
    // A getter, not a lazy: the repositories below are made only once the data is open.
    private val db: AppDatabase get() = storage.database ?: error("data is closed")

    val settings: SettingsRepository by lazy { SettingsStore(appContext) }

    /** The user's banks (F-22). */
    val banks: BankStore by lazy { BankStore(appContext, db, settings) }

    /** Cuts own bank logos out of gallery pictures (F-22). */
    val logoMaker: LogoMaker by lazy { LogoMaker(appContext) }

    /** Bank app icons kept on disk, refreshed at most daily (B-18). */
    val bankIcons: BankIconCache by lazy { BankIconCache(appContext) }

    /** Live current date; ticks at midnight. */
    val today: SystemToday by lazy { SystemToday(appContext) }

    val eventRepository: EventRepository by lazy { EventRepositoryImpl(db.eventDao()) }
    // Screens see only the user's banks (B-21); backup reads the database directly, so nothing is lost.
    val ledgerRepository: LedgerRepository by lazy { MyBanksScope.Ledger(LedgerRepositoryImpl(db.ledgerDao()), banks) }
    val cashbackRepository: CashbackRepository by lazy {
        MyBanksScope.Cashback(CashbackRepositoryImpl(db, onChanged = ::refreshWidget), banks)
    }

    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Redraws the home-screen cashback widget (after saving cashback, on a new month). */
    fun refreshWidget() {
        widgetScope.launch { runCatching { CashbackWidget().updateAll(appContext) } }
    }
    val cashbackShotRepository: CashbackShotRepository by lazy {
        CashbackShotRepositoryImpl(db.cashbackShotDao(), File(appContext.filesDir, "cashback_shots"))
    }
    val depositRepository: DepositRepository by lazy {
        MyBanksScope.Deposits(DepositRepositoryImpl(db.depositDao(), db.depositTopUpDao()), banks)
    }
    val depositShotRepository: DepositShotRepository by lazy {
        DepositShotRepositoryImpl(db.depositShotDao(), File(appContext.filesDir, "deposit_shots"))
    }
    val fxRepository: FxRepository by lazy { FxRepositoryImpl(db.fxDao()) }
    val ratesRepository: RatesRepository by lazy { RatesRepositoryImpl(db.fxDao()) }
    val cashRepository: CashRepository by lazy { CashRepositoryImpl(db) }

    // OCR (offline Tesseract) — behind the domain port; falls back gracefully if
    // the language model isn't present.
    val ocrEngine: OcrEngine by lazy { TesseractOcrEngine(appContext) }
    val shotImageStore by lazy { ShotImageStore(appContext) }
    val depositShotImageStore by lazy { ShotImageStore(appContext, "deposit_shots") }

    // Offline similarity model (assets/embedder) behind a domain port; the
    // resolver falls back to its dictionary if the model can't load.
    val textEmbedder: TextEmbedder by lazy { OnnxTextEmbedder(appContext) }
    val categoryResolver by lazy { CategoryResolver(textEmbedder) }

    val backupManager by lazy { BackupManager(appContext, db, settings, banks, bankCandidates) }
    val autoBackup by lazy { AutoBackup(db, backupManager, settings) }
    val backupPassword by lazy { BackupPassword(appContext) }
    val subscriptionRepository: SubscriptionRepository by lazy { SubscriptionRepositoryImpl(db.subscriptionDao()) }

    /** Keeps the notification journal from growing forever (U-33). */
    /**
     * The better parser goes over the log once and fixes what the old one got
     * wrong — wrong signs on transfers and advertisements taken for operations
     * (B-45, B-46).
     */
    suspend fun repairJournal() {
        if (settings.repairedParserVersion() >= NotificationParser.VERSION) return
        runCatching { repairFromJournal() }
            .onSuccess { settings.setRepairedParserVersion(NotificationParser.VERSION) }
    }

    suspend fun trimJournal() {
        val (parsed, unparsed) = JournalRetention.cutoffs(System.currentTimeMillis())
        runCatching { eventRepository.trim(parsed, unparsed) }
    }

    /** "Удалено · Вернуть" for every deletion in the app. */
    val undo by lazy { UndoCenter() }

    // Use cases
    val transactionRunner by lazy { RoomTransactionRunner(db) }
    val recordNotification by lazy { RecordNotification(eventRepository, ledgerRepository, transactionRunner) }
    val repairFromJournal by lazy { RepairFromJournal(eventRepository, ledgerRepository, transactionRunner) }

    /** The app's own notifications and their switches (F-24). */
    val reminders: ReminderStore by lazy { ReminderStore(appContext) }
    val notifier: AppNotifier by lazy { AppNotifier(appContext) }

    /** Apps that look like banks, and the answers to them (F-23). */
    val bankCandidates: CandidateStore by lazy { CandidateStore(appContext) }
    val newBankAnswers by lazy { NewBankAnswers(banks, bankCandidates, settings, recordNotification, appContext.packageName) }
    val recognizeCashbackShot by lazy { RecognizeCashbackShot(ocrEngine, categoryResolver) }
    val recognizeDepositShot by lazy { RecognizeDepositShot(ocrEngine) }
    val classifyScreenshot by lazy { ClassifyScreenshot(ocrEngine) }
    val refreshRates by lazy { RefreshRates(CbrRatesSource(), ratesRepository, settings) }
    val getCapital by lazy {
        GetCapital(ledgerRepository, depositRepository, fxRepository, ratesRepository, cashRepository, settings, today)
    }

    /** The capital's daily points (F-36). */
    val capitalHistory: CapitalHistoryRepository by lazy { CapitalHistoryRepositoryImpl(db.capitalSnapshotDao()) }
    val recordCapitalDay by lazy { RecordCapitalDay(getCapital, capitalHistory) }
    val buildCapitalHistory by lazy {
        BuildCapitalHistory(capitalHistory, ledgerRepository, depositRepository, fxRepository, ratesRepository, CbrRatesSource(), cashRepository, settings, today)
    }

    /** Rebuilds the past once when the history is empty, then keeps today's point up to date. */
    suspend fun trackCapitalHistory() {
        runCatching { buildCapitalHistory() }
        recordCapitalDay.track()
    }

    /**
     * A fresh sandbox for a tour (U-54): the use-cases over example data in
     * memory, nothing written anywhere. Built per tour, so a repeat starts clean.
     */
    fun sandbox(features: Features = Features.ALL): SandboxContainer = SandboxContainer(appContext, today, features)

    /** This process's lock session: unlocked yet, and when the app last went to the background. */
    val lockSession = LockSession()
    /** The lock-screen PIN: pauses after wrong tries, sealed with a phone-bound key (B-27, B-32). */
    val checkPin by lazy { CheckPin(settings, PhoneKeys.pinSeal(), tick = android.os.SystemClock::elapsedRealtime) }
}

class LockSession {
    var unlocked = false
    var backgroundedAt: Long? = null
    /** «Забыли код?» passed the phone's own check; lives in this process only, never saved (B-93). */
    var phoneVerified = false

    fun markUnlocked() {
        unlocked = true
        backgroundedAt = null
        phoneVerified = false
    }
}
