package ru.maxrubtsoff.bankagg.data.local

import android.content.Context
import ru.maxrubtsoff.bankagg.data.backup.BackupPassword
import ru.maxrubtsoff.bankagg.data.security.DbPasswordSeal
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.maxrubtsoff.bankagg.domain.model.PasswordResult
import ru.maxrubtsoff.bankagg.domain.model.StorageAction
import ru.maxrubtsoff.bankagg.domain.model.StorageDecision
import ru.maxrubtsoff.bankagg.domain.model.StorageGuard
import ru.maxrubtsoff.bankagg.domain.model.StorageStatus
import ru.maxrubtsoff.bankagg.domain.repository.StorageAccess

/**
 * The one place that opens the database. Everything that needs the data asks
 * [isReady] first; while it is false no empty database exists and nothing is written.
 */
class StorageGate(context: Context) : StorageAccess {

    private val appContext = context.applicationContext
    private val _status = MutableStateFlow<StorageStatus>(StorageStatus.Waiting)
    override val status: StateFlow<StorageStatus> = _status.asStateFlow()

    @Volatile var database: AppDatabase? = null
        private set

    override val isReady: Boolean get() = database != null

    /** Opens the data if it is not open yet; safe to call from anywhere, any number of times. */
    @Synchronized
    fun open(): StorageStatus {
        if (database != null) return StorageStatus.Ready
        val next = when (val r = AppDatabase.open(appContext)) {
            is AppDatabase.Companion.OpenResult.Opened -> {
                database = r.db
                ensurePasswordSeal()
                StorageStatus.Ready
            }
            is AppDatabase.Companion.OpenResult.Blocked ->
                if (r.decision == StorageDecision.WAIT) StorageStatus.Waiting else StorageStatus.Blocked(r.decision)
        }
        _status.value = next
        return next
    }

    /**
     * The backup password opens the data where the phone's key is lost (B-91): the
     * passphrase it unseals goes back under the phone's key and the database opens.
     */
    @Synchronized
    override fun unlockWithPassword(password: CharArray): PasswordResult {
        if (database != null) return PasswordResult.OPENED
        val passphrase = DbPasswordSeal.open(appContext, password) ?: return PasswordResult.WRONG_PASSWORD
        if (!DatabaseCipher.adopt(appContext, passphrase)) return PasswordResult.FAILED
        return if (open() == StorageStatus.Ready) PasswordResult.OPENED else PasswordResult.FAILED
    }

    /** A password set before this existed gets its seal now, in the background (PBKDF2 is slow on purpose). */
    private fun ensurePasswordSeal() {
        if (DbPasswordSeal.exists(appContext)) return
        thread(name = "db-password-seal") {
            runCatching {
                val password = BackupPassword(appContext).get() ?: return@thread
                val passphrase = DatabaseCipher.storedPassphrase(appContext) ?: return@thread
                DbPasswordSeal.write(appContext, passphrase, password)
            }
        }
    }

    /** Another try after a refusal. */
    override fun retry(): StorageStatus = open()

    /**
     * «Начать заново»: the old file is moved aside to `.broken` (never erased) and an
     * empty database is made in its place. Only from a closed state; false if the file
     * could not be moved, and then nothing changes.
     */
    @Synchronized
    override fun startOver(): Boolean {
        if (database != null) return false
        val decision = (status.value as? StorageStatus.Blocked)?.decision ?: return false
        if (StorageAction.START_OVER !in StorageGuard.actions(decision)) return false
        if (!AppDatabase.quarantine(appContext)) return false
        return open() == StorageStatus.Ready
    }
}
