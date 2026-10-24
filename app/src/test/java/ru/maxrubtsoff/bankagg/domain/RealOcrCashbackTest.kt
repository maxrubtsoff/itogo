package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CashbackMonth
import ru.maxrubtsoff.bankagg.domain.model.CashbackParser
import java.time.LocalDate

/** What Tesseract actually read from six cashback screenshots for October 2026. */
class RealOcrCashbackTest {

    private val today = LocalDate.of(2026, 10, 21)
    private fun ocr(name: String) = javaClass.getResource("/ocr/$name.txt")!!.readText()
    private fun rows(name: String) = CashbackParser.parse(ocr(name)).lines.map { it.text to it.percent }

    @Test fun banks() {
        assertEquals(BankBrand.OZON, CashbackParser.parse(ocr("cashback_ozon")).brand)
        assertEquals(BankBrand.SBER, CashbackParser.parse(ocr("cashback_sber")).brand)
        assertEquals(BankBrand.YANDEX, CashbackParser.parse(ocr("cashback_yandex")).brand)
        assertNull(CashbackParser.parse(ocr("cashback_tbank")).brand)
        assertNull(CashbackParser.parse(ocr("cashback_tiles")).brand)
    }

    @Test fun ozon_wrapped_name_is_joined() {
        assertEquals(
            listOf("Отели" to 5.0, "Рестораны" to 5.0, "Электроника и бытовая техника" to 5.0, "Ной" to 10.0, "Искусство" to 25.0),
            rows("cashback_ozon")
        )
    }

    @Test fun tiles_side_by_side_and_no_promo_discount() {
        assertEquals(
            listOf("За все покупки" to 1.0, "Транспорт" to 5.0, "Автоуслуги" to 5.0, "Активный отдых" to 5.0),
            rows("cashback_tiles")
        )
    }

    @Test fun sber_rows() {
        assertEquals(
            listOf("АЗС" to 4.0, "СпасибоТревел" to 5.0, "Парфюмерия и косметика" to 5.0, "Такси и каршеринг" to 5.0, "Транспорт" to 10.0),
            rows("cashback_sber")
        )
    }

    @Test fun yandex_taxi_tariffs_are_one_offer() {
        val names = rows("cashback_yandex").map { it.first }
        assertEquals(listOf("Яндекс Лавка", "Супермаркеты", "Кафе, бары и рестораны", "Одежда и обувь"), names.take(4))
        assertEquals(setOf("Яндекс Такси"), names.drop(4).toSet())
    }

    @Test fun tbank_and_light_rows() {
        assertEquals(listOf("Все покупки" to 1.0, "Аптеки" to 5.0, "Развлечения" to 5.0, "Такси" to 5.0), rows("cashback_tbank"))
        assertEquals(5, rows("cashback_light").size)
    }

    @Test fun months() {
        fun key(n: String) = CashbackMonth.guessKey(CashbackParser.monthsMentioned(ocr(n)), today)
        assertEquals("2026-10", key("cashback_ozon"))
        assertEquals("2026-10", key("cashback_light"))
        // Only the "В сентябре" tab was read: an earlier month than the page — the app keeps the page's month.
        assertEquals("2026-09", key("cashback_sber"))
    }
}
