package ru.maxrubtsoff.bankagg.di

import android.content.Context
import ru.maxrubtsoff.bankagg.data.ocr.ShotImageStore
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleBankRepository
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleCashRepository
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleCashbackRepository
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleCashbackShotRepository
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleDepositRepository
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleDepositShotRepository
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleFxRepository
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleLedgerRepository
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleOcrEngine
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleRatesRepository
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleRatesSource
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleSettings
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleSubscriptionRepository
import ru.maxrubtsoff.bankagg.domain.model.CategoryResolver
import ru.maxrubtsoff.bankagg.domain.model.Features
import ru.maxrubtsoff.bankagg.domain.model.TourSample
import ru.maxrubtsoff.bankagg.domain.repository.BankRepository
import ru.maxrubtsoff.bankagg.domain.repository.CashRepository
import ru.maxrubtsoff.bankagg.domain.repository.CashbackRepository
import ru.maxrubtsoff.bankagg.domain.repository.DepositRepository
import ru.maxrubtsoff.bankagg.domain.repository.FxRepository
import ru.maxrubtsoff.bankagg.domain.repository.LedgerRepository
import ru.maxrubtsoff.bankagg.domain.repository.RatesRepository
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository
import ru.maxrubtsoff.bankagg.domain.repository.SubscriptionRepository
import ru.maxrubtsoff.bankagg.domain.repository.Today
import ru.maxrubtsoff.bankagg.domain.usecase.GetCapital
import ru.maxrubtsoff.bankagg.domain.usecase.RecognizeCashbackShot
import ru.maxrubtsoff.bankagg.domain.usecase.RecognizeDepositShot
import ru.maxrubtsoff.bankagg.domain.usecase.RefreshRates
import ru.maxrubtsoff.bankagg.ui.common.UndoCenter

/**
 * The sandbox of the tours (U-54): the same use-cases as [AppContainer], over
 * example data in memory. The real screens run on it unchanged; whatever is
 * done there lives and dies with this object — nothing reaches Room, the
 * settings or the backup, and no request leaves the phone. A fresh one is
 * built for every tour ([AppContainer.sandbox]), so «Ещё раз» starts clean.
 */
class SandboxContainer(context: Context, val today: Today, features: Features) {

    private val appContext = context.applicationContext
    private val sample = TourSample.data(today.date.value)

    val settings: SettingsRepository = SampleSettings(features)

    val banks: BankRepository = SampleBankRepository(sample.banks)
    val ledgerRepository: LedgerRepository = SampleLedgerRepository(sample.accounts, sample.transactions)
    val depositRepository: DepositRepository = SampleDepositRepository(sample.deposits)
    val depositShotRepository = SampleDepositShotRepository()
    val fxRepository: FxRepository = SampleFxRepository(sample.fxOps)
    val ratesRepository: RatesRepository = SampleRatesRepository(sample.rates)
    val cashRepository: CashRepository = SampleCashRepository(sample.cash)
    val cashbackRepository: CashbackRepository = SampleCashbackRepository(sample.cashback)
    val cashbackShotRepository = SampleCashbackShotRepository()
    val subscriptionRepository: SubscriptionRepository = SampleSubscriptionRepository(sample.subscriptions)

    // Pickers and OCR are never really used in a tour; these only satisfy what the view-models ask for.
    val shotImageStore by lazy { ShotImageStore(appContext) }
    val depositShotImageStore by lazy { ShotImageStore(appContext, "deposit_shots") }
    val categoryResolver by lazy { CategoryResolver(null) }

    val undo by lazy { UndoCenter() }

    val recognizeCashbackShot by lazy { RecognizeCashbackShot(SampleOcrEngine, categoryResolver) }
    val recognizeDepositShot by lazy { RecognizeDepositShot(SampleOcrEngine) }
    val refreshRates by lazy { RefreshRates(SampleRatesSource, ratesRepository, settings) }
    val getCapital by lazy {
        GetCapital(ledgerRepository, depositRepository, fxRepository, ratesRepository, cashRepository, settings, today)
    }
}
