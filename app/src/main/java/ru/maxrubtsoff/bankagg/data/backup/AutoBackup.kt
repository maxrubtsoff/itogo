package ru.maxrubtsoff.bankagg.data.backup

import androidx.room.InvalidationTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.maxrubtsoff.bankagg.data.local.AppDatabase
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Keeps the backup current without the user doing anything: when the data
 * changes, today's copy is rewritten a little later (one write for a burst of
 * notifications, not one per row), and right away when the app goes to the
 * background with changes pending.
 */
class AutoBackup(
    private val db: AppDatabase,
    private val backup: BackupManager,
    private val settings: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val pending = AtomicBoolean(false)
    private val writing = Mutex()

    private val _lastSaved = MutableStateFlow<Long?>(null)
    /** When the last copy was written (epoch ms); null if never. */
    val lastSaved: StateFlow<Long?> = _lastSaved.asStateFlow()

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch { _lastSaved.value = settings.lastBackupAt().takeIf { it > 0 } }
        db.invalidationTracker.addObserver(object : InvalidationTracker.Observer(TABLES) {
            override fun onInvalidated(tables: Set<String>) {
                pending.set(true)
                changes.tryEmit(Unit)
            }
        })
        scope.launch { changes.debounce(DELAY_MS).collect { save() } }
    }

    /** The app is leaving the screen: write pending changes now. */
    fun flush() {
        if (pending.get()) scope.launch { save() }
    }

    /** Writes a copy now (also after a restore, so the copy matches what is on screen). */
    suspend fun save() = writing.withLock {
        pending.set(false)
        runCatching { backup.autoSave() }
            .onSuccess { written ->
                // Not written: an empty database must not replace today's copy (B-91).
                if (!written) return@onSuccess
                val now = System.currentTimeMillis()
                settings.setLastBackupAt(now)
                _lastSaved.value = now
            }
            .onFailure { pending.set(true) }
    }

    /** «Сохранить сейчас» (U-30): a separate copy with its time; the status shows it at once. */
    suspend fun saveNow() = writing.withLock {
        backup.saveNow()
        val now = System.currentTimeMillis()
        settings.setLastBackupAt(now)
        _lastSaved.value = now
    }

    private companion object {
        const val DELAY_MS = 30_000L
        /** What the user entered or the bank told; screenshots and rates come back by themselves. */
        val TABLES = arrayOf("accounts", "transactions", "cashback", "deposits", "deposit_topups", "fx_operations", "cash", "cash_changes", "subscriptions")
    }
}
