package ru.maxrubtsoff.bankagg.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.DepositOrder
import ru.maxrubtsoff.bankagg.domain.model.Features

/** User settings: tracked banks, what counts in capital, small remembered choices. */
interface SettingsRepository : PinStore {

    /** Which parser version the log was last re-read with (B-45, B-46). */
    suspend fun repairedParserVersion(): Int

    suspend fun setRepairedParserVersion(version: Int)

    val selectedPackages: Flow<Set<String>>
    suspend fun setSelectedPackages(packages: Set<String>)
    suspend fun currentSelectedPackages(): Set<String>

    /** Sections the user keeps (F-21); all by default. */
    val features: Flow<Features>
    suspend fun setFeatures(features: Features)
    suspend fun currentFeatures(): Features

    /** First-launch setup (sections, banks) is done. An install that already had data counts as done. */
    val onboarded: Flow<Boolean>
    suspend fun setOnboarded(done: Boolean)

    /** Parts of capital included in the total (all by default). */
    val capitalParts: Flow<Set<CapitalPart>>
    suspend fun setCapitalParts(parts: Set<CapitalPart>)
    suspend fun currentCapitalParts(): Set<CapitalPart>

    /** The section of the Capital tab opened last. */
    suspend fun lastCapitalSection(): CapitalPart?
    suspend fun setLastCapitalSection(part: CapitalPart)

    /** How the deposits list is ordered (default: ending soonest first). */
    suspend fun depositOrder(): DepositOrder
    suspend fun setDepositOrder(order: DepositOrder)

    /** How "По категориям" is ordered (a CashbackSummary.Order key; null — default). */
    suspend fun cashbackOrder(): String?
    suspend fun setCashbackOrder(key: String)

    /** App lock: the PIN is in [PinStore]; here — whether the fingerprint may unlock. */
    val biometricEnabled: Flow<Boolean>
    suspend fun setBiometricEnabled(on: Boolean)

    /** Amounts hidden behind dust, and the two ways to toggle it. */
    val amountsHidden: Flow<Boolean>
    suspend fun setAmountsHidden(hidden: Boolean)
    val hideOnFlip: Flow<Boolean>
    suspend fun setHideOnFlip(on: Boolean)
    val hideBySwipe: Flow<Boolean>
    suspend fun setHideBySwipe(on: Boolean)

    /** F-30: the tours already taken (or skipped). */
    /** «Что такое Itogo» has been shown once (B-50). */
    suspend fun introShown(): Boolean = true

    suspend fun markIntroShown() {}

    val toursDone: Flow<Set<String>> get() = kotlinx.coroutines.flow.flowOf(emptySet())
    suspend fun markTourDone(key: String) {}

    /** B-39: «Недавние» show a blank card instead of the app. Off unless chosen; not tied to the code. */
    val hideInRecents: Flow<Boolean> get() = kotlinx.coroutines.flow.flowOf(false)
    suspend fun setHideInRecents(on: Boolean) {}

    /** When the automatic backup last wrote a copy (epoch ms; 0 if never). */
    suspend fun lastBackupAt(): Long
    suspend fun setLastBackupAt(at: Long)

    /** The currency used in the last purchase or sale (form default). */
    suspend fun lastCurrency(): String?
    suspend fun setLastCurrency(code: String)

    /** Rates refresh bookkeeping (epoch ms / epoch day of the Moscow date fetched). */
    suspend fun ratesLastAttemptAt(): Long
    suspend fun ratesLastSuccessDay(): Long?
    suspend fun setRatesAttempt(at: Long)
    suspend fun setRatesSuccess(epochDay: Long)
}
