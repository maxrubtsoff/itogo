package ru.maxrubtsoff.bankagg.data.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.maxrubtsoff.bankagg.domain.model.StorageAction
import ru.maxrubtsoff.bankagg.domain.model.StorageDecision
import ru.maxrubtsoff.bankagg.domain.model.StorageGuard

/** Whether the app's data is open (B-91). */
sealed interface StorageStatus {
    data object Ready : StorageStatus
    /** The Keystore does not answer yet: nothing is opened, nothing is made, a retry may help. */
    data object Waiting : StorageStatus
    /** The data can't be opened now; the file is untouched and the user chooses what next. */
    class Blocked(val decision: StorageDecision) : StorageStatus
}

/**
 * The one place that opens the database. Everything that needs the data asks
 * [isReady] first; while it is false no empty database exists and nothing is written.
 */
class StorageGate(context: Context) {

    private val appContext = context.applicationContext
    private val _status = MutableStateFlow<StorageStatus>(StorageStatus.Waiting)
    val status: StateFlow<StorageStatus> = _status.asStateFlow()

    @Volatile var database: AppDatabase? = null
        private set

    val isReady: Boolean get() = database != null

    /** Opens the data if it is not open yet; safe to call from anywhere, any number of times. */
    @Synchronized
    fun open(): StorageStatus {
        if (database != null) return StorageStatus.Ready
        val next = when (val r = AppDatabase.open(appContext)) {
            is AppDatabase.Companion.OpenResult.Opened -> {
                database = r.db
                StorageStatus.Ready
            }
            is AppDatabase.Companion.OpenResult.Blocked ->
                if (r.decision == StorageDecision.WAIT) StorageStatus.Waiting else StorageStatus.Blocked(r.decision)
        }
        _status.value = next
        return next
    }

    /** Another try after a refusal. */
    fun retry(): StorageStatus = open()

    /**
     * «Начать заново»: the old file is moved aside to `.broken` (never erased) and an
     * empty database is made in its place. Only from a closed state; false if the file
     * could not be moved, and then nothing changes.
     */
    @Synchronized
    fun startOver(): Boolean {
        if (database != null) return false
        val decision = (status.value as? StorageStatus.Blocked)?.decision ?: return false
        if (StorageAction.START_OVER !in StorageGuard.actions(decision)) return false
        if (!AppDatabase.quarantine(appContext)) return false
        return open() == StorageStatus.Ready
    }
}
