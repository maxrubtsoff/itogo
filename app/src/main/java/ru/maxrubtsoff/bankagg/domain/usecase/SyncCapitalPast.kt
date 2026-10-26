package ru.maxrubtsoff.bankagg.domain.usecase

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.domain.model.CapitalPast
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.FxOperation
import ru.maxrubtsoff.bankagg.domain.repository.CapitalHistoryRepository
import ru.maxrubtsoff.bankagg.domain.repository.DepositRepository
import ru.maxrubtsoff.bankagg.domain.repository.FxRepository
import ru.maxrubtsoff.bankagg.domain.repository.RatesRepository
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository
import ru.maxrubtsoff.bankagg.domain.repository.Today

/**
 * Keeps the saved days before today in step with deposits and currency deals
 * entered with an earlier date (B-79, B-85). Today's point is [RecordCapitalDay]'s.
 */
class SyncCapitalPast(
    private val history: CapitalHistoryRepository,
    private val deposits: DepositRepository,
    private val fx: FxRepository,
    private val rates: RatesRepository,
    private val settings: SettingsRepository,
    private val today: Today
) {
    suspend fun track() = coroutineScope {
        launch { trackDeposits() }
        launch { trackFx() }
    }

    private suspend fun included(): Set<CapitalPart> =
        settings.capitalParts.first() intersect settings.features.first().capitalParts

    private suspend fun trackDeposits() {
        deposits.observeAll().distinctUntilChanged().collect { list ->
            val changed = CapitalPast.withDeposits(history.days(), list, included(), today.date.value)
            if (changed.isNotEmpty()) history.upsertAll(changed)
        }
    }

    private suspend fun trackFx() {
        var known: List<FxOperation>? = null
        fx.observeAll().distinctUntilChanged().collect { list ->
            val before = known
            known = list
            // The first list is only remembered: a deal made before has nothing to compare with.
            if (before == null) return@collect
            val changed = CapitalPast.withFxChange(history.days(), before, list, rates.all(), included(), today.date.value)
            if (changed.isNotEmpty()) history.upsertAll(changed)
        }
    }
}
