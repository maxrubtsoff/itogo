package ru.maxrubtsoff.bankagg.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.maxrubtsoff.bankagg.domain.model.BankCandidate

/** Apps that look like banks, waiting for the user's answer (F-23). */
interface CandidateRepository {
    val candidates: Flow<List<BankCandidate>>
    suspend fun current(): List<BankCandidate>

    /** Keeps the app's latest notification; a repeat only refreshes it. */
    suspend fun put(candidate: BankCandidate)

    /** Answered: the kept notification text is dropped. */
    suspend fun remove(packageName: String)

    /** "Not a bank": never asked about again. */
    suspend fun ignore(packageName: String)
    suspend fun ignored(): Set<String>

    /** Apps answered «Не банк», for the list in «Банки» (B-57). */
    val ignoredApps: Flow<Set<String>>

    /** The user changed their mind: the app may be offered again (B-57). */
    suspend fun unignore(packageName: String)
}
