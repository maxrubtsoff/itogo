package ru.maxrubtsoff.bankagg.ui.storage

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.maxrubtsoff.bankagg.data.backup.BackupFileCheck
import ru.maxrubtsoff.bankagg.data.local.StorageGate
import ru.maxrubtsoff.bankagg.data.local.StorageStatus

/** A copy picked on the screen: waits for its password, or has been given a wrong one. */
data class PickedCopy(val uri: Uri, val wrongPassword: Boolean)

data class StorageErrorState(
    /** Quiet retries still run: nothing is shown yet. */
    val quiet: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
    val copy: PickedCopy? = null,
    val confirmStartOver: Boolean = false
)

/**
 * The last-resort screen when the data does not open (B-91). It talks to the
 * [StorageGate] only; the database itself is never touched here.
 */
class StorageErrorViewModel(
    private val gate: StorageGate,
    private val check: (Uri, CharArray?) -> BackupFileCheck.Result,
    /** Loads the copy into the fresh database that [StorageGate.startOver] made. */
    private val restoreInto: suspend (Uri, CharArray?) -> Unit
) : ViewModel() {

    val status: StateFlow<StorageStatus> = gate.status

    private val _state = MutableStateFlow(StorageErrorState())
    val state: StateFlow<StorageErrorState> = _state.asStateFlow()

    init {
        // A busy Keystore often answers a moment later: a few quiet tries before the screen shows.
        viewModelScope.launch(Dispatchers.IO) {
            if (gate.status.value is StorageStatus.Waiting) {
                repeat(QUIET_TRIES) {
                    if (gate.isReady) return@launch
                    delay(QUIET_DELAY_MS)
                    gate.retry()
                }
            }
            _state.update { it.copy(quiet = false) }
        }
    }

    fun retry() = launchBusy {
        if (gate.retry() !is StorageStatus.Ready) _state.update { it.copy(message = "Пока не открылось. Попробуйте ещё раз чуть позже.") }
    }

    fun askStartOver(show: Boolean) = _state.update { it.copy(confirmStartOver = show) }

    fun startOver() {
        _state.update { it.copy(confirmStartOver = false) }
        launchBusy { if (!gate.startOver()) _state.update { it.copy(message = "Не получилось начать заново. Данные не тронуты.") } }
    }

    /** A copy was picked, or its password typed ([password] null on the first try). */
    fun restore(uri: Uri, password: CharArray?) = launchBusy {
        when (check(uri, password)) {
            BackupFileCheck.Result.OPENS -> {
                // The file opens: only now the old data is moved aside.
                if (!gate.startOver()) return@launchBusy fail("Не получилось начать заново. Данные не тронуты.")
                runCatching { restoreInto(uri, password) }
                    .onFailure { fail("Копию загрузить не удалось: ${it.message}") }
                _state.update { it.copy(copy = null) }
            }
            BackupFileCheck.Result.NEEDS_PASSWORD -> _state.update { it.copy(copy = PickedCopy(uri, false), message = null) }
            BackupFileCheck.Result.WRONG_PASSWORD -> _state.update { it.copy(copy = PickedCopy(uri, true)) }
            BackupFileCheck.Result.OTHER_PHONE -> fail("Эта копия не открывается на этом телефоне.")
            BackupFileCheck.Result.NOT_OURS -> fail("Файл не похож на копию Itogo.")
            BackupFileCheck.Result.UNREADABLE -> fail("Файл не читается. Данные не тронуты.")
        }
    }

    fun dismissCopy() = _state.update { it.copy(copy = null) }

    private fun fail(text: String) = _state.update { it.copy(message = text, copy = null) }

    private fun launchBusy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            withContext(Dispatchers.IO) { block() }
            _state.update { it.copy(busy = false) }
        }
    }

    private companion object {
        const val QUIET_TRIES = 3
        const val QUIET_DELAY_MS = 500L
    }
}
