package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.OpTime
import ru.maxrubtsoff.bankagg.domain.parser.OpTimeParser

class OpTimeParserTest {
    @Test fun alfaDayMonthWordTime() {
        assertEquals(OpTime(3, 11, null, 17, 39),
            OpTimeParser.find("Покупка 1 045 RUB Баланс 1 764,12 RUB Додо Пицца 3 ноября 17:39"))
    }
    @Test fun numericDateTime() {
        assertEquals(OpTime(3, 11, 2026, 17, 39), OpTimeParser.find("Оплата 5 ₽ 03.11.2026 в 17:39"))
        assertEquals(OpTime(3, 11, null, 17, 39), OpTimeParser.find("Оплата 5 ₽ 03.11 17:39"))
        assertEquals(OpTime(3, 11, 2026, 17, 39), OpTimeParser.find("Оплата 5 ₽ 03.11.26 17:39"))
    }
    @Test fun timeOnly() {
        assertEquals(OpTime(null, null, null, 9, 5), OpTimeParser.find("Покупка 5 ₽ в 9:05"))
    }
    @Test fun paymentOrderDateIsNotOpTime() {
        assertNull(OpTimeParser.find("Списано 440.00 счет *4863 п/п №92712 от 27.10.26 в адрес АО \"АЛЬФА-БАНК\" счет 0741. Остаток 3079.36"))
    }
    @Test fun twoDifferentTimesIsNull() {
        assertNull(OpTimeParser.find("Покупка 5 ₽ 17:39, баланс на 17:42"))
    }
    @Test fun amountWithColonNotTime() {
        assertNull(OpTimeParser.find("Покупка 1 045 RUB Баланс 281 764,12 RUB"))
    }
}
