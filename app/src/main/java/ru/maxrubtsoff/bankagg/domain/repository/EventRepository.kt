package ru.maxrubtsoff.bankagg.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.maxrubtsoff.bankagg.domain.model.CapturedEvent

interface EventRepository {
    /** Stores the raw event; returns false if it was a duplicate. */
    suspend fun record(event: CapturedEvent): Boolean

    /**
     * Whether an event with the same content hash was stored within [from]..[to] (epoch ms).
     * With a non-null [notificationKey] only a record with the same key, or without a key, matches.
     */
    suspend fun hasSimilar(contentHash: String, from: Long, to: Long, notificationKey: String?): Boolean
    fun observeRecent(limit: Int = 100): Flow<List<CapturedEvent>>

    /** Drops old texts (U-33): parsed ones posted before [parsedBefore], unparsed before [unparsedBefore]. */
    suspend fun trim(parsedBefore: Long, unparsedBefore: Long): Int

    /** The notification an operation came from: an operation's id is its event's hash (F-51). */
    suspend fun byHash(hash: String): CapturedEvent? = null

    /** Every stored event — for running a better parser over the log (B-45, B-46). */
    suspend fun all(): List<CapturedEvent>

    /** Re-parsing changed what the event means: its flag and hashes move with it. */
    suspend fun updateParse(id: Long, parsed: Boolean, dedupHash: String, contentHash: String)
}
