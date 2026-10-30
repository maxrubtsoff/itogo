package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.parser.OpDirection
import ru.maxrubtsoff.bankagg.domain.model.TransactionKind
import ru.maxrubtsoff.bankagg.domain.parser.NotificationParser

class NotificationParserTest {

    @Test fun bsb_goodsAndServices_isExpenseInOtherCurrency() {
        val p = NotificationParser.parse(
            "BSB Bank",
            "4475, Сумма: 1 USD, Успешно, Товары и услуги, 2026-10-30 11:31:53, Остаток: 100.29 EUR, LU3/Luxembourg/PAYPAL"
        )!!
        assertEquals(OpDirection.OUT, p.direction)
        assertEquals(TransactionKind.EXPENSE, p.kind)
        assertEquals("USD", p.currency)
        assertEquals(-100L, p.amount.minor)
        assertEquals(10029L, p.balance!!.minor)
    }

    @Test fun bsb_goodsAndServices_sameCurrency() {
        val p = NotificationParser.parse(
            "BSB Bank",
            "4475, Сумма: 9.94 EUR, Успешно, Товары и услуги, 2026-10-30 11:31:56, Остаток: 91.28 EUR, HK3/XXX XXX/PA..."
        )!!
        assertEquals(OpDirection.OUT, p.direction)
        assertEquals("EUR", p.currency)
        assertEquals(-994L, p.amount.minor)
        assertEquals(9128L, p.balance!!.minor)
    }

    @Test fun bsb_refund_stillIncome() {
        val p = NotificationParser.parse(
            "BSB Bank",
            "4475, Сумма: 1 USD, Успешно, Возврат средств, 2026-10-30 11:31:53, Остаток: 101.22 EUR, LU3/Luxembourg/PAYPAL"
        )!!
        assertEquals(OpDirection.IN, p.direction)
        assertEquals(100L, p.amount.minor)
    }

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

    // B-82: a bare number is never an amount; card/account masks give no numbers.

    @Test fun bare_account_number_is_not_an_amount() {
        assertNull(NotificationParser.parse(null, "Перевод от Иван И. Счёт 40817810 Баланс 5 000 ₽"))
    }

    @Test fun bare_date_like_number_is_not_an_amount() {
        assertNull(NotificationParser.parse(null, "Зачисление 20261026 Баланс 1 000 ₽"))
    }

    @Test fun middle_dot_card_mask_is_not_an_amount() {
        assertEquals(-50_000L, NotificationParser.parse(null, "Карта ·· 2026 Покупка 500 ₽")!!.amount.minor)
        assertNull(NotificationParser.parse(null, "Карта ·· 2026 Покупка"))
    }

    @Test fun card_brand_masks_are_not_amounts() {
        for (t in listOf("ECMC2026 Покупка 300 ₽", "MC*1234 Оплата 300 ₽", "MAES 5678 Покупка 300 ₽")) {
            assertEquals(t, -30_000L, NotificationParser.parse(null, t)!!.amount.minor)
        }
    }

    @Test fun spaced_account_number_is_masked() {
        assertEquals(150_000L, NotificationParser.parse(null, "Счёт 4081 7810 Зачисление 1 500 ₽")!!.amount.minor)
    }

    @Test fun amount_after_schet_with_currency_stays() {
        assertEquals(500_000L, NotificationParser.parse(null, "Зачисление на счёт 5000 ₽")!!.amount.minor)
    }

    @Test fun plain_number_without_currency_is_null() {
        assertNull(NotificationParser.parse(null, "Покупка 500 Магнит"))
    }

    // B-83: a phone number sets neither the direction nor the amount.

    @Test fun phone_plus_is_not_income_sign() {
        assertNull(NotificationParser.parse(null, "Перевод по номеру +7 926 263-45-67 1 500 ₽"))
    }

    @Test fun phone_digits_are_not_amount() {
        assertNull(NotificationParser.parse(null, "Перевод от +79262634567"))
    }

    @Test fun phone_with_explicit_word_keeps_amount() {
        assertEquals(-150_000L, NotificationParser.parse(null, "Списание 1 500 ₽ перевод на +7 926 263-45-67")!!.amount.minor)
    }

    @Test fun explicit_sign_still_works() {
        assertEquals(110_000L, NotificationParser.parse(null, "+1 100 ₽ Иван И.")!!.amount.minor)
        assertEquals(-50_000L, NotificationParser.parse(null, "−500 ₽ Магнит")!!.amount.minor)
    }

    // B-84: "входящий перевод … от Имени" is income.

    @Test fun incoming_transfer_with_sender_is_income() {
        val op = NotificationParser.parse(null, "Входящий перевод 500 ₽ от Ивана Иванова")!!
        assertEquals(50_000L, op.amount.minor)
        assertEquals(TransactionKind.TRANSFER, op.kind)
        assertEquals(OpDirection.IN, op.direction)
    }

    @Test fun transfer_from_name_not_adjacent_is_income() {
        assertEquals(50_000L, NotificationParser.parse(null, "Перевод 500 ₽ от Ивана Иванова")!!.amount.minor)
    }

    @Test fun outgoing_word_wins() {
        assertEquals(-70_000L, NotificationParser.parse(null, "Исходящий перевод 700 ₽ Ивану Петрову")!!.amount.minor)
    }

    @Test fun payment_from_name_stays_out() {
        assertEquals(-20_000L, NotificationParser.parse(null, "Оплата 200 ₽ от Ивана Иванова")!!.amount.minor)
    }

    private val alfa = "Списано 440.00 счет *4863 п/п №92712 от 27.10.26 в адрес АО \"АЛЬФА-БАНК\" счет 0741. Остаток 3079.36"

    @Test fun alfaBusinessPaymentOrderParsed() {
        for (title in listOf(null, "Альфа-Бизнес")) {
            val op = NotificationParser.parse(title, alfa)!!
            assertEquals(-44_000L, op.amount.minor)
            assertEquals("RUB", op.currency)
            assertEquals(OpDirection.OUT, op.direction)
            assertEquals(307_936L, op.balance!!.minor)
            assertEquals(TransactionKind.EXPENSE, op.kind)
        }
    }

    @Test fun alfaBusinessRecipientAccountIsNotOurs() {
        val op = NotificationParser.parse(null, alfa)!!
        assertTrue(op.balance!!.minor != 74_100L)
        assertTrue(op.amount.minor != -92_712L && op.amount.minor != -2_710L)
        assertNull(op.counterparty)
    }

    @Test fun alfaBusinessWithoutOwnMaskIsNull() {
        assertNull(NotificationParser.parse(null, "Списано 440.00 счет 0741. Остаток 3079.36"))
    }

    @Test fun bareAmountStillNotAnAmount() {
        assertNull(NotificationParser.parse(null, "Списано 440.00"))
        assertNull(NotificationParser.parse(null, "Оплата 440.00 счет *4863"))
    }

    @Test fun alfaBusinessWithoutBalance() {
        val op = NotificationParser.parse(null, "Списано 440.00 счет *4863 п/п №92712")!!
        assertEquals(-44_000L, op.amount.minor)
        assertNull(op.balance)
    }

    @Test fun alfaBusinessCredit() {
        val op = NotificationParser.parse(null, "Зачислено 1000.00 счет *4863 п/п №1. Остаток 5000.00")!!
        assertEquals(OpDirection.IN, op.direction)
        assertEquals(100_000L, op.amount.minor)
    }

    @Test fun oldFormatKeepsUnknownKind() {
        val op = NotificationParser.parse(null, "Поступило 500 ₽. Доступно 1 000 ₽")
        assertEquals(TransactionKind.UNKNOWN, op!!.kind)
    }
}
