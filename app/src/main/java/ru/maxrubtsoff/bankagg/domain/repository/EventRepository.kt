package ru.maxrubtsoff.bankagg.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.maxrubtsoff.bankagg.domain.model.CapturedEvent

interface EventRepository {
    /** Stores the raw event; returns false if it was a duplicate. */
    suspend fun record(event: CapturedEvent): Boolean

    /** Whether an event with the same content hash was stored within [from]..[to] (epoch ms). */
    suspend fun hasSimilar(contentHash: String, from: Long, to: Long): Boolean
    fun observeRecent(limit: Int = 100): Flow<List<CapturedEvent>>

    /** Drops old texts (U-33): parsed ones posted before [parsedBefore], unparsed before [unparsedBefore]. */
    suspend fun trim(parsedBefore: Long, unparsedBefore: Long): Int
}
