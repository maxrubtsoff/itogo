package ru.maxrubtsoff.bankagg.domain.model

/** Whether the app's data is open (B-91). */
sealed interface StorageStatus {
    data object Ready : StorageStatus
    /** The Keystore does not answer yet: nothing is opened, nothing is made, a retry may help. */
    data object Waiting : StorageStatus
    /** The data can't be opened now; the file is untouched and the user chooses what next. */
    class Blocked(val decision: StorageDecision) : StorageStatus
}

enum class PasswordResult { OPENED, WRONG_PASSWORD, FAILED }
