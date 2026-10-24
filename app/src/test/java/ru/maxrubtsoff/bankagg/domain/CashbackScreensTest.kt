package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CashbackBatch
import ru.maxrubtsoff.bankagg.domain.model.CashbackMonth
import ru.maxrubtsoff.bankagg.domain.model.CashbackParser
import ru.maxrubtsoff.bankagg.domain.model.ShotLine
import java.time.LocalDate

/** OCR-like text of six real cashback screenshots for October 2026. */
class CashbackScreensTest {

    private val today = LocalDate.of(2026, 10, 21)

    private val ozon = """
        Кешбэк
        0 ₽
        Копится до 4 октября
        Лимиты
        Категории на октябрь
        5 категорий с Ozon Premium
        Их можно поменять раз в месяц
        5% Отели
        5% Рестораны
        5% Электроника и бытовая техника
        10% Hoff
        25% Искусство
        Выгода от партнёров
    """.trimIndent()

    private val yandex = """
        Выгода в октябре
        Кешбэк по категориям
        Яндекс Лавка 3%
        Кешбэк при оплате онлайн и на кассе
        Супермаркеты 3%
        Кафе, бары и рестораны 3%
        Одежда и обувь 5%
        Кешбэк в Яндекс Такси
        Комфорт 5%
        Комфорт+ 5%
        Ultima 5%
    """.trimIndent()

    private val tiles = """
        Ваша выгода в октябре
        1%
        За все покупки
        5%
        Транспорт
        Зарплатным клиентам
        5%
        Автоуслуги
        5%
        Активный отдых
        Действует для карт
    """.trimIndent()

    private val sber = """
        Ваши категории
        В сентябре
        В октябре
        Будут действовать со СберПрайм
        4% АЗС
        5% СпасибоТревел
        До 5 000 бонусов
        5% Парфюмерия и косметика
        5% Такси и каршеринг
        10% Транспорт
    """.trimIndent()

    private val light = """
        Кэшбэк в октябре
        1 октября проверим ваш уровень.
        Ваши категории
        Кэшбэк начислим баллами 10 ноября
        3% Техника
        3% Маркетплейсы
    """.trimIndent()

    @Test fun banks_where_the_screen_names_them() {
        assertEquals(BankBrand.OZON, CashbackParser.parse(ozon).brand)
        assertEquals(BankBrand.YANDEX, CashbackParser.parse(yandex).brand)
        assertEquals(BankBrand.SBER, CashbackParser.parse(sber).brand)
        assertNull(CashbackParser.parse(tiles).brand)
        assertNull(CashbackParser.parse(light).brand)
    }

    @Test fun ozon_rows_and_a_limit_line_is_not_a_row() {
        assertEquals(
            listOf("Отели" to 5.0, "Рестораны" to 5.0, "Электроника и бытовая техника" to 5.0, "Hoff" to 10.0, "Искусство" to 25.0),
            CashbackParser.parse(ozon).lines.map { it.text to it.percent }
        )
        assertEquals(5, CashbackParser.parse(sber).lines.size)
    }

    @Test fun tiles_put_the_percent_above_the_name() {
        assertEquals(
            listOf("За все покупки" to 1.0, "Транспорт" to 5.0, "Автоуслуги" to 5.0, "Активный отдых" to 5.0),
            CashbackParser.parse(tiles).lines.map { it.text to it.percent }
        )
    }

    @Test fun taxi_tariffs_become_one_offer() {
        val names = CashbackParser.parse(yandex).lines.map { it.text }
        assertEquals(listOf("Яндекс Лавка", "Супермаркеты", "Кафе, бары и рестораны", "Одежда и обувь", "Яндекс Такси", "Яндекс Такси", "Яндекс Такси"), names)
    }

    @Test fun month_from_the_screen() {
        fun key(text: String) = CashbackMonth.guessKey(CashbackParser.monthsMentioned(text), today)
        assertEquals("2026-10", key(ozon))
        assertEquals("2026-10", key(sber))     // tabs "В сентябре · В октябре": the current one
        assertEquals("2026-10", key(light))    // "начислим 10 ноября" is not the month of the offers
        assertEquals("2027-01", CashbackMonth.guessKey(listOf(1), LocalDate.of(2026, 12, 28)))
        assertNull(CashbackMonth.guessKey(emptyList(), today))
    }

    @Test fun screens_of_one_bank_are_one_list() {
        fun l(t: String, p: Double) = ShotLine(t, "other", p, t)
        val items = listOf(
            CashbackBatch.Item("a", BankBrand.SBER, "2026-10", listOf(l("АЗС", 4.0), l("Такси", 5.0))),
            CashbackBatch.Item("b", null, "2026-10", listOf(l("Техника", 3.0))),
            CashbackBatch.Item("c", BankBrand.SBER, "2026-10", listOf(l("Такси", 5.0), l("Транспорт", 10.0))),
            CashbackBatch.Item("d", null, "2026-10", listOf(l("Аптеки", 5.0)))
        )
        val groups = CashbackBatch.group(items)
        assertEquals(listOf(listOf("a", "c"), listOf("b"), listOf("d")), groups)
        val sber = CashbackBatch.merge(items.filter { it.id in groups[0] })
        assertEquals(listOf("АЗС", "Такси", "Транспорт"), sber.lines.map { it.text })
    }
}
