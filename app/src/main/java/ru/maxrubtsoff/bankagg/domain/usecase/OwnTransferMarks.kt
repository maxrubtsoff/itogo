package ru.maxrubtsoff.bankagg.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ru.maxrubtsoff.bankagg.domain.model.OwnTransfers
import ru.maxrubtsoff.bankagg.domain.repository.EventRepository

/** Operations whose notification says «перевод себе» (F-59): their ids equal the events' hashes. */
class OwnTransferMarks(private val events: EventRepository) {
    fun observe(): Flow<Set<String>> =
        events.observeRecent(Int.MAX_VALUE)
            .map { list -> list.filter { it.parsed && OwnTransfers.isOwnText(it.title, it.text) }.map { it.dedupHash }.toSet() }
            .distinctUntilChanged()
}
