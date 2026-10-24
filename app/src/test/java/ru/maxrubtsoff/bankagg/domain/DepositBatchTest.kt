package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.DepositBatch
import ru.maxrubtsoff.bankagg.domain.model.DepositDraft
import ru.maxrubtsoff.bankagg.domain.model.DepositParser
import ru.maxrubtsoff.bankagg.domain.model.Money
import java.time.LocalDate

class DepositBatchTest {

    private val today = LocalDate.of(2026, 10, 21)
    private fun item(id: String, text: String) =
        DepositParser.parse(text.trimIndent(), today = today).let { DepositBatch.Item(id, it.brand, it.draft) }

    // The six real screenshots, two per deposit, shuffled.
    private val alfa1 = item("alfa1", """
        Альфа-Вклад Максимальный
        1 300 000 ₽
        До 2 февраля 2027
        Ваша ставка 13,51%
    """)
    private val alfa2 = item("alfa2", """
        Сумма при открытии вклада
        1 300 000 ₽
        Сумма в конце срока
        1 359 171,20 ₽
        Доход за весь срок
        59 171,20 ₽
        2 октября 2026 — 2 февраля 2027
    """)
    private val t1 = item("t1", """
        Вклад
        1 154 076,38 ₽ +154 076,38 ₽
        Ставка 16% годовых
        На 13 месяцев до 30 ноября
        https://www.tbank-online.ru
    """)
    private val t2 = item("t2", """
        Зачисленные проценты 154 076,38 ₽
        Пополнения 1 000 000 ₽
        Вы заработаете 185 142,31 ₽
    """)
    private val o1 = item("o1", """
        Эмитент: ООО "ОЗОН Капитал"
        Дата подачи заявки: 08.12.2025
        Общая сумма заявки: 500000,00
        Общая сумма погашения: 634880,00
        Планируемая дата погашения: 08.06.2027
    """)
    private val o2 = item("o2", """
        Баланс
        500 000 ₽
        18% годовых
        Доход в конце срока
        До вычета налога
        +134 880 ₽
        8 июня 2027 до 23:59 мск
    """)

    @Test fun six_screens_make_three_deposits() {
        val groups = DepositBatch.group(listOf(alfa1, t1, o1, alfa2, t2, o2))
        assertEquals(listOf(listOf("alfa1", "alfa2"), listOf("t1", "t2"), listOf("o1", "o2")), groups)
    }

    @Test fun merged_deposits_are_complete() {
        val alfa = DepositBatch.merge(listOf(alfa1, alfa2))
        assertEquals(BankBrand.ALFA, alfa.brand)
        assertEquals("Альфа-Вклад Максимальный", alfa.draft.title)
        assertEquals(LocalDate.of(2026, 10, 2), alfa.draft.startDate)
        assertTrue(alfa.isSavable)

        val t = DepositBatch.merge(listOf(t1, t2))
        assertEquals(BankBrand.TBANK, t.brand)
        assertEquals(118_514_231L, t.draft.exitAmount?.minor)
        assertEquals(LocalDate.of(2025, 10, 30), t.draft.startDate)
        assertTrue(t.isSavable)

        val o = DepositBatch.merge(listOf(o1, o2))
        assertEquals(BankBrand.OZON, o.brand)
        assertEquals(1800, o.draft.rateBp)
        assertEquals(LocalDate.of(2025, 12, 8), o.draft.startDate)
        assertTrue(o.isSavable)
    }

    @Test fun different_banks_never_merge() {
        val a = DepositBatch.Item("a", BankBrand.SBER, DepositDraft(principal = Money.ofMajor(100_000.0)))
        val b = DepositBatch.Item("b", BankBrand.TBANK, DepositDraft(principal = Money.ofMajor(100_000.0)))
        assertEquals(2, DepositBatch.group(listOf(a, b)).size)
    }

    @Test fun moved_shot_with_a_clash_offers_both_values() {
        val g = DepositBatch.merge(listOf(t2, o1))
        assertEquals(listOf(Money.ofMajor(1_000_000.0), Money.ofMajor(500_000.0)), g.options[DepositBatch.Part.PRINCIPAL])
        val picked = DepositBatch.merge(listOf(t2, o1), mapOf(DepositBatch.Part.PRINCIPAL to Money.ofMajor(500_000.0)))
        assertEquals(50_000_000L, picked.draft.principal?.minor)
    }

    @Test fun rate_is_implied_when_not_shown() {
        val d = DepositDraft(
            principal = Money.ofMajor(100_000.0), exitAmount = Money.ofMajor(110_000.0),
            startDate = LocalDate.of(2026, 1, 1), endDate = LocalDate.of(2027, 1, 1)
        )
        assertEquals(1000, DepositBatch.impliedRateBp(d))
    }
}
