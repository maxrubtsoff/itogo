package ru.maxrubtsoff.bankagg.ui.common

import ru.maxrubtsoff.bankagg.ui.settings.BanksViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.maxrubtsoff.bankagg.di.AppContainer
import ru.maxrubtsoff.bankagg.ui.capital.CapitalViewModel
import ru.maxrubtsoff.bankagg.ui.cashback.CashbackViewModel
import ru.maxrubtsoff.bankagg.ui.subscriptions.SubscriptionsViewModel
import ru.maxrubtsoff.bankagg.ui.deposits.DepositsViewModel
import ru.maxrubtsoff.bankagg.ui.log.LogViewModel
import ru.maxrubtsoff.bankagg.ui.settings.SettingsViewModel

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
                container.ledgerRepository
            ) as T
        modelClass.isAssignableFrom(LogViewModel::class.java) ->
            LogViewModel(container.eventRepository) as T
        modelClass.isAssignableFrom(BanksViewModel::class.java) ->
            BanksViewModel(
                container.banks, container.settings, container.bankIcons.icons, container.logoMaker, container.bankCandidates, container.newBankAnswers,
                container.ledgerRepository, container.depositRepository, container.cashbackRepository, container.subscriptionRepository, container.today, container.undo
            ) as T
        modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
            SettingsViewModel(container.settings) { container.checkPin.setNew(it) } as T
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
            SubscriptionsViewModel(container.subscriptionRepository, container.ledgerRepository, container.banks, container.today) as T
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
