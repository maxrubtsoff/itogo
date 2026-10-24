package ru.maxrubtsoff.bankagg.data.local

import androidx.room.withTransaction
import ru.maxrubtsoff.bankagg.domain.repository.TransactionRunner

class RoomTransactionRunner(private val db: AppDatabase) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
}
