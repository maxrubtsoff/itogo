package ru.maxrubtsoff.bankagg.domain.repository

/** Runs several repository writes as one atomic unit (a database transaction). */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}
