package ru.maxrubtsoff.bankagg.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.maxrubtsoff.bankagg.domain.model.CapitalHistory
import ru.maxrubtsoff.bankagg.domain.model.CapitalPoint
import ru.maxrubtsoff.bankagg.domain.repository.CapitalHistoryRepository
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository

/**
 * The saved days as the screens read them (B-101): the total of every day is made of
 * the parts counted now, so switching a part moves the whole line and leaves no step.
 * Writers use the raw repository.
 */
class CapitalDaysScope(
    private val inner: CapitalHistoryRepository,
    private val settings: SettingsRepository
) : CapitalHistoryRepository by inner {
    override fun observeDays(): Flow<List<CapitalPoint>> =
        combine(inner.observeDays(), settings.capitalParts, settings.features) { days, parts, features ->
            CapitalHistory.totalled(days, parts intersect features.capitalParts)
        }
}
