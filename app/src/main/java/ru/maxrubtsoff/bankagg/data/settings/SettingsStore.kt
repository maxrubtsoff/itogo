package ru.maxrubtsoff.bankagg.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.DepositOrder
import ru.maxrubtsoff.bankagg.domain.model.Features
import ru.maxrubtsoff.bankagg.domain.model.DepositSort
import ru.maxrubtsoff.bankagg.domain.model.LockAttempts
import ru.maxrubtsoff.bankagg.domain.model.StoredPin
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository

/** User settings in SharedPreferences: banks, capital parts, small remembered choices. */
class SettingsStore(context: Context) : SettingsRepository {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    init {
        // Favourite cashback groups are gone: the home card decides by itself.
        if (prefs.contains(LEGACY_FAVORITES)) prefs.edit().remove(LEGACY_FAVORITES).apply()
    }

    // ---- «Что такое Itogo» shown once (B-50) ----

    override suspend fun introShown(): Boolean = prefs.getBoolean(KEY_INTRO_SHOWN, false)

    override suspend fun markIntroShown() {
        prefs.edit().putBoolean(KEY_INTRO_SHOWN, true).apply()
    }

    // ---- the saved days filled once for a switched-off section (B-101) ----

    override suspend fun partsBackfilled(): Boolean = prefs.getBoolean(KEY_PARTS_BACKFILLED, false)

    override suspend fun markPartsBackfilled() {
        prefs.edit().putBoolean(KEY_PARTS_BACKFILLED, true).apply()
    }

    // ---- saved days cut down where an account was not yet known (B-109) ----

    override suspend fun accountsPastRepaired(): Boolean = prefs.getBoolean(KEY_ACCOUNTS_PAST_REPAIRED, false)

    override suspend fun markAccountsPastRepaired() {
        prefs.edit().putBoolean(KEY_ACCOUNTS_PAST_REPAIRED, true).apply()
    }

    // ---- the parser the log was read with (B-45, B-46) ----

    override suspend fun repairedParserVersion(): Int = prefs.getInt(KEY_PARSER_VERSION, 0)

    override suspend fun setRepairedParserVersion(version: Int) {
        prefs.edit().putInt(KEY_PARSER_VERSION, version).apply()
    }

    // ---- banks ----

    private val _selected = MutableStateFlow(
        prefs.getStringSet(KEY_PACKAGES, emptySet())?.toSet() ?: emptySet()
    )

    override val selectedPackages: Flow<Set<String>> = _selected.asStateFlow()

    override suspend fun setSelectedPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_PACKAGES, packages).apply()
        _selected.value = packages
    }

    override suspend fun currentSelectedPackages(): Set<String> =
        prefs.getStringSet(KEY_PACKAGES, emptySet())?.toSet() ?: emptySet()

    // ---- sections (F-21) ----

    private val _features = MutableStateFlow(Features.decode(prefs.getStringSet(KEY_FEATURES, null)))
    override val features: Flow<Features> = _features.asStateFlow()

    override suspend fun setFeatures(features: Features) {
        prefs.edit().putStringSet(KEY_FEATURES, Features.encode(features)).apply()
        _features.value = features
    }

    override suspend fun currentFeatures(): Features = _features.value

    // An install from before F-21 already chose its banks or saved a copy: no first-launch setup.
    private val _onboarded = MutableStateFlow(
        prefs.getBoolean(KEY_ONBOARDED, prefs.contains(KEY_PACKAGES) || prefs.getLong(KEY_LAST_BACKUP, 0L) > 0L)
    )
    override val onboarded: Flow<Boolean> = _onboarded.asStateFlow()

    override suspend fun setOnboarded(done: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDED, done).apply()
        _onboarded.value = done
    }

    // ---- capital ----

    private fun readParts(): Set<CapitalPart> =
        prefs.getStringSet(KEY_CAPITAL_PARTS, null)
            ?.mapNotNull { CapitalPart.fromKey(it) }?.toSet()
            ?: CapitalPart.entries.toSet()

    private val _parts = MutableStateFlow(readParts())

    override val capitalParts: Flow<Set<CapitalPart>> = _parts.asStateFlow()

    override suspend fun setCapitalParts(parts: Set<CapitalPart>) {
        prefs.edit().putStringSet(KEY_CAPITAL_PARTS, parts.map { it.key }.toSet()).apply()
        _parts.value = parts
    }

    override suspend fun currentCapitalParts(): Set<CapitalPart> = _parts.value

    override suspend fun lastCapitalSection(): CapitalPart? =
        CapitalPart.fromKey(prefs.getString(KEY_CAPITAL_SECTION, null))

    override suspend fun setLastCapitalSection(part: CapitalPart) {
        prefs.edit().putString(KEY_CAPITAL_SECTION, part.key).apply()
    }

    // ---- deposits ----

    override suspend fun depositOrder(): DepositOrder = DepositOrder(
        DepositSort.fromKey(prefs.getString(KEY_DEPOSIT_SORT, null)) ?: DepositSort.DEFAULT,
        prefs.getBoolean(KEY_DEPOSIT_SORT_REVERSED, false)
    )

    override suspend fun setDepositOrder(order: DepositOrder) {
        prefs.edit().putString(KEY_DEPOSIT_SORT, order.sort.key).putBoolean(KEY_DEPOSIT_SORT_REVERSED, order.reversed).apply()
    }

    // ---- cashback ----

    override suspend fun cashbackOrder(): String? = prefs.getString(KEY_CASHBACK_ORDER, null)

    override suspend fun setCashbackOrder(key: String) {
        prefs.edit().putString(KEY_CASHBACK_ORDER, key).apply()
    }

    // ---- lock ----

    override suspend fun lockPin(): StoredPin? {
        val hash = prefs.getString(KEY_PIN_HASH, null) ?: return null
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return null
        return StoredPin(hash, salt, prefs.getInt(KEY_PIN_ITERATIONS, 0), prefs.getBoolean(KEY_PIN_KEYED, false))
    }

    override suspend fun lockAttempts(): LockAttempts =
        LockAttempts(prefs.getInt(KEY_PIN_FAILURES, 0), prefs.getLong(KEY_PIN_FAILED_AT, 0L), prefs.getLong(KEY_PIN_FAILED_TICK, -1L))

    override suspend fun setLockAttempts(attempts: LockAttempts) {
        prefs.edit().putInt(KEY_PIN_FAILURES, attempts.failures).putLong(KEY_PIN_FAILED_AT, attempts.lastFailAt)
            .putLong(KEY_PIN_FAILED_TICK, attempts.lastFailTick).apply()
    }

    override suspend fun setLockPin(pin: StoredPin?) {
        prefs.edit().apply {
            if (pin == null) remove(KEY_PIN_HASH).remove(KEY_PIN_SALT).remove(KEY_PIN_ITERATIONS).remove(KEY_PIN_KEYED)
            else putString(KEY_PIN_HASH, pin.hash).putString(KEY_PIN_SALT, pin.salt).putInt(KEY_PIN_ITERATIONS, pin.iterations)
                .putBoolean(KEY_PIN_KEYED, pin.keyed)
            remove(KEY_PIN_FAILURES).remove(KEY_PIN_FAILED_AT).remove(KEY_PIN_FAILED_TICK)
        }.apply()
        if (pin == null) setBiometricEnabled(false)
    }

    private val _biometric = MutableStateFlow(prefs.getBoolean(KEY_BIOMETRIC, false))
    override val biometricEnabled: Flow<Boolean> = _biometric.asStateFlow()

    override suspend fun setBiometricEnabled(on: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC, on).apply()
        _biometric.value = on
    }

    // ---- hidden amounts ----

    private val _hidden = MutableStateFlow(prefs.getBoolean(KEY_HIDDEN, false))
    override val amountsHidden: Flow<Boolean> = _hidden.asStateFlow()

    override suspend fun setAmountsHidden(hidden: Boolean) {
        prefs.edit().putBoolean(KEY_HIDDEN, hidden).apply()
        _hidden.value = hidden
    }

    private val _flip = MutableStateFlow(prefs.getBoolean(KEY_HIDE_FLIP, true))
    override val hideOnFlip: Flow<Boolean> = _flip.asStateFlow()

    override suspend fun setHideOnFlip(on: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_FLIP, on).apply()
        _flip.value = on
    }

    private val _swipe = MutableStateFlow(prefs.getBoolean(KEY_HIDE_SWIPE, true))
    override val hideBySwipe: Flow<Boolean> = _swipe.asStateFlow()

    override suspend fun setHideBySwipe(on: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_SWIPE, on).apply()
        _swipe.value = on
    }

    private val _tours = MutableStateFlow(prefs.getStringSet(KEY_TOURS_DONE, emptySet()).orEmpty().toSet())
    override val toursDone: Flow<Set<String>> = _tours.asStateFlow()

    override suspend fun markTourDone(key: String) {
        val next = _tours.value + key
        prefs.edit().putStringSet(KEY_TOURS_DONE, next).apply()
        _tours.value = next
    }

    private val _recents = MutableStateFlow(prefs.getBoolean(KEY_HIDE_RECENTS, false))
    override val hideInRecents: Flow<Boolean> = _recents.asStateFlow()

    override suspend fun setHideInRecents(on: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_RECENTS, on).apply()
        _recents.value = on
    }

    // ---- backup ----

    override suspend fun lastBackupAt(): Long = prefs.getLong(KEY_LAST_BACKUP, 0L)

    override suspend fun setLastBackupAt(at: Long) {
        prefs.edit().putLong(KEY_LAST_BACKUP, at).apply()
    }

    // ---- currency ----

    override suspend fun lastCurrency(): String? = prefs.getString(KEY_LAST_CURRENCY, null)

    override suspend fun setLastCurrency(code: String) {
        prefs.edit().putString(KEY_LAST_CURRENCY, code).apply()
    }

    override suspend fun ratesLastAttemptAt(): Long = prefs.getLong(KEY_RATES_ATTEMPT, 0L)

    override suspend fun ratesLastSuccessDay(): Long? =
        prefs.getLong(KEY_RATES_SUCCESS_DAY, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }

    override suspend fun setRatesAttempt(at: Long) {
        prefs.edit().putLong(KEY_RATES_ATTEMPT, at).apply()
    }

    override suspend fun setRatesSuccess(epochDay: Long) {
        prefs.edit().putLong(KEY_RATES_SUCCESS_DAY, epochDay).apply()
    }

    private companion object {
        const val KEY_PACKAGES = "packages"
        const val KEY_FEATURES = "features"
        const val KEY_ONBOARDED = "onboarded"
        const val LEGACY_FAVORITES = "favorite_groups"
        const val KEY_CAPITAL_PARTS = "capital_parts"
        const val KEY_CAPITAL_SECTION = "capital_section"
        const val KEY_LAST_CURRENCY = "last_currency"
        const val KEY_DEPOSIT_SORT = "deposit_sort"
        const val KEY_LAST_BACKUP = "last_backup_at"
        const val KEY_DEPOSIT_SORT_REVERSED = "deposit_sort_reversed"
        const val KEY_RATES_ATTEMPT = "rates_attempt_at"
        const val KEY_RATES_SUCCESS_DAY = "rates_success_day"
        const val KEY_CASHBACK_ORDER = "cashback_order"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_SALT = "pin_salt"
        const val KEY_PIN_ITERATIONS = "pin_iterations"
        const val KEY_PIN_KEYED = "pin_keyed"
        const val KEY_PIN_FAILURES = "pin_failures"
        const val KEY_PIN_FAILED_AT = "pin_failed_at"
        const val KEY_PIN_FAILED_TICK = "pin_failed_tick"
        const val KEY_BIOMETRIC = "biometric"
        const val KEY_HIDDEN = "amounts_hidden"
        const val KEY_HIDE_FLIP = "hide_on_flip"
        const val KEY_HIDE_SWIPE = "hide_by_swipe"
        const val KEY_HIDE_RECENTS = "hide_in_recents"
        const val KEY_TOURS_DONE = "tours_done"
        const val KEY_PARSER_VERSION = "parser_version"
        const val KEY_INTRO_SHOWN = "intro_shown"
        const val KEY_PARTS_BACKFILLED = "parts_backfilled"
        const val KEY_ACCOUNTS_PAST_REPAIRED = "accounts_past_repaired"
    }
}
