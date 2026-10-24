package ru.maxrubtsoff.bankagg.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.maxrubtsoff.bankagg.domain.model.Subscription

/** Subscriptions (F-27), including the «Не подписка» marks. */
interface SubscriptionRepository {
    fun observeAll(): Flow<List<Subscription>>
    suspend fun save(subscription: Subscription)
    suspend fun delete(id: String)
}
