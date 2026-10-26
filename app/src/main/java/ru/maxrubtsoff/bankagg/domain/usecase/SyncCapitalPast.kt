package ru.maxrubtsoff.bankagg.domain.usecase

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import ru.maxrubtsoff.bankagg.domain.model.CapitalPast
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.repository.CapitalHistoryRepository
import ru.maxrubtsoff.bankagg.domain.repository.DepositRepository
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository
import ru.maxrubtsoff.bankagg.domain.repository.Today

/**
 * Keeps the saved days before today in step with deposits
 * entered with an earlier date (B-79). Today's point is [RecordCapitalDay]'s.
 */
class SyncCapitalPast(
    private val history: CapitalHistoryRepository,
    private val deposits: DepositRepository,
    private val settings: SettingsRepository,
    private val today: Today
) {
    suspend fun track() = trackDeposits()

    private suspend fun included(): Set<CapitalPart> =
        settings.capitalParts.first() intersect settings.features.first().capitalParts

    private suspend fun trackDeposits() {
        deposits.observeAll().distinctUntilChanged().collect { list ->
            val changed = CapitalPast.withDeposits(history.days(), list, included(), today.date.value)
            if (changed.isNotEmpty()) history.upsertAll(changed)
        }
    }
}
