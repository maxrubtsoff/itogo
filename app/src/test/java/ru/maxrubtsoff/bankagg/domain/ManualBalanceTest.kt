package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.ManualBalance
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.RevaluationLeg
import ru.maxrubtsoff.bankagg.domain.model.Transaction
import ru.maxrubtsoff.bankagg.domain.model.TransactionKind
import java.time.LocalDate
import java.time.ZoneOffset

class ManualBalanceTest {
    private val zone = ZoneOffset.UTC
    private val day = LocalDate.of(2026, 5, 10)
    private val dayMs = day.atStartOfDay(zone).toInstant().toEpochMilli() + 3_600_000L

    private fun acc(id: String, cur: String, minor: Long, checked: Long? = null, excluded: Boolean = false, brand: BankBrand = BankBrand.SBER) =
        Account(id, brand, id, Money.ofMinor(minor), cur, 0L, excluded = excluded, checkedAt = checked)

    private fun leg(cur: String) = RevaluationLeg(cur, 90_000_000L, 91_000_000L, 100L, 10L)

    @Test fun single_leg_gives_its_currency() {
        assertEquals("EUR", ManualBalance.currencyOf(listOf(leg("EUR")), listOf(acc("a", "USD", 1))))
    }

    @Test fun many_legs_one_foreign_account_gives_account_currency() {
        assertEquals("USD", ManualBalance.currencyOf(listOf(leg("EUR"), leg("CNY")), listOf(acc("a", "USD", 1), acc("r", "RUB", 1))))
    }

    @Test fun ambiguous_gives_null() {
        assertNull(ManualBalance.currencyOf(emptyList(), listOf(acc("a", "USD", 1), acc("b", "EUR", 1))))
        assertNull(ManualBalance.currencyOf(emptyList(), listOf(acc("r", "RUB", 1))))
    }

    @Test fun accounts_of_skip_excluded_and_other_currencies() {
        val list = listOf(acc("a", "USD", 1), acc("b", "USD", 2, excluded = true), acc("c", "EUR", 3))
        assertEquals(listOf("a"), ManualBalance.accountsOf("USD", list).map { it.id })
    }

    private fun tx(account: String, at: Long) = Transaction("t", BankBrand.SBER, account, Money.ofMinor(-1), "USD", TransactionKind.EXPENSE, null, null, at)

    @Test fun single_touched_account_explained_is_balance_minus_manual() {
        // 1 USD = 100 RUB; the balance rose by 8000 $ = 800 000 RUB by hand.
        val rows = ManualBalance.rows(listOf(acc("a", "USD", 812_000, checked = dayMs)), 80_000_000L, 100_000_000L, day, zone)
        assertEquals(12_000L, rows.single().explainedMinor)
        assertEquals(true, rows.single().changed)
    }

    @Test fun touched_account_first_and_other_matches() {
        val list = listOf(acc("s", "USD", 100_500), acc("t", "USD", 812_000, checked = dayMs))
        val rows = ManualBalance.rows(list, 80_000_000L, 100_000_000L, day, zone)
        assertEquals(listOf("t", "s"), rows.map { it.account.id })
        assertEquals(12_000L, rows[0].explainedMinor)
        assertEquals(100_500L, rows[1].explainedMinor)
    }

    @Test fun operations_after_the_day_hide_explained() {
        val list = listOf(acc("a", "USD", 812_000, checked = dayMs))
        val later = dayMs + 2 * 86_400_000L
        assertNull(ManualBalance.rows(list, 80_000_000L, 100_000_000L, day, zone, listOf(tx("a", later))).single().explainedMinor)
    }

    @Test fun no_rate_hides_explained() {
        val list = listOf(acc("a", "USD", 812_000, checked = dayMs))
        assertNull(ManualBalance.rows(list, 80_000_000L, null, day, zone).single().explainedMinor)
    }

    @Test fun no_touched_account_hides_explained() {
        assertNull(ManualBalance.rows(listOf(acc("a", "USD", 812_000)), 80_000_000L, 100_000_000L, day, zone).single().explainedMinor)
    }

    @Test fun merged_line_hides_explained() {
        val list = listOf(acc("a", "USD", 812_000, checked = dayMs))
        assertNull(ManualBalance.rows(list, 80_000_000L, 100_000_000L, day, zone, merged = true).single().explainedMinor)
    }

    @Test fun several_touched_accounts_have_no_explained() {
        val list = listOf(acc("s", "USD", 100, checked = dayMs), acc("t", "USD", 200, checked = dayMs))
        assertNull(ManualBalance.rows(list, 80_000_000L, 100_000_000L, day, zone).first().explainedMinor)
    }

    @Test fun bank_of_the_only_touched_account() {
        val list = listOf(acc("s", "USD", 1, brand = BankBrand.SBER), acc("t", "USD", 2, checked = dayMs, brand = BankBrand.TBANK))
        assertEquals(BankBrand.TBANK, ManualBalance.onlyChanged(list, day, zone)?.brand)
        assertNull(ManualBalance.onlyChanged(list + acc("u", "USD", 3, checked = dayMs), day, zone))
    }
}
