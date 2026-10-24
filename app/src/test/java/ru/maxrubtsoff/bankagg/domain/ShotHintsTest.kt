package ru.maxrubtsoff.bankagg.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.DepositDraft
import ru.maxrubtsoff.bankagg.domain.model.Money
import ru.maxrubtsoff.bankagg.domain.model.ShotHints
import ru.maxrubtsoff.bankagg.domain.model.ShotLine

class ShotHintsTest {

    private fun line(text: String, percent: Double?) = ShotLine(text, "other", percent, text)

    @Test fun cashback_allRead_saysNothing() {
        assertNull(ShotHints.cashback(listOf(line("Аптеки", 5.0), line("Такси", 3.0))))
    }

    @Test fun cashback_onePercentMissing() {
        assertEquals("Процент одной категории не прочитался", ShotHints.cashback(listOf(line("Аптеки", null), line("Такси", 3.0))))
    }

    @Test fun cashback_severalPercentsMissing() {
        assertEquals("Проценты 2 категорий не прочитались", ShotHints.cashback(listOf(line("Аптеки", null), line("Такси", null))))
    }

    @Test fun cashback_unnamedRow() {
        assertEquals("Одна категория не прочиталась", ShotHints.cashback(listOf(line("", 5.0), line("Такси", 3.0))))
    }

    @Test fun deposit_listsWhatIsEmpty() {
        val d = DepositDraft(principal = Money.ofMinor(100_000), rateBp = 1300, startDate = LocalDate.of(2026, 1, 1))
        assertEquals("Не прочиталось: дата закрытия", ShotHints.deposit(d))
        assertEquals("Не прочиталось: сумма, ставка, дата открытия, дата закрытия", ShotHints.deposit(DepositDraft()))
        assertNull(ShotHints.deposit(d.copy(endDate = LocalDate.of(2027, 1, 1))))
    }

    @Test fun deposit_exitAmountStandsInForTheRate() {
        val d = DepositDraft(
            principal = Money.ofMinor(100_000), exitAmount = Money.ofMinor(110_000),
            startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2027, 1, 1)
        )
        assertNull(ShotHints.deposit(d))
    }
}
