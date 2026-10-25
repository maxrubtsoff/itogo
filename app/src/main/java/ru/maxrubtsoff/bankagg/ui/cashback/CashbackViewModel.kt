package ru.maxrubtsoff.bankagg.ui.cashback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.maxrubtsoff.bankagg.domain.model.CashbackSummary
import ru.maxrubtsoff.bankagg.domain.model.SortOrder
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.maxrubtsoff.bankagg.data.ocr.ShotImageStore
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CashbackBatch
import ru.maxrubtsoff.bankagg.domain.model.CashbackItem
import ru.maxrubtsoff.bankagg.domain.model.CashbackDraft
import ru.maxrubtsoff.bankagg.domain.model.CashbackRows
import ru.maxrubtsoff.bankagg.domain.model.CashbackMerge
import ru.maxrubtsoff.bankagg.domain.model.CashbackMonth
import ru.maxrubtsoff.bankagg.domain.model.CashbackShot
import ru.maxrubtsoff.bankagg.domain.model.CashbackSpotlight
import ru.maxrubtsoff.bankagg.domain.model.CategoryGroup
import ru.maxrubtsoff.bankagg.domain.model.CategoryGroups
import ru.maxrubtsoff.bankagg.domain.model.CategoryResolver
import ru.maxrubtsoff.bankagg.domain.model.Resolution
import ru.maxrubtsoff.bankagg.domain.model.ShotLine
import ru.maxrubtsoff.bankagg.domain.model.ShotStatus
import ru.maxrubtsoff.bankagg.domain.repository.CashbackRepository
import ru.maxrubtsoff.bankagg.domain.repository.CashbackShotRepository
import ru.maxrubtsoff.bankagg.domain.repository.Today
import ru.maxrubtsoff.bankagg.domain.usecase.RecognizeCashbackShot
import ru.maxrubtsoff.bankagg.ui.common.UndoCenter
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class CashbackViewModel(
    private val repo: CashbackRepository,
    private val today: Today,
    private val shotRepo: CashbackShotRepository,
    private val recognizeShot: RecognizeCashbackShot,
    private val resolver: CategoryResolver,
    private val imageStore: ShotImageStore,
    private val undo: UndoCenter,
    private val settings: SettingsRepository
) : ViewModel() {

    private val _categoryOrder = MutableStateFlow(SortOrder(CashbackSummary.Order.PERCENT))
    /** How "По категориям" is ordered; remembered between launches. */
    val categoryOrder: StateFlow<SortOrder<CashbackSummary.Order>> = _categoryOrder.asStateFlow()

    init {
        viewModelScope.launch { _categoryOrder.value = CashbackSummary.Order.decode(settings.cashbackOrder()) }
    }

    /** Picks a sort in its natural direction; the current one stays as it is. */
    fun pickCategoryOrder(order: CashbackSummary.Order) = setCategoryOrder(_categoryOrder.value.pick(order))

    /** The arrow button: the same sort, the other way round. */
    fun flipCategoryOrder() = setCategoryOrder(_categoryOrder.value.flipped())

    private fun setCategoryOrder(order: SortOrder<CashbackSummary.Order>) {
        _categoryOrder.value = order
        viewModelScope.launch { settings.setCashbackOrder(CashbackSummary.Order.encode(order)) }
    }

    /** The current month's key, live: flips at midnight on the 1st. */
    val currentMonthKey: StateFlow<String> =
        today.date.map { CashbackMonth.keyOf(it) }.distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, CashbackMonth.keyOf(today.date.value))

    val currentMonthItems: StateFlow<List<CashbackItem>> =
        currentMonthKey.flatMapLatest { repo.observeMonth(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val hasNextMonth: Flow<Boolean> =
        currentMonthKey.flatMapLatest { repo.observeMonth(CashbackMonth.nextKey(it)) }.map { it.isNotEmpty() }

    /** The home card: what matters this month, decided by [CashbackSpotlight]. */
    val spotlight: StateFlow<CashbackSpotlight.Spotlight> =
        combine(currentMonthItems, hasNextMonth, today.date) { items, hasNext, day ->
            CashbackSpotlight.build(items, hasNext, day)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            CashbackSpotlight.build(emptyList(), true, today.date.value)
        )

    fun monthItems(monthKey: String): Flow<List<CashbackItem>> = repo.observeMonth(monthKey)

    fun shots(monthKey: String): Flow<List<CashbackShot>> = shotRepo.observeMonth(monthKey)

    /** Names a piece of category text (dictionary, then the offline model). */
    suspend fun resolve(text: String): Resolution = resolver.resolve(text)

    /** Picker suggestions for what the user typed. */
    suspend fun suggest(query: String): List<CategoryGroup> = resolver.suggest(query)

    // ---- single-item save (manual edit of one row, used by the list) ----

    fun save(
        existing: CashbackItem?,
        monthKey: String,
        brand: BankBrand,
        category: String,
        groupKey: String,
        percent: Double
    ) {
        viewModelScope.launch {
            if (existing == null) {
                // Adding one row never asks: the same offer just updates its percent.
                repo.saveBankMonth(monthKey, brand, listOf(newItem(monthKey, brand, category, groupKey, percent)), append = true)
            } else {
                repo.update(existing.copy(brand = brand, category = category.trim(), groupKey = groupKey, percent = percent))
            }
        }
    }

    fun delete(item: CashbackItem) {
        viewModelScope.launch {
            repo.delete(item.id)
            undo.offer("Категория удалена", restore = { repo.update(item) })
        }
    }

    // ---- shots: upload, recognise, save-all, delete ----

    /**
     * A draft row in an editor: display text, its group, percent text, and the OCR line it came from.
     * [id] is the row's own name, so a list can keep it in place while others come and go (B-52).
     */
    data class DraftLine(
        val text: String,
        val groupKey: String,
        val percentText: String,
        val sourceText: String = "",
        val id: String = UUID.randomUUID().toString()
    ) {
        val iconKey: String get() = CategoryGroups.byKey(groupKey).iconKey
        val isComplete: Boolean get() = CashbackDraft.isComplete(text, percentText)
    }

    /**
     * Saves a picked image as a shot (status PROCESSING), opens it via [onReady],
     * recognises it once and stores the draft rows with the shot.
     */
    /** Shots whose OCR runs in this process (a PROCESSING shot not here was cut off by a process kill). */
    private val inFlight: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet())

    fun addShot(uri: Uri, monthKey: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val saved = imageStore.save(uri) ?: return@launch
            val shot = CashbackShot(
                id = UUID.randomUUID().toString(),
                monthKey = monthKey,
                imagePath = saved.path,
                brand = null,
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
     * Several pictures at once: each becomes a shot of [monthKey], [onReady] gets
     * their ids right away, and they are recognised one after another. A screen
     * that names its month ("Категории на ноябрь") moves to that month.
     */
    fun addShots(uris: List<Uri>, monthKey: String, onReady: (List<String>) -> Unit) {
        viewModelScope.launch {
            val shots = uris.mapNotNull { uri ->
                val saved = imageStore.save(uri) ?: return@mapNotNull null
                CashbackShot(
                    id = UUID.randomUUID().toString(),
                    monthKey = monthKey,
                    imagePath = saved.path,
                    brand = null,
                    status = ShotStatus.PROCESSING,
                    createdAt = System.currentTimeMillis(),
                    fileName = saved.displayName
                )
            }
            shots.forEach { inFlight += it.id; shotRepo.add(it) }
            onReady(shots.map { it.id })
            shots.forEach { recognise(it, adoptMonth = true) }
        }
    }

    /** The shots of a batch, live; deleted ones drop out. */
    fun observeShots(ids: List<String>): Flow<List<CashbackShot>> =
        if (ids.isEmpty()) flowOf(emptyList())
        else combine(ids.map { shotRepo.observe(it) }) { arr -> arr.filterNotNull() }

    /** Names the bank for the shots of a group the app couldn't place. */
    fun setShotsBrand(ids: List<String>, brand: BankBrand) {
        viewModelScope.launch { ids.forEach { id -> shotRepo.get(id)?.let { shotRepo.update(it.copy(brand = brand)) } } }
    }

    /** A group's rows as editor lines. */
    fun draftLines(lines: List<ShotLine>): List<DraftLine> = merged(
        lines.map { l ->
            DraftLine(text = l.text, groupKey = l.groupKey, percentText = l.percent?.let(::percentToText) ?: "", sourceText = l.sourceText)
        }
    )

    /** Drops the shots of a saved group. */
    fun discardShots(ids: List<String>) {
        viewModelScope.launch { ids.forEach { shotRepo.delete(it) } }
    }

    /** A group to fix by hand: its rows go into the first shot, the rest are dropped; [onReady] opens it. */
    /** «Вернуть» for a change made on the screen itself (a shot carried to another group, U-65). */
    fun offerUndo(message: String, restore: () -> Unit) = undo.offer(message, restore = { restore() })

    /** Puts a shot's bank back, or clears it (undo of a carry that named the bank). */
    fun restoreShotBrand(id: String, brand: BankBrand?) {
        viewModelScope.launch { shotRepo.get(id)?.let { shotRepo.update(it.copy(brand = brand)) } }
    }

    fun collapseGroup(group: CashbackBatch.Group, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val first = shotRepo.get(group.ids.first()) ?: return@launch
            shotRepo.update(first.copy(brand = group.brand, lines = group.lines, monthKey = group.monthKey))
            group.ids.drop(1).forEach { shotRepo.delete(it) }
            onReady(first.id)
        }
    }

    /**
     * Restarts recognition of a shot left in PROCESSING by an earlier process
     * (the app was killed mid-OCR); no-op while it is already running here.
     */
    fun resumeIfStuck(shot: CashbackShot) {
        if (shot.status != ShotStatus.PROCESSING || !inFlight.add(shot.id)) return
        viewModelScope.launch { recognise(shot) }
    }

    private suspend fun recognise(shot: CashbackShot, adoptMonth: Boolean = false) {
        try {
            val draft = recognizeShot(shot)
            // The user may have deleted the shot while OCR ran — don't bring it back.
            if (shotRepo.get(shot.id) != null) {
                // A screen of a later month moves there; an earlier one is a stray tab label ("В сентябре").
                val month = draft.monthKey?.takeIf { adoptMonth && it > shot.monthKey } ?: shot.monthKey
                shotRepo.update(shot.copy(brand = draft.brand, status = draft.status, lines = draft.lines, monthKey = month))
            }
        } finally {
            inFlight -= shot.id
        }
    }

    /** A shot, live (status flips from PROCESSING once OCR is done; null once deleted). */
    fun observeShot(id: String): Flow<CashbackShot?> = shotRepo.observe(id)

    /** A shot's stored draft rows as editable lines (no OCR — a shot is recognised once, in [addShot]). */
    fun draftLines(shot: CashbackShot): List<DraftLine> = merged(
        shot.lines.map { l ->
            DraftLine(
                text = l.text,
                groupKey = l.groupKey,
                percentText = l.percent?.let(::percentToText) ?: "",
                sourceText = l.sourceText
            )
        }
    )

    /** A screenshot that read one category twice gives one row (B-53). */
    private fun merged(lines: List<DraftLine>): List<DraftLine> =
        CashbackRows.merge(lines, name = { it.text }, percent = { CashbackDraft.percent(it.percentText) })

    /** Drops a shot and its image once its rows are saved. */
    fun discardShot(id: String) {
        viewModelScope.launch { shotRepo.delete(id) }
    }

    /** The user deleted a shot: gone at once, "Вернуть" brings it back with its image. */
    fun deleteShot(id: String) {
        viewModelScope.launch {
            val shot = shotRepo.get(id) ?: return@launch
            shotRepo.deleteKeepingImage(id)
            undo.offer("Скрин удалён", restore = { shotRepo.add(shot) }, finish = { shotRepo.deleteImage(shot.imagePath) })
        }
    }

    /** How a set of rows lands on a bank-month that already has cashback. */
    enum class SaveMode { APPEND, REPLACE }

    /** Ask "Дополнить / Заменить"? Only when the bank already has different offers this month. */
    suspend fun decideSave(monthKey: String, brand: BankBrand, lines: List<DraftLine>): CashbackMerge.Decision =
        CashbackMerge.decide(repo.listBankMonth(monthKey, brand), toItems(monthKey, brand, lines))

    /**
     * Writes [lines] as [brand]'s cashback for [monthKey]: [SaveMode.APPEND] adds
     * them to what is saved (same offer → new percent), [SaveMode.REPLACE]
     * replaces the bank's month. Then [onDone] on the main thread.
     */
    fun commit(
        monthKey: String,
        brand: BankBrand,
        lines: List<DraftLine>,
        mode: SaveMode,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repo.saveBankMonth(monthKey, brand, toItems(monthKey, brand, lines), append = mode == SaveMode.APPEND)
            onDone()
        }
    }

    /** Count of this bank's existing cashback rows this month (for the save dialog). */
    suspend fun existingCountForBank(monthKey: String, brand: BankBrand): Int =
        repo.countForBankMonth(monthKey, brand)


    // ---- helpers ----

    /** Draft rows → cashback items (the editors only allow saving when every row is complete). */
    private fun toItems(monthKey: String, brand: BankBrand, lines: List<DraftLine>): List<CashbackItem> =
        // Duplicates can't be saved, whatever the form let through (B-53).
        merged(lines).mapNotNull { l ->
            val pct = CashbackDraft.percent(l.percentText)
            if (l.text.isBlank() || pct == null) null
            else newItem(monthKey, brand, l.text, l.groupKey, pct)
        }

    private fun newItem(monthKey: String, brand: BankBrand, category: String, groupKey: String, percent: Double) =
        CashbackItem(
            id = UUID.randomUUID().toString(),
            monthKey = monthKey,
            brand = brand,
            category = category.trim(),
            percent = percent,
            createdAt = System.currentTimeMillis(),
            groupKey = groupKey
        )
}

internal fun percentToText(p: Double): String =
    if (p % 1.0 == 0.0) p.toInt().toString() else p.toString()
