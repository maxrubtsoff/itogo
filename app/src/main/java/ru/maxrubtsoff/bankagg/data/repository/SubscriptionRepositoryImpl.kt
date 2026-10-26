package ru.maxrubtsoff.bankagg.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.maxrubtsoff.bankagg.data.local.SubscriptionDao
import ru.maxrubtsoff.bankagg.data.local.toDomain
import ru.maxrubtsoff.bankagg.data.local.toEntity
import ru.maxrubtsoff.bankagg.domain.model.Subscription
import ru.maxrubtsoff.bankagg.domain.repository.SubscriptionRepository

class SubscriptionRepositoryImpl(private val dao: SubscriptionDao) : SubscriptionRepository {
    override fun observeAll(): Flow<List<Subscription>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun get(id: String): Subscription? = dao.get(id)?.toDomain()
    override suspend fun save(subscription: Subscription) = dao.upsert(subscription.toEntity())
    override suspend fun delete(id: String) = dao.delete(id)
}
