package ru.maxrubtsoff.bankagg.domain.usecase

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import ru.maxrubtsoff.bankagg.domain.model.OwnTransfers
import ru.maxrubtsoff.bankagg.domain.repository.EventRepository

/**
 * Operations whose notification says «перевод себе» (F-59): their ids equal the events' hashes. Only the latest
 * [WINDOW] events are looked through and a burst of notifications is read once: the whole log is not walked
 * on every new push. A pair of legs needs no words; the words only save a lone leg.
 */
class OwnTransferMarks(private val events: EventRepository) {
    @OptIn(FlowPreview::class)
    fun observe(): Flow<Set<String>> = flow {
        // The first value goes at once, so the screen is not held back; the later ones of a burst wait.
        var first = true
        emitAll(
            events.observeRecent(WINDOW)
                .debounce { if (first) { first = false; 0L } else DEBOUNCE_MS }
                .map { list -> list.filter { it.parsed && OwnTransfers.isOwnText(it.title, it.text) }.map { it.dedupHash }.toSet() }
                .distinctUntilChanged()
        )
    }

    private companion object {
        const val WINDOW = 2_000
        const val DEBOUNCE_MS = 300L
    }
}
