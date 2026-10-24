package ru.maxrubtsoff.bankagg.data.repository.sample

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CbrBanks
import ru.maxrubtsoff.bankagg.domain.model.Subscription
import ru.maxrubtsoff.bankagg.domain.repository.BankRepository
import ru.maxrubtsoff.bankagg.domain.repository.OcrEngine
import ru.maxrubtsoff.bankagg.domain.repository.SubscriptionRepository

/** Subscriptions of a tour (U-54): in memory. */
class SampleSubscriptionRepository(subscriptions: List<Subscription>) : SubscriptionRepository {

    private val items = MutableStateFlow(subscriptions)

    override fun observeAll(): Flow<List<Subscription>> = items

    override suspend fun save(subscription: Subscription) = items.update { it.filterNot { s -> s.id == subscription.id } + subscription }

    override suspend fun delete(id: String) = items.update { list -> list.filterNot { it.id == id } }
}

/** The banks of a tour (U-54): the example ones; the person's own list is neither read nor changed. */
class SampleBankRepository(banks: List<BankBrand>) : BankRepository {

    private val items = MutableStateFlow(banks)

    override val myBanks: Flow<List<BankBrand>> = items

    override suspend fun current(): List<BankBrand> = items.value

    override suspend fun add(bank: BankBrand) = items.update { if (bank in it) it else it + bank }

    override suspend fun update(bank: BankBrand) = items.update { list -> list.map { if (it == bank) bank else it } }

    override suspend fun remove(key: String) = items.update { list -> list.filterNot { it.key == key } }

    override suspend fun cbrEntries(): List<CbrBanks.Entry> = emptyList()
}

/** A tour reads no real screenshots: the engine is never ready, what a shot "says" comes from the example data. */
object SampleOcrEngine : OcrEngine {
    override suspend fun isReady(): Boolean = false
    override suspend fun recognize(imagePath: String): String? = null
}
