package ru.maxrubtsoff.bankagg.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.maxrubtsoff.bankagg.domain.model.CapitalPoint

/** The daily points of the capital (table capital_days), one per day. */
interface CapitalHistoryRepository {
    fun observeDays(): Flow<List<CapitalPoint>>
    suspend fun days(): List<CapitalPoint>
    /** Writes the day's point, replacing one of the same date. */
    suspend fun upsert(point: CapitalPoint)
    suspend fun upsertAll(points: List<CapitalPoint>)
}
