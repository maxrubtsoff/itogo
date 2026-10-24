package ru.maxrubtsoff.bankagg.domain.model

/** Changing the cash amount in one of three ways; the result can never go below zero. */
object CashEdit {

    enum class Mode { SET, ADD, TAKE }

    /** The new amount in kopecks, or null when taking more than there is. */
    fun result(currentMinor: Long, mode: Mode, inputMinor: Long): Long? = when (mode) {
        Mode.SET -> inputMinor.takeIf { it >= 0L }
        Mode.ADD -> currentMinor + inputMinor
        Mode.TAKE -> (currentMinor - inputMinor).takeIf { it >= 0L }
    }
}
