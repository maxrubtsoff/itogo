package ru.maxrubtsoff.bankagg.domain.repository

import kotlinx.coroutines.flow.StateFlow
import ru.maxrubtsoff.bankagg.domain.model.PasswordResult
import ru.maxrubtsoff.bankagg.domain.model.StorageStatus

/** The door to the app's data (B-91): whether it is open, and what the user can do when it is not. */
interface StorageAccess {
    val status: StateFlow<StorageStatus>
    val isReady: Boolean
    /** Another try after a refusal. */
    fun retry(): StorageStatus
    /** The backup password opens the data where the phone's key is lost. */
    fun unlockWithPassword(password: CharArray): PasswordResult
    /** «Начать заново»: the old file is moved aside, never erased. False if nothing changed. */
    fun startOver(): Boolean
}
