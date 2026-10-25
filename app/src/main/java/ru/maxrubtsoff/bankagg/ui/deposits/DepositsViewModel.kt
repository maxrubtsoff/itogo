package ru.maxrubtsoff.bankagg.ui.deposits

import ru.maxrubtsoff.bankagg.domain.model.DepositTopUp
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.data.ocr.ShotImageStore
import ru.maxrubtsoff.bankagg.domain.model.AccrualMethod
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.DepositBatch
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.model.DepositMath
import ru.maxrubtsoff.bankagg.domain.model.DepositOrder
import ru.maxrubtsoff.bankagg.domain.model.DepositShot
import ru.maxrubtsoff.bankagg.domain.model.DepositSort
import ru.maxrubtsoff.bankagg.domain.model.DepositSorting
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus
import ru.maxrubtsoff.bankagg.domain.repository.DepositRepository
import ru.maxrubtsoff.bankagg.domain.repository.DepositShotRepository
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository
import ru.maxrubtsoff.bankagg.domain.repository.Today
import ru.maxrubtsoff.bankagg.domain.usecase.RecognizeDepositShot
import ru.maxrubtsoff.bankagg.ui.common.UndoCenter
import java.time.LocalDate
import java.util.Collections
import java.util.UUID

class DepositsViewModel(
    private val repo: DepositRepository,
    private val shotRepo: DepositShotRepository,
    private val today: Today,
    private val recognizeShot: RecognizeDepositShot,
    private val imageStore: ShotImageStore,
    private val undo: UndoCenter,
    private val settings: SettingsRepository
) : ViewModel() {

    /** One deposit with its figures as of today, computed here rather than on every recomposition. */
    data class Item(val deposit: Deposit, val projection: DepositMath.Projection, val upcoming: Boolean)

    /** Everything the deposits section shows, recomputed when data or the date changes. */
    data class State(
        val today: LocalDate,
        val active: List<Item>,
        val completed: List<Item>,
        val summary: DepositMath.Summary,
        val completedTotals: DepositMath.Completed,
        val order: DepositOrder = DepositOrder(DepositSort.DEFAULT)
    ) {
        val isEmpty: Boolean get() = active.isEmpty() && completed.isEmpty()
    }

    val deposits: StateFlow<List<Deposit>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The list order the user picked; restored from settings. */
    private val order = MutableStateFlow(DepositOrder(DepositSort.DEFAULT))

    init {
        viewModelScope.launch { order.value = settings.depositOrder() }
    }

    val state: StateFlow<State> =
        combine(repo.observeAll(), today.date, order) { list, day, o -> stateOf(list, day, o) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), stateOf(emptyList(), today.date.value, order.value))

    /** Picks a sort in its natural direction; the current one stays as it is. */
    fun pickSort(sort: DepositSort) = setOrder(order.value.pick(sort))

    /** The arrow button: the same sort, the other way round. */
    fun flipSort() = setOrder(order.value.flipped())

    private fun setOrder(next: DepositOrder) {
        order.value = next
        viewModelScope.launch { settings.setDepositOrder(next) }
    }

    private fun stateOf(list: List<Deposit>, day: LocalDate, o: DepositOrder): State {
        val items = list.map { Item(it, DepositMath.project(it, day), DepositMath.isUpcoming(it, day)) }
        val (active, done) = items.partition { DepositMath.isActive(it.deposit, day) }
        return State(
            today = day,
            active = DepositSorting.sort(active, o, { it.deposit }, { it.projection }),
            completed = done.sortedByDescending { it.deposit.endDate },
            summary = DepositMath.summary(list, day),
            completedTotals = DepositMath.completed(list, day),
            order = o
        )
    }

    private val allShots: StateFlow<List<DepositShot>> =
        shotRepo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Shots that wait for review: the ones already saved into a deposit are not here. */
    val shots: StateFlow<List<DepositShot>> =
        allShots.map { list -> list.filter { it.depositId == null } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The screens a saved deposit was made from (B-64). */
    fun observeDepositShots(depositId: String): Flow<List<DepositShot>> =
        allShots.map { list -> list.filter { it.depositId == depositId } }

    fun observeDeposit(id: String): Flow<Deposit?> = repo.observe(id)

    /** One deposit with its projection as of the live date; null once deleted. */
    fun observeItem(id: String): Flow<Item?> =
        combine(repo.observe(id), today.date) { d, day ->
            d?.let { Item(it, DepositMath.project(it, day), DepositMath.isUpcoming(it, day)) }
        }

    val todayDate: StateFlow<LocalDate> get() = today.date

    // ---- shots: upload, recognise, delete ----

    private val inFlight: MutableSet<String> = Collections.synchronizedSet(HashSet())

    fun addShot(uri: Uri, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val saved = imageStore.save(uri) ?: return@launch
            val shot = DepositShot(
                id = UUID.randomUUID().toString(),
                imagePath = saved.path,
                status = ShotStatus.PROCESSING,
                createdAt = System.currentTimeMillis(),
                fileName = saved.displayName
            )
            inFlight += shot.id
            shotRepo.add(shot)
            onReady(shot.id)
            recognise(shot)
        }
    }

    /**
     * Several pictures at once: each becomes a shot, [onReady] gets their ids right
     * away, and they are recognised one after another (OCR is heavy).
     */
    fun addShots(uris: List<Uri>, onReady: (List<String>) -> Unit) {
        viewModelScope.launch {
            val shots = uris.mapNotNull { uri ->
                val saved = imageStore.save(uri) ?: return@mapNotNull null
                DepositShot(
                    id = UUID.randomUUID().toString(),
                    imagePath = saved.path,
                    status = ShotStatus.PROCESSING,
                    createdAt = System.currentTimeMillis(),
                    fileName = saved.displayName
                )
            }
            shots.forEach { inFlight += it.id; shotRepo.add(it) }
            onReady(shots.map { it.id })
            shots.forEach { recognise(it) }
        }
    }

    /** The shots of a batch, live; deleted ones drop out. */
    fun observeShots(ids: List<String>): Flow<List<DepositShot>> =
        if (ids.isEmpty()) flowOf(emptyList())
        else combine(ids.map { shotRepo.observe(it) }) { arr -> arr.filterNotNull() }

    /** Names the bank for every shot of a group the app couldn't place. */
    fun setShotsBrand(ids: List<String>, brand: BankBrand) {
        viewModelScope.launch { ids.forEach { id -> shotRepo.get(id)?.let { shotRepo.update(it.copy(brand = brand)) } } }
    }

    /** Saves every ready group as a deposit and drops its shots; then [onDone]. */
    fun saveGroups(groups: List<DepositBatch.Group>, onDone: () -> Unit) {
        viewModelScope.launch {
            for (g in groups) {
                val brand = g.brand ?: continue
                val d = g.draft
                val deposit = Deposit(
                    id = UUID.randomUUID().toString(),
                    brand = brand,
                    title = d.title,
                    principal = d.principal ?: continue,
                    rateBp = g.rateBp ?: continue,
                    startDate = d.startDate ?: continue,
                    endDate = d.endDate ?: continue,
                    exitAmount = d.exitAmount,
                    method = d.method ?: AccrualMethod.LINEAR,
                    createdAt = System.currentTimeMillis()
                )
                repo.add(deposit)
                // B-64: every screen of the group stays with the deposit, not just the first.
                shotRepo.setDepositId(g.ids, deposit.id)
            }
            onDone()
        }
    }

    /**
     * A group that needs the user's hand: its facts go into the first shot, the
     * other shots are dropped, and [onReady] opens that shot's full form.
     */
    fun collapseGroup(group: DepositBatch.Group, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val first = shotRepo.get(group.ids.first()) ?: return@launch
            shotRepo.update(first.copy(brand = group.brand, draft = group.draft, status = ShotStatus.NEEDS_INPUT))
            group.ids.drop(1).forEach { shotRepo.delete(it) }
            onReady(first.id)
        }
    }

    fun resumeIfStuck(shot: DepositShot) {
        if (shot.status != ShotStatus.PROCESSING || !inFlight.add(shot.id)) return
        viewModelScope.launch { recognise(shot) }
    }

    private suspend fun recognise(shot: DepositShot) {
        try {
            val draft = recognizeShot(shot)
            if (shotRepo.get(shot.id) != null) {
                shotRepo.update(shot.copy(brand = draft.brand, status = draft.status, draft = draft.fields))
            }
        } finally {
            inFlight -= shot.id
        }
    }

    fun observeShot(id: String): Flow<DepositShot?> = shotRepo.observe(id)

    /** The user deleted a shot: gone at once, "Вернуть" brings it back with its image. */
    fun deleteShot(id: String) {
        viewModelScope.launch {
            val shot = shotRepo.get(id) ?: return@launch
            shotRepo.deleteKeepingImage(id)
            undo.offer("Скрин удалён", restore = { shotRepo.add(shot) }, finish = { shotRepo.deleteImage(shot.imagePath) })
        }
    }

    // ---- save / delete ----

    fun saveDeposit(
        existingId: String?,
        brand: BankBrand,
        title: String?,
        principal: Money,
        rateBp: Int,
        startDate: LocalDate,
        endDate: LocalDate,
        exitAmount: Money?,
        method: AccrualMethod,
        shotIdToDiscard: String? = null,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val createdAt = existingId?.let { repo.get(it)?.createdAt } ?: System.currentTimeMillis()
            val deposit = Deposit(
                id = existingId ?: UUID.randomUUID().toString(),
                brand = brand,
                title = title?.trim()?.takeIf { it.isNotBlank() },
                principal = principal,
                rateBp = rateBp,
                startDate = startDate,
                endDate = endDate,
                exitAmount = exitAmount,
                method = method,
                createdAt = createdAt
            )
            if (existingId == null) repo.add(deposit) else repo.update(deposit)
            shotIdToDiscard?.let { shotRepo.setDepositId(listOf(it), deposit.id) }
            onDone()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            val deposit = repo.get(id) ?: return@launch
            val linked = shotRepo.observeAll().first().filter { it.depositId == id }.map { it.id }
            repo.delete(id)
            undo.offer("Вклад удалён", restore = {
                repo.add(deposit)
                deposit.topUps.forEach { repo.addTopUp(deposit.id, it) }
            }, finish = { linked.forEach { shotRepo.delete(it) } })
        }
    }

    /** A top-up (F-20); its income counts from [date] to the deposit's end. */
    fun addTopUp(depositId: String, amountMinor: Long, date: LocalDate) = viewModelScope.launch {
        repo.addTopUp(depositId, DepositTopUp(UUID.randomUUID().toString(), date, Money.ofMinor(amountMinor)))
    }

    fun deleteTopUp(depositId: String, t: DepositTopUp) = viewModelScope.launch {
        repo.deleteTopUp(t.id)
        undo.offer("Пополнение удалено", restore = { repo.addTopUp(depositId, t) })
    }
}
