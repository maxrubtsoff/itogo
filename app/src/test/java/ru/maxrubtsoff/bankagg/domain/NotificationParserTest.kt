package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.TransactionKind
import ru.maxrubtsoff.bankagg.domain.parser.NotificationParser

class NotificationParserTest {

    @Test fun income_withBalanceAndCounterparty() {
        val op = NotificationParser.parse(null, "Пополнение на 5 000 ₽, счет RUB. Максим Р. Доступно 107 409,81 ₽")!!
        assertEquals(TransactionKind.INCOME, op.kind)
        assertEquals(500_000L, op.amount.minor)
        assertEquals("RUB", op.currency)
        assertEquals(10_740_981L, op.balance!!.minor)
        assertEquals("Максим Р.", op.counterparty)
    }

    @Test fun expense_isNegative() {
        val op = NotificationParser.parse("Т-Банк", "Оплата 450 ₽ Магнит. Доступно 106 959,81 ₽")!!
        assertEquals(TransactionKind.EXPENSE, op.kind)
        assertTrue(op.amount.isNegative)
        assertEquals(-45_000L, op.amount.minor)
        assertEquals(10_695_981L, op.balance!!.minor)
    }

    @Test fun transfer_isDetected() {
        // A transfer addressed to someone is money going out (B-45): the kind is
        // still a transfer, the sign comes from the direction.
        val op = NotificationParser.parse(null, "Перевод 3 000 ₽ Ивану Петрову. Доступно 10 000 ₽")!!
        assertEquals(TransactionKind.TRANSFER, op.kind)
        assertEquals(-300_000L, op.amount.minor)
    }

    @Test fun blankOrNoAmount_returnsNull() {
        assertNull(NotificationParser.parse(null, ""))
        assertNull(NotificationParser.parse(null, "Вам пришло уведомление без суммы"))
    }

    @Test fun counterparty_notTakenFromBankTitle() {
        // "Т-Банк Пополнение" must NOT be read as a counterparty.
        val op = NotificationParser.parse("Т-Банк", "Пополнение на 100 ₽. Доступно 200 ₽")!!
        assertNull(op.counterparty)
    }

    // ---- real-world phrasings (amounts and card numbers anonymised) ----

    @Test fun sber_glued_account_and_rubles_r() {
        val op = NotificationParser.parse("СберБанк", "СЧЁТ1234 10:15 Покупка 1 250р MAGNIT Баланс: 48 300,15р")!!
        assertEquals(-125_000L, op.amount.minor)
        assertEquals("RUB", op.currency)
        assertEquals(4_830_015L, op.balance!!.minor)
    }

    @Test fun tbank_purchase_with_card_mask() {
        val op = NotificationParser.parse("Т-Банк", "Покупка, карта *1234. 500 ₽. Пятёрочка. Доступно 12 345,67 ₽")!!
        assertEquals(-50_000L, op.amount.minor)
        assertEquals(1_234_567L, op.balance!!.minor)
    }

    @Test fun card_mask_is_not_glued_to_amount() {
        val op = NotificationParser.parse(null, "Оплата *1234 500 ₽")!!
        assertEquals(-50_000L, op.amount.minor)
    }

    @Test fun alfa_rur_with_balance() {
        val op = NotificationParser.parse("Альфа-Банк", "Покупка 2 000,50 RUR в YANDEX.TAXI. Карта *5678. Баланс: 9 999,50 RUR")!!
        assertEquals(-200_050L, op.amount.minor)
        assertEquals("RUB", op.currency)
        assertEquals(999_950L, op.balance!!.minor)
    }

    @Test fun usd_operation() {
        val op = NotificationParser.parse(null, "Покупка 12.99 USD APPLE.COM. Доступно 150.00 USD")!!
        assertEquals(-1_299L, op.amount.minor)
        assertEquals("USD", op.currency)
        assertEquals(15_000L, op.balance!!.minor)
    }

    @Test fun dollar_sign_before_amount() {
        val op = NotificationParser.parse(null, "Списание \$25 по подписке")!!
        assertEquals(-2_500L, op.amount.minor)
        assertEquals("USD", op.currency)
    }

    @Test fun without_balance() {
        val op = NotificationParser.parse(null, "Зачисление 15 000 ₽ от Иван И.")!!
        assertEquals(1_500_000L, op.amount.minor)
        assertNull(op.balance)
    }

    @Test fun rub_short_forms() {
        assertEquals(50_000L, NotificationParser.parse(null, "Пополнение 500р")!!.amount.minor)
        assertEquals(50_000L, NotificationParser.parse(null, "Пополнение 500 р.")!!.amount.minor)
        assertEquals(50_000L, NotificationParser.parse(null, "Пополнение 500руб")!!.amount.minor)
        assertEquals(50_000L, NotificationParser.parse(null, "Пополнение 500 рублей")!!.amount.minor)
    }

    @Test fun comma_thousands_dot_decimals() {
        val op = NotificationParser.parse(null, "Покупка 1,234.56 ₽")!!
        assertEquals(-123_456L, op.amount.minor)
    }

    @Test fun currency_is_taken_next_to_the_amount_not_anywhere() {
        // "USD" in the merchant name must not turn a ruble purchase into dollars.
        val op = NotificationParser.parse(null, "Покупка 300 ₽ в магазине USD-SHOP")!!
        assertEquals("RUB", op.currency)
    }

    @Test fun pay_by_card_amount_is_not_a_mask() {
        val op = NotificationParser.parse(null, "Оплата картой 5000 ₽")!!
        assertEquals(-500_000L, op.amount.minor)
    }

    @Test fun belarusian_rubles() {
        val op = NotificationParser.parse(null, "Оплата 25,40 BYN. Остаток 100,00 BYN", defaultCurrency = null)!!
        assertEquals("BYN", op.currency)
        assertEquals(-2_540L, op.amount.minor)
        val br = NotificationParser.parse(null, "Оплата 10 Br", defaultCurrency = null)!!
        assertEquals("BYN", br.currency)
    }

    @Test fun no_currency_and_no_default_is_not_recognised() {
        assertNull(NotificationParser.parse(null, "Оплата 25,40. Остаток 100,00", defaultCurrency = null))
    }

    @Test fun time_and_date_are_not_amounts() {
        assertNull(NotificationParser.parse(null, "Вход в приложение 21.10.2026 в 10:15", defaultCurrency = null))
        val op = NotificationParser.parse(null, "21.10 10:15 Покупка 99 ₽")!!
        assertEquals(-9_900L, op.amount.minor)
    }
}
