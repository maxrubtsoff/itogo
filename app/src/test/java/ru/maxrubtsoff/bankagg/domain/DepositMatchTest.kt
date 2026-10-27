package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.Deposit
import ru.maxrubtsoff.bankagg.domain.model.DepositDraft
import ru.maxrubtsoff.bankagg.domain.model.DepositMatch
import ru.maxrubtsoff.bankagg.domain.model.Money
import java.time.LocalDate

class DepositMatchTest {

    private val start = LocalDate.of(2026, 10, 2)
    private val end = LocalDate.of(2027, 2, 2)

    private fun saved(id: String = "d1", brand: BankBrand = BankBrand.ALFA, principal: Double = 1_300_000.0, rateBp: Int = 1351) =
        Deposit(id = id, brand = brand, principal = Money.ofMajor(principal), rateBp = rateBp, startDate = start, endDate = end)

    private val draft = DepositDraft(
        principal = Money.ofMajor(1_300_000.0), rateBp = 1351, startDate = start, endDate = end
    )

    @Test fun exactMatchFound() {
        val d = saved()
        assertEquals(d, DepositMatch.find(BankBrand.ALFA, draft, listOf(d)))
    }

    @Test fun differentBankNoMatch() = assertNull(DepositMatch.find(BankBrand.TBANK, draft, listOf(saved())))

    @Test fun differentPrincipalNoMatch() =
        assertNull(DepositMatch.find(BankBrand.ALFA, draft, listOf(saved(principal = 1_300_001.0))))

    @Test fun differentRateNoMatch() = assertNull(DepositMatch.find(BankBrand.ALFA, draft, listOf(saved(rateBp = 1600))))

    @Test fun differentEndDateNoMatch() =
        assertNull(DepositMatch.find(BankBrand.ALFA, draft.copy(endDate = end.plusDays(1)), listOf(saved())))

    @Test fun differentStartDateNoMatch() =
        assertNull(DepositMatch.find(BankBrand.ALFA, draft.copy(startDate = start.plusDays(1)), listOf(saved())))

    @Test fun missingFieldNoMatch() {
        val list = listOf(saved())
        assertNull(DepositMatch.find(null, draft, list))
        assertNull(DepositMatch.find(BankBrand.ALFA, draft.copy(principal = null), list))
        assertNull(DepositMatch.find(BankBrand.ALFA, draft.copy(rateBp = null), list))
        assertNull(DepositMatch.find(BankBrand.ALFA, draft.copy(startDate = null), list))
        assertNull(DepositMatch.find(BankBrand.ALFA, draft.copy(endDate = null), list))
    }

    @Test fun picksTheEqualOneAmongMany() {
        val target = saved(id = "x")
        val list = listOf(saved(id = "a", brand = BankBrand.TBANK), saved(id = "b", principal = 5.0), target)
        assertEquals(target, DepositMatch.find(BankBrand.ALFA, draft, list))
    }

    @Test fun matchIgnoresExitAmountAndTitle() {
        val d = saved().copy(title = "Другое имя", exitAmount = Money.ofMajor(1.0))
        assertEquals(d, DepositMatch.find(BankBrand.ALFA, draft.copy(exitAmount = Money.ofMajor(2.0)), listOf(d)))
    }

    @Test fun emptyExistingNull() = assertNull(DepositMatch.find(BankBrand.ALFA, draft, emptyList()))
}
