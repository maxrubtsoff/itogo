package ru.maxrubtsoff.bankagg.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CapitalHistory
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CapitalPoint
import ru.maxrubtsoff.bankagg.domain.model.CapitalReconstruction
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.model.Features
import ru.maxrubtsoff.bankagg.domain.model.HistoryPeriod
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.repository.CapitalHistoryRepository
import ru.maxrubtsoff.bankagg.domain.repository.SettingsRepository
import ru.maxrubtsoff.bankagg.domain.usecase.CapitalDaysScope
import java.lang.reflect.Proxy
import java.time.LocalDate

/** B-101: the total of a day follows the parts counted now; days saved empty are filled once. */
class CapitalPartsSwitchTest {

    private val today = LocalDate.of(2026, 10, 25)
    private val all = CapitalPart.entries.toSet()

    private fun pt(day: LocalDate, accounts: Long, cash: Long, total: Long, deposits: Long = 0L) = CapitalPoint(
        day, total,
        mapOf(CapitalPart.ACCOUNTS to accounts, CapitalPart.DEPOSITS to deposits, CapitalPart.CURRENCY to 0L, CapitalPart.CASH to cash)
    )

    // Saved at different compositions: the stored totals disagree, the parts do not.
    private val days = listOf(
        pt(today.minusDays(2), 500L, 100L, total = 600L),
        pt(today.minusDays(1), 500L, 100L, total = 500L),
        pt(today, 500L, 100L, total = 600L)
    )

    @Test fun totalled_sums_only_included_parts() {
        val out = CapitalHistory.totalled(days, setOf(CapitalPart.ACCOUNTS))
        assertEquals(listOf(500L, 500L, 500L), out.map { it.totalMinor })
    }

    @Test fun totalled_keeps_parts_untouched() {
        val out = CapitalHistory.totalled(days, setOf(CapitalPart.CASH))
        assertEquals(days.map { it.parts }, out.map { it.parts })
    }

    @Test fun totalled_makes_series_flat_when_part_switched_off() {
        val raw = CapitalHistory.series(days, HistoryPeriod.WEEK, today).map { it.minor }
        assertTrue(raw.distinct().size > 1)
        val flat = CapitalHistory.series(CapitalHistory.totalled(days, all), HistoryPeriod.WEEK, today).map { it.minor }
        assertEquals(1, flat.distinct().size)
    }

    private class History(val days: List<CapitalPoint>) : CapitalHistoryRepository {
        override fun observeDays(): Flow<List<CapitalPoint>> = flowOf(days)
        override suspend fun days() = days
        override suspend fun upsert(point: CapitalPoint) = Unit
        override suspend fun upsertAll(points: List<CapitalPoint>) = Unit
    }

    @Test fun scope_totals_follow_the_switch_and_the_features() = runTest {
        val parts = MutableStateFlow(all)
        val features = MutableStateFlow(Features.ALL)
        val settings = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SettingsRepository::class.java)) { _, m, _ ->
            when (m.name) {
                "getCapitalParts" -> parts
                "getFeatures" -> features
                else -> error("unused ${m.name}")
            }
        } as SettingsRepository
        val scope = CapitalDaysScope(History(days), settings)
        assertEquals(listOf(600L, 600L, 600L), scope.observeDays().first().map { it.totalMinor })
        parts.value = setOf(CapitalPart.ACCOUNTS)
        assertEquals(listOf(500L, 500L, 500L), scope.observeDays().first().map { it.totalMinor })
    }

    private fun dep() = Deposit("d", BankBrand.SBER, null, Money.ofMinor(1_000_000L), 1600, today.minusDays(10), today.plusDays(100))

    private fun rebuilt() = CapitalReconstruction.build(
        emptyList(), emptyList(), listOf(dep()), emptyList(), emptyList(), null, all, today
    )

    @Test fun day_with_part_off_stops_being_zero_after_backfill() {
        val saved = (1..5).map { pt(today.minusDays(it.toLong()), 0L, 0L, total = 0L) }
        val changed = CapitalReconstruction.fillEmptyParts(saved, rebuilt(), all, today, null)
        assertTrue(changed.isNotEmpty())
        assertTrue(changed.all { it.parts[CapitalPart.DEPOSITS]!! > 0L && it.totalMinor > 0L })
    }

    @Test fun backfill_is_idempotent_and_keeps_saved_values() {
        val saved = listOf(
            pt(today.minusDays(3), 700L, 0L, total = 700L, deposits = 0L),
            pt(today.minusDays(2), 700L, 0L, total = 700L, deposits = 1L),
            pt(today, 700L, 0L, total = 700L)
        )
        val first = CapitalReconstruction.fillEmptyParts(saved, rebuilt(), all, today, null)
        val applied = saved.map { p -> first.firstOrNull { it.date == p.date } ?: p }
        assertEquals(700L, applied[0].parts[CapitalPart.ACCOUNTS])
        assertEquals(1L, applied[1].parts[CapitalPart.DEPOSITS])
        assertEquals(saved[2], applied[2])   // today is not touched
        assertTrue(CapitalReconstruction.fillEmptyParts(applied, rebuilt(), all, today, null).isEmpty())
    }
}
