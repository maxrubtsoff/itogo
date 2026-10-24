package ru.maxrubtsoff.bankagg.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.maxrubtsoff.bankagg.domain.model.ReminderSettings

/** What the app tells about by itself (F-24), and what it has told already. */
interface ReminderRepository {
    val settings: Flow<ReminderSettings>
    suspend fun current(): ReminderSettings
    suspend fun set(settings: ReminderSettings)

    suspend fun sent(): Set<String>
    suspend fun markSent(ids: Collection<String>)
}
