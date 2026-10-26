package ru.maxrubtsoff.bankagg.domain.usecase

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CapitalPoint
import ru.maxrubtsoff.bankagg.domain.model.CapitalSnapshot
import ru.maxrubtsoff.bankagg.domain.repository.CapitalHistoryRepository

/**
 * Keeps today's point of the capital history (F-36): rewritten after every change
 * of the capital — a notification, cash, a deal, a deposit — and once a day by the
 * worker, which catches interest and the rate without any action.
 */
class RecordCapitalDay(
    private val getCapital: GetCapital,
    private val history: CapitalHistoryRepository,
    private val lock: HistoryLock = HistoryLock()
) {
    /** Follows the capital for as long as the caller lives. */
    @OptIn(FlowPreview::class)
    suspend fun track() {
        getCapital().debounce(DEBOUNCE_MS).collect { save(it) }
    }

    /** One write right now (the daily worker). */
    suspend fun now() = save(getCapital().first())

    private suspend fun save(snap: CapitalSnapshot) {
        // Nothing to count yet: no empty line at the start of the history.
        if (snap.parts.none { it.hasData }) return
        val point = CapitalPoint(
            snap.today, snap.totalMinor,
            CapitalPart.entries.associateWith { p -> snap.parts.firstOrNull { it.part == p }?.valueMinor ?: 0L }
        )
        lock.run { if (history.days().lastOrNull() != point) history.upsert(point) }
    }

    private companion object {
        const val DEBOUNCE_MS = 1_500L
    }
}
