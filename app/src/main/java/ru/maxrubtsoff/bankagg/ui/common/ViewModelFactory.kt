package ru.maxrubtsoff.bankagg.ui.common

import ru.maxrubtsoff.bankagg.ui.settings.BanksViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.maxrubtsoff.bankagg.di.AppContainer
import ru.maxrubtsoff.bankagg.di.SandboxContainer
import ru.maxrubtsoff.bankagg.ui.capital.CapitalViewModel
import ru.maxrubtsoff.bankagg.ui.history.CapitalHistoryViewModel
import ru.maxrubtsoff.bankagg.ui.cashback.CashbackViewModel
import ru.maxrubtsoff.bankagg.ui.subscriptions.SubscriptionsViewModel
import ru.maxrubtsoff.bankagg.ui.deposits.DepositsViewModel
import ru.maxrubtsoff.bankagg.ui.log.LogViewModel
import ru.maxrubtsoff.bankagg.ui.settings.SettingsViewModel
import ru.maxrubtsoff.bankagg.data.backup.BackupFileCheck
import ru.maxrubtsoff.bankagg.ui.storage.StorageErrorViewModel

/** Builds screen ViewModels from the app's manual DI container. */
class ViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(CapitalViewModel::class.java) ->
            CapitalViewModel(
                container.getCapital,
                container.settings,
                container.fxRepository,
                container.ratesRepository,
                container.cashRepository,
                container.refreshRates,
                container.today,
                container.undo,
                container.ledgerRepository,
                container.capitalHistory
            ) as T
        modelClass.isAssignableFrom(CapitalHistoryViewModel::class.java) ->
            CapitalHistoryViewModel(
                container.capitalHistory, container.ledgerRepository, container.fxRepository, container.ratesRepository,
                container.depositRepository, container.cashRepository, container.settings, container.today
            ) as T
        modelClass.isAssignableFrom(StorageErrorViewModel::class.java) ->
            StorageErrorViewModel(
                container.storage,
                BackupFileCheck(container.appContext)::check,
                // After «Начать заново» the data is open again, so the backup manager can be made.
                { uri, password -> container.backupManager.restoreFrom(uri, password) }
            ) as T
        modelClass.isAssignableFrom(LogViewModel::class.java) ->
            LogViewModel(container.eventRepository) as T
        modelClass.isAssignableFrom(BanksViewModel::class.java) ->
            BanksViewModel(
                container.banks, container.settings, container.bankIcons.icons, container.logoMaker, container.bankCandidates, container.newBankAnswers,
                container.ledgerRepository, container.depositRepository, container.cashbackRepository, container.subscriptionRepository, container.today, container.undo
            ) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(container.settings) {
                container.checkPin.setNew(it)
                // The PIN is turned on by a signed-in user: this session counts as unlocked (B-94).
                container.lockSession.markUnlocked()
            } as T
        modelClass.isAssignableFrom(CashbackViewModel::class.java) ->
            CashbackViewModel(
                container.cashbackRepository,
                container.today,
                container.cashbackShotRepository,
                container.recognizeCashbackShot,
                container.categoryResolver,
                container.shotImageStore,
                container.undo,
                container.settings
            ) as T
        modelClass.isAssignableFrom(SubscriptionsViewModel::class.java) ->
            SubscriptionsViewModel(container.subscriptionRepository, container.ledgerRepository, container.banks, container.today, container.logoMaker) as T
        modelClass.isAssignableFrom(DepositsViewModel::class.java) ->
            DepositsViewModel(
                container.depositRepository,
                container.depositShotRepository,
                container.today,
                container.recognizeDepositShot,
                container.depositShotImageStore,
                container.undo,
                container.settings
            ) as T
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    }
}

/**
 * The same screens' ViewModels over the tours' sandbox (U-54): example data in
 * memory, nothing saved. Only the screens a tour shows are here.
 */
class SandboxViewModelFactory(
    private val container: SandboxContainer
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(CapitalViewModel::class.java) ->
            CapitalViewModel(
                container.getCapital,
                container.settings,
                container.fxRepository,
                container.ratesRepository,
                container.cashRepository,
                container.refreshRates,
                container.today,
                container.undo,
                container.ledgerRepository,
                container.capitalHistory
            ) as T
        modelClass.isAssignableFrom(CashbackViewModel::class.java) ->
            CashbackViewModel(
                container.cashbackRepository,
                container.today,
                container.cashbackShotRepository,
                container.recognizeCashbackShot,
                container.categoryResolver,
                container.shotImageStore,
                container.undo,
                container.settings
            ) as T
        modelClass.isAssignableFrom(SubscriptionsViewModel::class.java) ->
            SubscriptionsViewModel(container.subscriptionRepository, container.ledgerRepository, container.banks, container.today, null) as T
        modelClass.isAssignableFrom(DepositsViewModel::class.java) ->
            DepositsViewModel(
                container.depositRepository,
                container.depositShotRepository,
                container.today,
                container.recognizeDepositShot,
                container.depositShotImageStore,
                container.undo,
                container.settings
            ) as T
        else -> throw IllegalArgumentException("No sandbox ViewModel: ${modelClass.name}")
    }
}
