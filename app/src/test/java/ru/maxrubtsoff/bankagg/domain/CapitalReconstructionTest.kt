package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CapitalEvents
import ru.maxrubtsoff.bankagg.domain.model.CapitalPart
import ru.maxrubtsoff.bankagg.domain.model.CapitalPast
import ru.maxrubtsoff.bankagg.domain.model.CapitalPoint
import ru.maxrubtsoff.bankagg.domain.model.CapitalReconstruction
import ru.maxrubtsoff.bankagg.domain.model.Currencies
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.model.HistoryKind
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.Transaction
import ru.maxrubtsoff.bankagg.domain.model.TransactionKind
import java.time.LocalDate
import java.time.ZoneId

/** The rebuilt past shows only what was known that day (B-109). */
class CapitalReconstructionTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 27)
    private val all = CapitalPart.entries.toSet()

    private fun ms(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000L

    private fun acc(id: String, minor: Long, updated: LocalDate? = null, checked: LocalDate? = null) =
        Account(id, BankBrand.SBER, "Карта $id", Money.ofMinor(minor), Currencies.RUB,
            updated?.let { ms(it) } ?: 0L, checkedAt = checked?.let { ms(it) })

    private fun tx(id: String, account: String, minor: Long, d: LocalDate) =
        Transaction(id, BankBrand.SBER, account, Money.ofMinor(minor), Currencies.RUB,
            if (minor > 0) TransactionKind.INCOME else TransactionKind.EXPENSE, null, null, ms(d))

    private val deposit = Deposit("d", BankBrand.SBER, null, Money.ofMinor(65_000_000L), 1600,
        LocalDate.of(2024, 8, 7), LocalDate.of(2027, 8, 7))

    private fun build(accounts: List<Account>, txs: List<Transaction>, deps: List<Deposit> = listOf(deposit)) =
        CapitalReconstruction.build(accounts, txs, deps, emptyList(), emptyList(), null, all, today, zone)

    @Test fun b109_pointOfDecember2024_isDepositOnly() {
        val a = acc("a", 208_295_829L)
        val points = build(listOf(a), listOf(tx("1", "a", 100_00L, LocalDate.of(2026, 10, 1))))
        val day = LocalDate.of(2024, 12, 30)
        val p = points.first { it.date == day }
        assertEquals(0L, p.valueOf(CapitalPart.ACCOUNTS))
        assertEquals(CapitalPast.depositsOn(listOf(deposit), day), p.totalMinor)
    }

    @Test fun build_accountFromKnownDay_isOpeningPlusOps() {
        val a = acc("a", 1_000L)
        val d1 = LocalDate.of(2026, 10, 1)
        val txs = listOf(tx("1", "a", 300L, d1), tx("2", "a", -100L, d1.plusDays(2)))
        val points = build(listOf(a), txs, emptyList())
        assertEquals(1_100L, points.first { it.date == d1 }.valueOf(CapitalPart.ACCOUNTS))
        assertEquals(0L, points.first { it.date == d1.minusDays(1) }.valueOf(CapitalPart.ACCOUNTS))
        assertEquals(1_000L, points.last().valueOf(CapitalPart.ACCOUNTS))
    }

    @Test fun accountsPart_equalsSumOfAccountEvents() {
        val a = acc("a", 5_000L)
        val b = acc("b", 2_000L, updated = LocalDate.of(2026, 9, 1))
        val txs = listOf(
            tx("1", "a", 700L, LocalDate.of(2026, 8, 1)), tx("2", "a", -200L, LocalDate.of(2026, 8, 5)),
            tx("3", "b", 100L, LocalDate.of(2026, 9, 10)), tx("4", "b", -50L, LocalDate.of(2026, 10, 2))
        )
        val points = build(listOf(a, b), txs, emptyList())
        val events = CapitalEvents.build(txs, listOf(a, b), emptyList(), emptyList(), null, points, today, zone)
            .filter { it.part == CapitalPart.ACCOUNTS }
        points.forEach { p ->
            val sum = events.filter { !it.date.isAfter(p.date) }.sumOf { it.amountMinor ?: 0L }
            assertEquals("day ${p.date}", sum, p.valueOf(CapitalPart.ACCOUNTS))
        }
    }

    @Test fun knownFrom_isEarliestOfFirstTxUpdatedChecked() {
        val a = acc("a", 100L, updated = LocalDate.of(2026, 5, 1), checked = LocalDate.of(2026, 4, 1))
        val txs = listOf(tx("1", "a", 10L, LocalDate.of(2026, 6, 1)))
        assertEquals(LocalDate.of(2026, 4, 1), CapitalReconstruction.knownFrom(a, txs, zone))
        assertEquals(90L, CapitalReconstruction.openingOf(a, txs))
    }

    @Test fun capUnknown_lowersOverstatedDays_isIdempotent_neverRaises() {
        val a = acc("a", 1_000L)
        val known = LocalDate.of(2026, 10, 1)
        val txs = listOf(tx("1", "a", 100L, known))
        val rebuilt = build(listOf(a), txs)
        val before = known.minusDays(5)
        val high = CapitalPoint(before, 5_000L, mapOf(CapitalPart.ACCOUNTS to 5_000L))
        val low = CapitalPoint(before.minusDays(1), 0L, mapOf(CapitalPart.ACCOUNTS to 0L))
        val after = CapitalPoint(known.plusDays(3), 9_000L, mapOf(CapitalPart.ACCOUNTS to 9_000L))
        val fixed = CapitalReconstruction.capUnknown(listOf(high, low, after), rebuilt, listOf(a), txs, all, today, zone)
        assertEquals(listOf(before), fixed.map { it.date })
        assertEquals(0L, fixed.single().valueOf(CapitalPart.ACCOUNTS))
        assertEquals(0L, fixed.single().totalMinor)
        assertTrue(CapitalReconstruction.capUnknown(fixed + low + after, rebuilt, listOf(a), txs, all, today, zone).isEmpty())
    }

    @Test fun accountOpen_event_onKnownDay() {
        val a = acc("a", 1_000L)
        val d = LocalDate.of(2026, 10, 1)
        val txs = listOf(tx("1", "a", 300L, d))
        val points = build(listOf(a), txs, emptyList())
        val ev = CapitalEvents.build(txs, listOf(a), emptyList(), emptyList(), null, points, today, zone)
            .single { it.kind == HistoryKind.ACCOUNT_OPEN }
        assertEquals(700L, ev.amountMinor)
        assertEquals(d, ev.date)
        val none = CapitalEvents.build(txs, listOf(a.copy(excluded = true)), emptyList(), emptyList(), null, points, today, zone)
        assertTrue(none.none { it.kind == HistoryKind.ACCOUNT_OPEN })
    }

    @Test fun capUnknown_accountWithoutOps_updatedYesterday_leavesSavedDaysAlone() {
        val a = acc("a", 1_000L, updated = today.minusDays(1))
        val rebuilt = build(CapitalReconstruction.withoutBareStarts(listOf(a), emptyList()), emptyList())
        val day = LocalDate.of(2026, 9, 1)
        val saved = CapitalPoint(day, 1_000L, mapOf(CapitalPart.ACCOUNTS to 1_000L))
        assertTrue(CapitalReconstruction.capUnknown(listOf(saved), rebuilt, listOf(a), emptyList(), all, today, zone).isEmpty())
    }

    @Test fun capUnknown_neverRaises() {
        val a = acc("a", 1_000L)
        val txs = listOf(tx("1", "a", 100L, LocalDate.of(2026, 10, 1)))
        val rebuilt = build(listOf(a), txs)
        val day = LocalDate.of(2026, 9, 20)
        val saved = CapitalPoint(day, 0L, mapOf(CapitalPart.ACCOUNTS to 0L))
        assertTrue(CapitalReconstruction.capUnknown(listOf(saved), rebuilt, listOf(a), txs, all, today, zone).isEmpty())
    }
}
