package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BalanceState
import ru.maxrubtsoff.bankagg.domain.model.RunningBalance

/** B-29: a bank without a balance in its pushes gets a counted one. */
class RunningBalanceTest {

    @Test fun noBalanceYet_waitsForTheUser() {
        val s = RunningBalance.apply(null, -63_189, null, 100)
        assertEquals(BalanceState(0, 100, null, false), s)
        assertEquals(BalanceState(0, 200, null, false), RunningBalance.apply(s, -10_000, null, 200))
    }

    @Test fun userStartingPoint_thenSpendingAndRefunds() {
        var s = RunningBalance.setByUser(BalanceState(0, 100, null, false), 9_200_000, 1_000)
        s = RunningBalance.apply(s, -187_125, null, 2_000)
        s = RunningBalance.apply(s, 53_742, null, 3_000)   // возврат
        assertEquals(BalanceState(9_200_000 - 187_125 + 53_742, 3_000, 1_000, true), s)
    }

    @Test fun bankBalanceIsTheNewStartingPoint() {
        var s = RunningBalance.apply(null, -50_000, 100_000, 1_000)
        s = RunningBalance.apply(s, -10_000, null, 2_000)
        assertEquals(true, s.estimated)
        s = RunningBalance.apply(s, 500_000, 590_000, 3_000)
        assertEquals(BalanceState(590_000, 3_000, 3_000, false), s)
    }

    @Test fun operationOlderThanTheExactBalance_isAlreadyInIt() {
        val s = RunningBalance.setByUser(null, 1_000_000, 5_000)
        assertEquals(1_000_000, RunningBalance.apply(s, -20_000, null, 4_000).balanceMinor)
    }

    @Test fun lateBankBalance_doesNotOverrideANewerOne() {
        val s = RunningBalance.setByUser(null, 1_000_000, 5_000)
        assertEquals(1_000_000, RunningBalance.apply(s, -20_000, 300_000, 4_000).balanceMinor)
    }
}
