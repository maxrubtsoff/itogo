package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.Account
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.RepairBase
import ru.maxrubtsoff.bankagg.domain.model.RepairEvent

class RepairBaseTest {
    private fun acc(checkedAt: Long?) = Account(
        id = "a", brand = BankBrand.UNKNOWN, title = "a", balance = Money.ofMinor(1_000L),
        currency = "RUB", updatedAt = 5L, checkedAt = checkedAt
    )

    @Test fun keep_when_checked_time_is_not_a_changed_event() {
        assertEquals(RepairBase.Keep(5L, 700L), RepairBase.pick(acc(5L), emptyList(), setOf(9L), 300L))
    }

    @Test fun from_journal_takes_latest_exact_balance() {
        val ev = listOf(RepairEvent(1L, 100L), RepairEvent(3L, 300L), RepairEvent(4L, null))
        assertEquals(RepairBase.FromJournal(3L, 300L), RepairBase.pick(acc(5L), ev, setOf(5L), 0L))
    }

    @Test fun none_when_poisoned_and_no_exact_balance() {
        assertEquals(RepairBase.None, RepairBase.pick(acc(5L), listOf(RepairEvent(1L, null)), setOf(5L), 0L))
    }
}
