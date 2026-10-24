package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankBrand
import ru.maxrubtsoff.bankagg.domain.model.CashbackParser

class CashbackParserTest {

    @Test fun parseLine_readsIntegerPercent() {
        val line = CashbackParser.parseLine("АЗС 7%")
        assertNotNull(line)
        assertEquals(7.0, line!!.percent, 0.0001)
        assertEquals("АЗС", line.text)
    }

    @Test fun parseLine_readsDecimalWithCommaAndSpace() {
        val line = CashbackParser.parseLine("Кафе и рестораны 5,5 %")
        assertNotNull(line)
        assertEquals(5.5, line!!.percent, 0.0001)
        assertEquals("Кафе и рестораны", line.text)
    }

    @Test fun parseLine_noPercentIsNull() {
        assertNull(CashbackParser.parseLine("Просто какой-то текст"))
    }

    @Test fun parseLine_rejectsOutOfRange() {
        assertNull(CashbackParser.parseLine("Всё 250%"))
    }

    @Test fun parse_multiLineKeepsEveryLineWithPercent() {
        val text = """
            Кэшбэк ноябрь
            АЗС 7%
            Кафе 5%
            Обувь 20%
            На всё 1%
        """.trimIndent()
        val res = CashbackParser.parse(text)
        assertEquals(listOf("АЗС", "Кафе", "Обувь", "На всё"), res.lines.map { it.text })
        assertEquals(listOf(7.0, 5.0, 20.0, 1.0), res.lines.map { it.percent })
    }

    @Test fun guessBank_findsTBankFromText() {
        assertEquals(BankBrand.TBANK, CashbackParser.guessBank("Т-Банк · кэшбэк ноябрь"))
    }

    @Test fun guessBank_findsAlfa() {
        assertEquals(BankBrand.ALFA, CashbackParser.guessBank("Альфа-Банк кэшбэк"))
    }

    @Test fun guessBank_unknownIsNull() {
        assertNull(CashbackParser.guessBank("Какой-то магазин без банка"))
    }

    @Test fun parse_partnerRowIsNotTheBank() {
        assertEquals(BankBrand.TBANK, CashbackParser.parse("Т-Банк\nOzon 5%\nЯндекс Маркет 3%").brand)
        assertNull(CashbackParser.parse("Ozon 5%").brand)
    }

    @Test fun parse_brandHintWins() {
        val res = CashbackParser.parse("АЗС 7%", brandHint = BankBrand.SBER)
        assertEquals(BankBrand.SBER, res.brand)
    }

    // OCR picks up the category icon on the left and the arrow/badge on the
    // right as stray characters; the category name must come out clean.
    @Test fun parseLine_stripsLeadingIconDotAndTrailingArrow() {
        assertEquals("Яндекс Лавка", CashbackParser.parseLine(". Яндекс Лавка / 3%")!!.text)
    }

    @Test fun parseLine_stripsLoneLetterIconAndTrailingBadge() {
        assertEquals("Комфорт", CashbackParser.parseLine("э Комфорт @ 5%")!!.text)
        assertEquals("Унта", CashbackParser.parseLine("э Унта @ 5%")!!.text)
    }

    @Test fun parseLine_keepsTrailingPlusAndInnerPunctuation() {
        assertEquals("Комфорт+", CashbackParser.parseLine("Комфорт+ 5%")!!.text)
        assertEquals("Кафе, бары и рестораны", CashbackParser.parseLine("Кафе, бары и рестораны 3%")!!.text)
    }

    @Test fun parse_realYandexPayScreenCategoriesAreClean() {
        val text = """
            Выгода в октябре
            . Яндекс Лавка / 3%
            Супермаркеты 3%
            Кафе, бары и рестораны 3%
            Одежда и обувь 5%
            э Комфорт @ 5%
            э Унта @ 5%
        """.trimIndent()
        val res = CashbackParser.parse(text, brandHint = BankBrand.YANDEX)
        assertEquals(
            listOf("Яндекс Лавка", "Супермаркеты", "Кафе, бары и рестораны", "Одежда и обувь", "Комфорт", "Унта"),
            res.lines.map { it.text }
        )
    }
}
