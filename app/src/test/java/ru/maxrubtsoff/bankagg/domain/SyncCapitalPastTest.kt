package ru.maxrubtsoff.bankagg.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CapitalPoint
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.model.DepositTopUp
import ru.maxrubtsoff.bankagg.domain.model.Features
import ru.maxrubtsoff.bankagg.domain.model.FxKind
import ru.maxrubtsoff.bankagg.domain.model.FxOperation
import ru.maxrubtsoff.bankagg.domain.model.FxRate
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.repository.CapitalHistoryRepository
import ru.maxrubtsoff.bankagg.domain.repository.DepositRepository
import ru.maxrubtsoff.bankagg.domain.repository.FxRepository
import ru.maxrubtsoff.bankagg.domain.repository.RatesRepository
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository
import ru.maxrubtsoff.bankagg.domain.repository.Today
import ru.maxrubtsoff.bankagg.domain.usecase.SyncCapitalPast
import java.lang.reflect.Proxy
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class SyncCapitalPastTest {

    private val day = LocalDate.of(2026, 7, 1)
    private val today = object : Today { override val date: StateFlow<LocalDate> = MutableStateFlow(day) }
    private val allParts = CapitalPart.entries.toSet()

    /** Reads and writes yield, so two writers without a common lock would interleave. */
    private class History(start: List<CapitalPoint>) : CapitalHistoryRepository {
        val data = start.associateBy { it.date }.toMutableMap()
        var writes = 0
        override fun observeDays(): Flow<List<CapitalPoint>> = flowOf(data.values.sortedBy { it.date })
        override suspend fun days(): List<CapitalPoint> {
            val snap = data.values.sortedBy { it.date }
            yield()
            return snap
        }
        override suspend fun upsert(point: CapitalPoint) { data[point.date] = point }
        override suspend fun upsertAll(points: List<CapitalPoint>) {
            yield()
            writes++
            points.forEach { data[it.date] = it }
        }
    }

    private class Deposits(val flow: MutableStateFlow<List<Deposit>>) : DepositRepository {
        override fun observeAll(): Flow<List<Deposit>> = flow
        override fun observe(id: String): Flow<Deposit?> = flowOf(null)
        override suspend fun get(id: String): Deposit? = null
        override suspend fun add(deposit: Deposit) = Unit
        override suspend fun update(deposit: Deposit) = Unit
        override suspend fun delete(id: String) = Unit
        override suspend fun addTopUp(depositId: String, topUp: DepositTopUp) = Unit
        override suspend fun deleteTopUp(id: String) = Unit
    }

    private class Fx(val flow: MutableStateFlow<List<FxOperation>>) : FxRepository {
        override fun observeAll(): Flow<List<FxOperation>> = flow
        override suspend fun all() = flow.value
        override suspend fun get(id: String): FxOperation? = null
        override suspend fun upsert(op: FxOperation) = Unit
        override suspend fun delete(id: String) = Unit
    }

    private val rates = object : RatesRepository {
        override fun observeAll(): Flow<List<FxRate>> = flowOf(emptyList())
        override suspend fun all() = listOf(FxRate("USD", LocalDate.of(2026, 1, 1), 90_000_000L))
        override suspend fun save(rates: List<FxRate>) = Unit
        override suspend fun latestOnOrBefore(currency: String, date: LocalDate): FxRate? = null
    }

    private val settings: SettingsRepository = Proxy.newProxyInstance(
        javaClass.classLoader, arrayOf(SettingsRepository::class.java)
    ) { _, m, _ ->
        when (m.name) {
            "getCapitalParts" -> flowOf(allParts)
            "getFeatures" -> flowOf(Features.ALL)
            else -> error("unused ${m.name}")
        }
    } as SettingsRepository

    private fun pt(d: LocalDate) = CapitalPoint(d, 600L, mapOf(
        CapitalPart.ACCOUNTS to 500L, CapitalPart.DEPOSITS to 0L, CapitalPart.CURRENCY to 70L, CapitalPart.CASH to 30L
    ))

    private fun daily() = generateSequence(day.minusDays(20)) { it.plusDays(1) }.takeWhile { it <= day }.map { pt(it) }.toList()

    private fun dep(start: LocalDate) =
        Deposit("d", BankBrand.SBER, null, Money.ofMinor(1_000_000L), 1600, start, day.plusDays(100))

    private fun buy(d: LocalDate) = FxOperation("f", "USD", FxKind.BUY, d, 100_000L, 9_000_000L, 0L)

    @Test fun deposit_start_day_appears_and_second_run_changes_nothing() = runTest {
        val history = History(daily().filter { it.date != day.minusDays(10) })
        val start = day.minusDays(10)
        val deposits = Deposits(MutableStateFlow(listOf(dep(start))))
        val fx = Fx(MutableStateFlow(emptyList()))

        val job = launch { SyncCapitalPast(history, deposits, fx, rates, settings, today).track() }
        advanceUntilIdle()
        job.cancel()
        assertEquals(1_000_000L, history.data[start]!!.parts[CapitalPart.DEPOSITS])
        val writes = history.writes

        val again = launch { SyncCapitalPast(history, deposits, fx, rates, settings, today).track() }
        advanceUntilIdle()
        again.cancel()
        assertEquals(writes, history.writes)
    }

    @Test fun parallel_deposit_and_deal_edits_do_not_lose_each_others_part() = runTest {
        val history = History(daily())
        val deposits = Deposits(MutableStateFlow(emptyList()))
        val fx = Fx(MutableStateFlow(emptyList()))
        val job = launch { SyncCapitalPast(history, deposits, fx, rates, settings, today).track() }
        advanceUntilIdle()

        deposits.flow.value = listOf(dep(day.minusDays(10)))
        fx.flow.value = listOf(buy(day.minusDays(5)))
        advanceUntilIdle()
        job.cancel()

        val last = history.data[day.minusDays(1)]!!
        assertTrue(last.parts[CapitalPart.DEPOSITS]!! >= 1_000_000L)
        assertEquals(70L + 9_000_000L, last.parts[CapitalPart.CURRENCY])
        assertNotNull(history.data[day.minusDays(10)])
    }
}
