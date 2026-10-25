package ru.maxrubtsoff.bankagg.data.repository.sample

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import ru.maxrubtsoff.bankagg.domain.model.CapitalPoint
import ru.maxrubtsoff.bankagg.domain.repository.CapitalHistoryRepository

/** The capital's days in a tour (U-54): in memory, empty to begin with. */
class SampleCapitalHistory : CapitalHistoryRepository {
    private val items = MutableStateFlow<List<CapitalPoint>>(emptyList())

    override fun observeDays(): Flow<List<CapitalPoint>> = items
    override suspend fun days(): List<CapitalPoint> = items.value
    override suspend fun upsert(point: CapitalPoint) = items.update { l -> l.filterNot { it.date == point.date } + point }
    override suspend fun upsertAll(points: List<CapitalPoint>) = items.update { l ->
        l.filterNot { o -> points.any { it.date == o.date } } + points
    }
}
