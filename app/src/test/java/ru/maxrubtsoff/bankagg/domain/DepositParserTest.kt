package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.DepositParser
import org.junit.Test
import java.time.LocalDate

class DepositParserTest {

    @Test
    fun reads_full_deposit_screen() {
        val text = """
            Т-Банк
            Вклад «Надёжный»
            Сумма вклада 500 000 ₽
            Ставка 19% годовых
            Дата открытия 01.06.2026
            Дата окончания 01.12.2026
            В конце срока 547 500 ₽
        """.trimIndent()

        val r = DepositParser.parse(text)
        assertEquals(BankBrand.TBANK, r.brand)
        assertEquals(50_000_000L, r.draft.principal?.minor)   // 500 000 ₽
        assertEquals(54_750_000L, r.draft.exitAmount?.minor)  // 547 500 ₽
        assertEquals(1900, r.draft.rateBp)                    // 19,00%
        assertEquals(LocalDate.of(2026, 6, 1), r.draft.startDate)
        assertEquals(LocalDate.of(2026, 12, 1), r.draft.endDate)
    }

    @Test
    fun without_captions_smaller_is_deposit_larger_is_payout() {
        // Numbers alone are read only on a screen that is about a deposit (B-47).
        val r = DepositParser.parse("Вклад\n600 000\n660 000")
        assertEquals(60_000_000L, r.draft.principal?.minor)
        assertEquals(66_000_000L, r.draft.exitAmount?.minor)
    }

    @Test
    fun ignores_small_numbers_like_a_bare_rate() {
        // No grouped thousands and no 4+ digit amount → nothing to read as money.
        val r = DepositParser.parse("Вклад\nСтавка 7,5%")
        assertNull(r.draft.principal)
        assertEquals(750, r.draft.rateBp)
    }

    @Test
    fun reads_forecast_screen_with_split_captions_and_textual_dates() {
        // Реальный скрин «Прогноз дохода»: подписи на отдельных строках от чисел,
        // даты текстовые, ставки на экране нет.
        val text = """
            Прогноз дохода
            Начислим в конце месяца
            13 716,60 ₽
            Сумма при открытии вклада
            1 300 000 ₽
            Сумма в конце срока
            1 359 171,20 ₽
            Доход за весь срок
            59 171,20 ₽
            Срок вклада: 4 месяца
            2 октября 2026 — 2 февраля 2027
            График начислений
            2026 год
            31 октября 13 716,60 ₽
            30 ноября 14 339,31 ₽
        """.trimIndent()

        val r = DepositParser.parse(text)
        assertEquals(130_000_000L, r.draft.principal?.minor)   // 1 300 000 ₽, не год 2026
        assertEquals(135_917_120L, r.draft.exitAmount?.minor)  // «в конце срока», не «в конце месяца»
        assertEquals(LocalDate.of(2026, 10, 2), r.draft.startDate)
        assertEquals(LocalDate.of(2027, 2, 2), r.draft.endDate)
        assertNull(r.draft.rateBp)
    }

    @Test
    fun a_year_number_is_not_read_as_a_deposit_amount() {
        val r = DepositParser.parse("2026 год\nСумма при открытии вклада\n1 300 000 ₽")
        assertEquals(130_000_000L, r.draft.principal?.minor)
        assertNull(r.draft.exitAmount)
    }

    @Test
    fun a_screen_without_a_deposit_isNotRead() {
        // The capture log: a bank name and an amount from someone else's push (B-47).
        val text = """
            Журнал перехвата
            Ozon Банк · разобрано
            Покупка в FAST-FUD NUMI. 1073 RUR. Баланс 1983.15 ₽
            Альфа-Бизнес · не разобрано
        """.trimIndent()

        val r = DepositParser.parse(text)
        assertNull(r.brand)
        assertNull(r.draft.principal)
        assertTrue(r.draft.isEmpty)
    }

    @Test
    fun an_annual_rate_is_enough_to_read_a_deposit() {
        val r = DepositParser.parse("Баланс\n18% годовых\n1 300 000 \u20bd")
        assertEquals(1800, r.draft.rateBp)
    }
}
