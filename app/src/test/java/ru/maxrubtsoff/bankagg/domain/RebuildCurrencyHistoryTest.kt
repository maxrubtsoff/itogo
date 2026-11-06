package ru.maxrubtsoff.bankagg.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleCapitalHistory
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleFxRepository
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleLedgerRepository
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleRatesRepository
import ru.maxrubtsoff.bankagg.data.repository.sample.SampleSettings
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CapitalPoint
import ru.maxrubtsoff.bankagg.domain.model.Features
import ru.maxrubtsoff.bankagg.domain.model.FxKind
import ru.maxrubtsoff.bankagg.domain.model.FxOperation
import ru.maxrubtsoff.bankagg.domain.model.FxRate
import ru.maxrubtsoff.bankagg.domain.repository.RatesSource
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository
import ru.maxrubtsoff.bankagg.domain.repository.Today
import ru.maxrubtsoff.bankagg.domain.usecase.RebuildCurrencyHistory
import java.time.LocalDate

class RebuildCurrencyHistoryTest {
    private val today = LocalDate.of(2026, 6, 10)
    private val old = LocalDate.of(2024, 8, 1)
    private val buy = FxOperation("1", "USD", FxKind.BUY, LocalDate.of(2026, 5, 30), 1_000_000L, 76_500_000L, 0L)

    private class Flagged : SettingsRepository by SampleSettings(Features.ALL) {
        var rebuilt = false
        override suspend fun currencyHistoryRebuilt() = rebuilt
        override suspend fun markCurrencyHistoryRebuilt() { rebuilt = true }
    }

    private fun setup(rates: List<FxRate>, fetched: List<FxRate> = emptyList()): Triple<RebuildCurrencyHistory, SampleCapitalHistory, Flagged> {
        val history = SampleCapitalHistory()
        runBlocking {
            history.upsertAll(listOf(CapitalPoint(old, 710_226_00L, mapOf(CapitalPart.CURRENCY to 710_226_00L))))
        }
        val settings = Flagged()
        val source = object : RatesSource { override suspend fun fetch(date: LocalDate) = fetched.ifEmpty { throw java.io.IOException("offline") } }
        val day = object : Today { override val date = MutableStateFlow(today) }
        val uc = RebuildCurrencyHistory(history, SampleLedgerRepository(emptyList(), emptyList()), SampleFxRepository(listOf(buy)),
            SampleRatesRepository(rates), source, settings, day)
        return Triple(uc, history, settings)
    }

    @Test fun noRatesLeavesDaysAndFlag() = runBlocking {
        val (uc, history, settings) = setup(emptyList())
        uc.ifNeeded()
        assertFalse(settings.rebuilt)
        assertEquals(710_226_00L, history.days().single().parts[CapitalPart.CURRENCY])
        assertEquals(RebuildCurrencyHistory.NO_RATES, uc())
    }

    @Test fun withRatesRecountsAndSetsFlag() = runBlocking {
        val (uc, history, settings) = setup(listOf(FxRate("USD", LocalDate.of(2026, 5, 29), 80_000_000L)))
        uc.ifNeeded()
        assertTrue(settings.rebuilt)
        assertEquals(0L, history.days().single().parts[CapitalPart.CURRENCY])
    }
}
