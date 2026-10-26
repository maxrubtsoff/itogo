package ru.maxrubtsoff.bankagg.domain.usecase

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One lock for everyone who reads the saved days of the capital and writes them back whole
 * (the daily point, the past following deposits and deals, a hand edit): without it two
 * of them at once would overwrite each other's part of a point.
 */
class HistoryLock {
    private val mutex = Mutex()
    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock { block() }
}
