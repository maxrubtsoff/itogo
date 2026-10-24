package ru.maxrubtsoff.bankagg.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.maxrubtsoff.bankagg.data.local.EventDao
import ru.maxrubtsoff.bankagg.data.local.toDomain
import ru.maxrubtsoff.bankagg.data.local.toEntity
import ru.maxrubtsoff.bankagg.domain.model.CapturedEvent
import ru.maxrubtsoff.bankagg.domain.repository.EventRepository

class EventRepositoryImpl(
    private val dao: EventDao
) : EventRepository {

    override suspend fun trim(parsedBefore: Long, unparsedBefore: Long): Int = dao.trim(parsedBefore, unparsedBefore)

    override suspend fun record(event: CapturedEvent): Boolean =
        dao.insert(event.toEntity()) != -1L

    override suspend fun hasSimilar(contentHash: String, from: Long, to: Long): Boolean =
        dao.countSimilar(contentHash, from, to) > 0

    override fun observeRecent(limit: Int): Flow<List<CapturedEvent>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }
}
