package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.AccrualMethod
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.DepositField
import ru.maxrubtsoff.bankagg.domain.model.DepositParser
import java.time.LocalDate

/**
 * OCR-like text of real deposit screenshots (2026-10-21), personal details removed.
 * Each deposit came as two screenshots with different halves of the facts.
 */
class DepositScreensTest {

    private val today = LocalDate.of(2026, 10, 21)

    @Test fun alfa_main_screen() {
        val r = DepositParser.parse(
            """
            Альфа-Вклад Максимальный
            1 300 000 ₽
            До 2 февраля 2027
            Ваша ставка
            13,51%
            Прогноз дохода
            13 716,6 ₽
            Начислим на вклад
            31 октября
            История операций
            За последние 2 недели не было трат
            """.trimIndent(), today = today
        )
        assertEquals(BankBrand.ALFA, r.brand)
        assertEquals("Альфа-Вклад Максимальный", r.draft.title)
        assertEquals(130_000_000L, r.draft.principal?.minor)
        assertEquals(LocalDate.of(2027, 2, 2), r.draft.endDate)
        assertNull(r.draft.startDate)
        assertEquals(1351, r.draft.rateBp)
        assertNull(r.draft.exitAmount)
    }

    @Test fun alfa_forecast_screen() {
        val r = DepositParser.parse(
            """
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
            Начисляем деньги с 00:00 до 05:00 мск в последний день каждого месяца
            2026 год
            31 октября 13 716,60 ₽
            30 ноября 14 339,31 ₽
            """.trimIndent(), today = today
        )
        assertNull(r.brand)
        assertEquals(130_000_000L, r.draft.principal?.minor)
        assertEquals(135_917_120L, r.draft.exitAmount?.minor)
        assertEquals(5_917_120L, r.draft.income?.minor)
        assertEquals(LocalDate.of(2026, 10, 2), r.draft.startDate)
        assertEquals(LocalDate.of(2027, 2, 2), r.draft.endDate)
        assertEquals(AccrualMethod.CAPITALIZATION, r.draft.method)
        assertTrue(r.draft.guessed.isEmpty())
    }

    @Test fun tbank_main_screen() {
        val r = DepositParser.parse(
            """
            Вклад
            1 154 076,38 ₽ +154 076,38 ₽
            Ставка 16% годовых
            На 13 месяцев до 30 ноября
            Пополнить Вывести
            Операции по счету
            Нет пополнений в октябре
            Аналитика
            31 октября зачислим
            15 682,79 ₽
            Реквизиты
            Номер договора 3232118255
            Ссылка https://www.tbank-online.ru
            """.trimIndent(), today = today
        )
        assertEquals(BankBrand.TBANK, r.brand)
        assertNull(r.draft.title)
        assertEquals(100_000_000L, r.draft.principal?.minor)     // balance minus its growth
        assertEquals(1600, r.draft.rateBp)
        assertEquals(LocalDate.of(2026, 11, 30), r.draft.endDate)
        assertEquals(LocalDate.of(2025, 10, 30), r.draft.startDate)
        assertEquals(setOf(DepositField.START, DepositField.END), r.draft.guessed)
    }

    @Test fun tbank_events_screen() {
        val r = DepositParser.parse(
            """
            31 октября зачислим еще 15 682,79 ₽
            Май Июн Июл Авг Сен Окт Ноя
            События по вкладу
            Зачисленные проценты 154 076,38 ₽
            Пополнения 1 000 000 ₽
            Прогноз на дату закрытия
            30 ноября
            Вы заработаете 185 142,31 ₽
            Сумма на вкладе 1 185 142,31 ₽
            """.trimIndent(), today = today
        )
        assertEquals(100_000_000L, r.draft.principal?.minor)
        assertEquals(18_514_231L, r.draft.income?.minor)
        assertEquals(118_514_231L, r.draft.exitAmount?.minor)
    }

    @Test fun ozon_application_document() {
        val r = DepositParser.parse(
            """
            Заявка на участие в выпуске ЦФА
            ID заявки: 019afc75-aa76-7615-830f-c1cd88d28a55
            Дата подачи заявки: 08.12.2025
            Эмитент: ООО "ОЗОН Капитал"
            Решение о выпуске ЦФА: Решение о выпуске цифровых финансовых активов № 84 от 26.11.2025
            Цена одного ЦФА: 1000,00
            Валюта: RUB
            Количество ЦФА: 500 шт.
            Общая сумма заявки: 500000,00
            Общая сумма погашения: 634880,00
            Планируемая дата погашения: 08.06.2027
            Дата и время подписания: 08.12.2025 08:36:21
            """.trimIndent(), today = today
        )
        assertEquals(BankBrand.OZON, r.brand)
        assertEquals(50_000_000L, r.draft.principal?.minor)
        assertEquals(63_488_000L, r.draft.exitAmount?.minor)
        assertEquals(LocalDate.of(2025, 12, 8), r.draft.startDate)
        assertEquals(LocalDate.of(2027, 6, 8), r.draft.endDate)
    }

    @Test fun ozon_asset_screen() {
        val r = DepositParser.parse(
            """
            Цифровые активы
            Баланс
            500 000 ₽
            18% годовых
            Доход в конце срока
            До вычета налога
            +134 880 ₽
            8 июня 2027 до 23:59 мск
            О цифровом активе
            """.trimIndent(), today = today
        )
        assertEquals(50_000_000L, r.draft.principal?.minor)
        assertEquals(13_488_000L, r.draft.income?.minor)
        assertEquals(63_488_000L, r.draft.exitAmount?.minor)
        assertEquals(1800, r.draft.rateBp)
        assertEquals(LocalDate.of(2027, 6, 8), r.draft.endDate)
        assertNull(r.draft.startDate)
    }
}
