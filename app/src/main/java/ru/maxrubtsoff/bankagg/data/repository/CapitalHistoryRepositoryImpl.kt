package ru.maxrubtsoff.bankagg.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.maxrubtsoff.bankagg.data.local.CapitalSnapshotDao
import ru.maxrubtsoff.bankagg.data.local.toDomain
import ru.maxrubtsoff.bankagg.data.local.toEntity
import ru.maxrubtsoff.bankagg.domain.model.CapitalPoint
import ru.maxrubtsoff.bankagg.domain.repository.CapitalHistoryRepository

class CapitalHistoryRepositoryImpl(private val dao: CapitalSnapshotDao) : CapitalHistoryRepository {
    override fun observeDays(): Flow<List<CapitalPoint>> = dao.observeAll().map { l -> l.map { it.toDomain() } }
    override suspend fun days(): List<CapitalPoint> = dao.all().map { it.toDomain() }
    override suspend fun upsert(point: CapitalPoint) = dao.upsert(point.toEntity())
    override suspend fun upsertAll(points: List<CapitalPoint>) = dao.upsertAll(points.map { it.toEntity() })
}
