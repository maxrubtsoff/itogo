package ru.maxrubtsoff.bankagg.data.repository.sample

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.DepositOrder
import ru.maxrubtsoff.bankagg.domain.model.DepositSort
import ru.maxrubtsoff.bankagg.domain.model.Features
import ru.maxrubtsoff.bankagg.domain.model.LockAttempts
import ru.maxrubtsoff.bankagg.domain.model.StoredPin
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository

/**
 * The settings a tour's screens read (U-54): the sections the person keeps,
 * everything else at its default, all in memory — a choice made during the
 * tour (a sort, hidden amounts) never reaches the real settings or the backup.
 */
class SampleSettings(features: Features) : SettingsRepository {

    private val featuresState = MutableStateFlow(features)
    private val partsState = MutableStateFlow(CapitalPart.entries.toSet())
    private val hidden = MutableStateFlow(false)
    private var capitalSection: CapitalPart? = null
    private var depositOrder = DepositOrder(DepositSort.DEFAULT)
    private var cashbackOrder: String? = null
    private var lastCurrency: String? = null

    override suspend fun repairedParserVersion(): Int = Int.MAX_VALUE
    override suspend fun setRepairedParserVersion(version: Int) {}

    override val selectedPackages: Flow<Set<String>> = MutableStateFlow(emptySet())
    override suspend fun setSelectedPackages(packages: Set<String>) {}
    override suspend fun currentSelectedPackages(): Set<String> = emptySet()

    override val features: Flow<Features> = featuresState.asStateFlow()
    override suspend fun setFeatures(features: Features) { featuresState.value = features }
    override suspend fun currentFeatures(): Features = featuresState.value

    override val onboarded: Flow<Boolean> = MutableStateFlow(true)
    override suspend fun setOnboarded(done: Boolean) {}

    override val capitalParts: Flow<Set<CapitalPart>> = partsState.asStateFlow()
    override suspend fun setCapitalParts(parts: Set<CapitalPart>) { partsState.value = parts }
    override suspend fun currentCapitalParts(): Set<CapitalPart> = partsState.value

    override suspend fun lastCapitalSection(): CapitalPart? = capitalSection
    override suspend fun setLastCapitalSection(part: CapitalPart) { capitalSection = part }

    override suspend fun depositOrder(): DepositOrder = depositOrder
    override suspend fun setDepositOrder(order: DepositOrder) { depositOrder = order }

    override suspend fun cashbackOrder(): String? = cashbackOrder
    override suspend fun setCashbackOrder(key: String) { cashbackOrder = key }

    override val biometricEnabled: Flow<Boolean> = MutableStateFlow(false)
    override suspend fun setBiometricEnabled(on: Boolean) {}

    override val amountsHidden: Flow<Boolean> = hidden.asStateFlow()
    override suspend fun setAmountsHidden(hidden: Boolean) { this.hidden.value = hidden }
    override val hideOnFlip: Flow<Boolean> = MutableStateFlow(false)
    override suspend fun setHideOnFlip(on: Boolean) {}
    override val hideBySwipe: Flow<Boolean> = MutableStateFlow(false)
    override suspend fun setHideBySwipe(on: Boolean) {}

    override suspend fun lastBackupAt(): Long = 0L
    override suspend fun setLastBackupAt(at: Long) {}

    override suspend fun lastCurrency(): String? = lastCurrency
    override suspend fun setLastCurrency(code: String) { lastCurrency = code }

    private var sliceMode = ru.maxrubtsoff.bankagg.domain.model.SliceMode.BY_PARTS
    override suspend fun sliceMode() = sliceMode
    override suspend fun setSliceMode(mode: ru.maxrubtsoff.bankagg.domain.model.SliceMode) { sliceMode = mode }

    override suspend fun ratesLastAttemptAt(): Long = 0L
    override suspend fun ratesLastSuccessDay(): Long? = null
    override suspend fun setRatesAttempt(at: Long) {}
    override suspend fun setRatesSuccess(epochDay: Long) {}

    // PinStore: a tour has no lock.
    override suspend fun lockPin(): StoredPin? = null
    override suspend fun setLockPin(pin: StoredPin?) {}
    override suspend fun lockAttempts(): LockAttempts = LockAttempts.NONE
    override suspend fun setLockAttempts(attempts: LockAttempts) {}
}
