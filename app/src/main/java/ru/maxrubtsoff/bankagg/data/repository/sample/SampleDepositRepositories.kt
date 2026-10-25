package ru.maxrubtsoff.bankagg.data.repository.sample

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.model.DepositShot
import ru.maxrubtsoff.bankagg.domain.model.DepositTopUp
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus
import ru.maxrubtsoff.bankagg.domain.model.TourSample
import java.time.LocalDate
import ru.maxrubtsoff.bankagg.domain.repository.DepositRepository
import ru.maxrubtsoff.bankagg.domain.repository.DepositShotRepository

/** Deposits of a tour (U-54): in memory, top-ups ride inside each deposit as in the real one. */
class SampleDepositRepository(deposits: List<Deposit>) : DepositRepository {

    private val items = MutableStateFlow(deposits)

    override fun observeAll(): Flow<List<Deposit>> = items

    override fun observe(id: String): Flow<Deposit?> = items.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun get(id: String): Deposit? = items.value.firstOrNull { it.id == id }

    override suspend fun add(deposit: Deposit) = items.update { it.filterNot { d -> d.id == deposit.id } + deposit }

    override suspend fun update(deposit: Deposit) = items.update { list -> list.map { if (it.id == deposit.id) deposit else it } }

    override suspend fun delete(id: String) = items.update { list -> list.filterNot { it.id == id } }

    override suspend fun addTopUp(depositId: String, topUp: DepositTopUp) = items.update { list ->
        list.map { if (it.id == depositId) it.copy(topUps = it.topUps.filterNot { t -> t.id == topUp.id } + topUp) else it }
    }

    override suspend fun deleteTopUp(id: String) = items.update { list ->
        list.map { d -> d.copy(topUps = d.topUps.filterNot { it.id == id }) }
    }
}

/** Deposit screenshots of a tour (U-54): rows only, there are no image files behind them. */
class SampleDepositShotRepository : DepositShotRepository {

    private val shots = MutableStateFlow<List<DepositShot>>(emptyList())

    override fun observeAll(): Flow<List<DepositShot>> = shots

    override suspend fun get(id: String): DepositShot? = shots.value.firstOrNull { it.id == id }

    override fun observe(id: String): Flow<DepositShot?> = shots.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun add(shot: DepositShot) = update(shot)

    override suspend fun update(shot: DepositShot) = put(shot)

    private fun put(shot: DepositShot) = shots.update { it.filterNot { s -> s.id == shot.id } + shot }

    override suspend fun setDepositId(ids: List<String>, depositId: String?) = shots.update { list ->
        list.map { if (it.id in ids) it.copy(depositId = depositId) else it }
    }

    override suspend fun setStatus(id: String, status: ShotStatus) = shots.update { list ->
        list.map { if (it.id == id) it.copy(status = status) else it }
    }

    override suspend fun delete(id: String) = shots.update { list -> list.filterNot { it.id == id } }

    override suspend fun deleteKeepingImage(id: String) = delete(id)

    /** There is no file to remove. */
    override suspend fun deleteImage(imagePath: String) {}

    /** The example screenshot of the tour, just picked and still being read; no file behind it. */
    fun startExample(): String {
        put(DepositShot(id = EXAMPLE_ID, imagePath = "", status = ShotStatus.PROCESSING))
        return EXAMPLE_ID
    }

    /** The example is read: the bank and the fields come from [TourSample.scannedDepositDraft], not from an engine. */
    fun finishExample(today: LocalDate) = shots.update { list ->
        list.map {
            if (it.id == EXAMPLE_ID) it.copy(brand = TourSample.BANK, status = ShotStatus.RECOGNIZED, draft = TourSample.scannedDepositDraft(today)) else it
        }
    }

    companion object {
        const val EXAMPLE_ID = "sample-shot-deposit"
    }
}
