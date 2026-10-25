package ru.maxrubtsoff.bankagg.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.FxRate
import ru.maxrubtsoff.bankagg.domain.repository.RatesRepository
import ru.maxrubtsoff.bankagg.domain.repository.RatesSource
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository
import ru.maxrubtsoff.bankagg.domain.usecase.RefreshRates
import java.time.LocalDate

class RefreshRatesTest {

    private class FakeSettings : SettingsRepository {
        override suspend fun repairedParserVersion() = 0
        override suspend fun setRepairedParserVersion(version: Int) = Unit
        var attempt = 0L
        var success: Long? = null
        override val selectedPackages: Flow<Set<String>> = emptyFlow()
        override suspend fun setSelectedPackages(packages: Set<String>) {}
        override suspend fun currentSelectedPackages(): Set<String> = emptySet()
        override val capitalParts: Flow<Set<CapitalPart>> = emptyFlow()
        override val features: Flow<ru.maxrubtsoff.bankagg.domain.model.Features> = emptyFlow()
        override suspend fun setFeatures(features: ru.maxrubtsoff.bankagg.domain.model.Features) = Unit
        override suspend fun currentFeatures() = ru.maxrubtsoff.bankagg.domain.model.Features.ALL
        override val onboarded: Flow<Boolean> = emptyFlow()
        override suspend fun setOnboarded(done: Boolean) = Unit
        override suspend fun setCapitalParts(parts: Set<CapitalPart>) {}
        override suspend fun currentCapitalParts(): Set<CapitalPart> = CapitalPart.entries.toSet()
        override suspend fun lastCapitalSection(): CapitalPart? = null
        override suspend fun setLastCapitalSection(part: CapitalPart) {}
        override suspend fun depositOrder() = ru.maxrubtsoff.bankagg.domain.model.DepositOrder(ru.maxrubtsoff.bankagg.domain.model.DepositSort.DEFAULT)
        override suspend fun setDepositOrder(order: ru.maxrubtsoff.bankagg.domain.model.DepositOrder) {}
        override suspend fun cashbackOrder(): String? = null
        override suspend fun setCashbackOrder(key: String) {}
        override suspend fun lockPin(): ru.maxrubtsoff.bankagg.domain.model.StoredPin? = null
        override suspend fun setLockPin(pin: ru.maxrubtsoff.bankagg.domain.model.StoredPin?) {}
        override suspend fun lockAttempts() = ru.maxrubtsoff.bankagg.domain.model.LockAttempts.NONE
        override suspend fun setLockAttempts(attempts: ru.maxrubtsoff.bankagg.domain.model.LockAttempts) {}
        override val biometricEnabled: Flow<Boolean> = emptyFlow()
        override suspend fun setBiometricEnabled(on: Boolean) {}
        override val amountsHidden: Flow<Boolean> = emptyFlow()
        override suspend fun setAmountsHidden(hidden: Boolean) {}
        override val hideOnFlip: Flow<Boolean> = emptyFlow()
        override suspend fun setHideOnFlip(on: Boolean) {}
        override val hideBySwipe: Flow<Boolean> = emptyFlow()
        override suspend fun setHideBySwipe(on: Boolean) {}
        override suspend fun lastBackupAt(): Long = 0L
        override suspend fun setLastBackupAt(at: Long) {}
        override suspend fun lastCurrency(): String? = null
        override suspend fun setLastCurrency(code: String) {}
        override suspend fun ratesLastAttemptAt(): Long = attempt
        override suspend fun ratesLastSuccessDay(): Long? = success
        override suspend fun setRatesAttempt(at: Long) { attempt = at }
        override suspend fun setRatesSuccess(epochDay: Long) { success = epochDay }
    }

    private class FakeRates : RatesRepository {
        val saved = mutableListOf<FxRate>()
        override fun observeAll(): Flow<List<FxRate>> = emptyFlow()
        override suspend fun all(): List<FxRate> = saved
        override suspend fun save(rates: List<FxRate>) { saved += rates }
        override suspend fun latestOnOrBefore(currency: String, date: LocalDate): FxRate? =
            saved.filter { it.currency == currency && !it.date.isAfter(date) }.maxByOrNull { it.date }
    }

    private val day = LocalDate.of(2026, 10, 21)

    @Test fun fetches_once_a_day_and_retries_after_an_hour() = runTest {
        var calls = 0
        var online = false
        val source = object : RatesSource {
            override suspend fun fetch(date: LocalDate): List<FxRate> {
                calls++
                if (!online) error("нет сети")
                return listOf(FxRate("USD", date, 81_000_000))
            }
        }
        var now = 1_700_000_000_000L
        val settings = FakeSettings()
        val refresh = RefreshRates(source, FakeRates(), settings) { now }

        assertTrue(refresh.ifNeeded(day) is RefreshRates.Result.Failed)
        now += 30 * 60_000L
        assertEquals(RefreshRates.Result.Skipped, refresh.ifNeeded(day))      // < 1 h since the attempt
        online = true
        now += 31 * 60_000L
        assertTrue(refresh.ifNeeded(day) is RefreshRates.Result.Updated)
        assertEquals(RefreshRates.Result.Skipped, refresh.ifNeeded(day))      // today already fetched
        assertTrue(refresh.now(day) is RefreshRates.Result.Updated)           // ↻ always fetches
        assertEquals(3, calls)
    }

    @Test fun rate_for_purchase_date_uses_cache_first() = runTest {
        var calls = 0
        val source = object : RatesSource {
            override suspend fun fetch(date: LocalDate): List<FxRate> { calls++; return listOf(FxRate("USD", date, 76_500_000)) }
        }
        val rates = FakeRates()
        val refresh = RefreshRates(source, rates, FakeSettings())
        assertEquals(76_500_000L, refresh.rateOn("USD", day)!!.rubPerUnitMicro)
        assertEquals(76_500_000L, refresh.rateOn("USD", day)!!.rubPerUnitMicro)
        assertEquals(1, calls)
    }
}
