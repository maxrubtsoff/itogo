package ru.maxrubtsoff.bankagg.data.repository.sample

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CashbackItem
import ru.maxrubtsoff.bankagg.domain.model.CashbackMerge
import ru.maxrubtsoff.bankagg.domain.model.CashbackShot
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus
import ru.maxrubtsoff.bankagg.domain.model.TourSample
import ru.maxrubtsoff.bankagg.domain.repository.CashbackRepository
import ru.maxrubtsoff.bankagg.domain.repository.CashbackShotRepository

/** Cashback of a tour (U-54): in memory; the home-screen widget is not touched. */
class SampleCashbackRepository(items: List<CashbackItem>) : CashbackRepository {

    private val items = MutableStateFlow(items)

    override fun observeMonth(monthKey: String): Flow<List<CashbackItem>> =
        items.map { list -> list.filter { it.monthKey == monthKey }.sortedByDescending { it.percent } }

    override fun observeMonths(): Flow<List<String>> =
        items.map { list -> list.map { it.monthKey }.distinct().sortedDescending() }

    override suspend fun update(item: CashbackItem) {
        items.update { list ->
            val target = list.filter { it.monthKey == item.monthKey && it.brand == item.brand }
            val dropped = CashbackMerge.replacedByEdit(item, target).toSet()
            list.filterNot { it.id in dropped || it.id == item.id } + item
        }
    }

    override suspend fun countForBankMonth(monthKey: String, brand: BankBrand): Int =
        items.value.count { it.monthKey == monthKey && it.brand == brand }

    override suspend fun saveBankMonth(monthKey: String, brand: BankBrand, incoming: List<CashbackItem>, append: Boolean) {
        items.update { list ->
            val mine = list.filter { it.monthKey == monthKey && it.brand == brand }
            val result = if (append) CashbackMerge.append(mine, incoming) else incoming
            list.filterNot { it.monthKey == monthKey && it.brand == brand } + result
        }
    }

    override suspend fun listBankMonth(monthKey: String, brand: BankBrand): List<CashbackItem> =
        items.value.filter { it.monthKey == monthKey && it.brand == brand }.sortedBy { it.createdAt }

    override suspend fun delete(id: String) = items.update { list -> list.filterNot { it.id == id } }
}

/** Cashback screenshots of a tour (U-54): rows only, there are no image files behind them. */
class SampleCashbackShotRepository : CashbackShotRepository {

    private val shots = MutableStateFlow<List<CashbackShot>>(emptyList())

    override fun observeMonth(monthKey: String): Flow<List<CashbackShot>> =
        shots.map { list -> list.filter { it.monthKey == monthKey } }

    override suspend fun get(id: String): CashbackShot? = shots.value.firstOrNull { it.id == id }

    override fun observe(id: String): Flow<CashbackShot?> = shots.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun add(shot: CashbackShot) = update(shot)

    override suspend fun update(shot: CashbackShot) = put(shot)

    private fun put(shot: CashbackShot) = shots.update { it.filterNot { s -> s.id == shot.id } + shot }

    override suspend fun setStatus(id: String, status: ShotStatus) = shots.update { list ->
        list.map { if (it.id == id) it.copy(status = status) else it }
    }

    override suspend fun delete(id: String) = shots.update { list -> list.filterNot { it.id == id } }

    override suspend fun deleteKeepingImage(id: String) = delete(id)

    /** There is no file to remove. */
    override suspend fun deleteImage(imagePath: String) {}

    /**
     * The example screenshot of the tour, just picked and still being read.
     * There is no file behind it: the head of the form draws a screenshot itself.
     */
    fun startExample(monthKey: String): String {
        put(CashbackShot(id = EXAMPLE_ID, monthKey = monthKey, imagePath = "", status = ShotStatus.PROCESSING))
        return EXAMPLE_ID
    }

    /**
     * The example is read: the bank and the rows come from the example data
     * ([TourSample.scannedCashbackLines], one percent left unread on purpose),
     * not from an engine that would have to read a picture.
     */
    fun finishExample() = shots.update { list ->
        list.map {
            if (it.id == EXAMPLE_ID) it.copy(brand = TourSample.BANK, status = ShotStatus.NEEDS_INPUT, lines = TourSample.scannedCashbackLines()) else it
        }
    }

    companion object {
        const val EXAMPLE_ID = "sample-shot-cashback"
    }
}
