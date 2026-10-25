package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.TransactionKind
import ru.maxrubtsoff.bankagg.domain.parser.NotificationParser
import ru.maxrubtsoff.bankagg.domain.parser.OpDirection

/**
 * Direction of an operation (money in / money out) is read separately from its
 * kind, and the sign comes from the direction (B-45). A push without an
 * operation sign at all, or an advertisement with a number in it, is not an
 * operation (B-46).
 */
class OpDirectionTest {

    // ---- B-45: sign follows the direction, not the kind ----

    @Test fun alfa_outgoing_transfer_isNegative() {
        val op = NotificationParser.parse(
            "Альфа-Банк",
            "Списание со счета 4*1833; Перевод на сумму 5 000,00 RUB; Получатель: 798*0842; 23 октября 19:23"
        )!!
        assertEquals(-500_000L, op.amount.minor)
        assertEquals(TransactionKind.TRANSFER, op.kind)
        assertEquals(OpDirection.OUT, op.direction)
    }

    @Test fun incoming_transfer_isPositive() {
        val op = NotificationParser.parse("Альфа-Банк", "Зачисление; Перевод на сумму 5 000,00 RUB; Отправитель: 798*0842")!!
        assertEquals(500_000L, op.amount.minor)
        assertEquals(OpDirection.IN, op.direction)
    }

    @Test fun transfer_from_someone_isPositive() {
        val op = NotificationParser.parse("Сбербанк", "Перевод от Ивана П. 2 000 ₽. Баланс: 12 000 ₽")!!
        assertEquals(200_000L, op.amount.minor)
        assertEquals(TransactionKind.TRANSFER, op.kind)
    }

    @Test fun transfer_to_a_person_isNegative() {
        val op = NotificationParser.parse(null, "Перевод 3 000 ₽ Ивану Петрову. Доступно 10 000 ₽")!!
        assertEquals(-300_000L, op.amount.minor)
    }

    @Test fun transfer_to_a_card_isNegative() {
        val op = NotificationParser.parse("ВТБ", "Перевод на карту 1 500 ₽. Остаток 3 000 ₽")!!
        assertEquals(-150_000L, op.amount.minor)
    }

    @Test fun tbank_credit_with_na_before_amount_isPositive() {
        // Т-Банк writes an incoming transfer as "Перевод на <сумма>" (as "Пополнение на …").
        val op = NotificationParser.parse("Т-Банк", "Перевод на 1 100 ₽, счет RUB")!!
        assertEquals(110_000L, op.amount.minor)
        assertEquals(OpDirection.IN, op.direction)
    }

    @Test fun transfer_between_own_accounts_keepsKind() {
        val op = NotificationParser.parse("Т-Банк", "Списание: перевод между счетами 1 000 ₽")!!
        assertEquals(TransactionKind.TRANSFER, op.kind)
        assertEquals(-100_000L, op.amount.minor)
    }

    @Test fun transfer_withoutDirection_isNotRecognised() {
        assertNull(NotificationParser.parse("ВТБ", "Перевод 2 000 ₽, карта RUB"))
    }

    @Test fun explicit_sign_setsDirection() {
        assertEquals(110_000L, NotificationParser.parse("Ozon Банк", "+1 100 ₽. Баланс 2 000 ₽")!!.amount.minor)
        assertEquals(-50_000L, NotificationParser.parse("Ozon Банк", "−500 ₽. Баланс 1 500 ₽")!!.amount.minor)
        assertEquals(-50_000L, NotificationParser.parse("Ozon Банк", "-500 ₽. Баланс 1 500 ₽")!!.amount.minor)
    }

    // ---- B-46: a number alone is not an operation ----

    @Test fun promo_withPrize_isNotAnOperation() {
        assertNull(
            NotificationParser.parse(
                "Альфа-Бизнес",
                "Всё просто. Ставьте бочонки на карточки. Закроете одну — и вы в розыгрыше 100 000 рублей и завтрак"
            )
        )
    }

    @Test fun promo_aboutCashbackCategory_isNotAnOperation() {
        assertNull(
            NotificationParser.parse(
                "СберБанк",
                "Получить до 15% кешбэка в категории «СпасибоТревел» в октябре"
            )
        )
    }

    @Test fun fileProgress_isNotAnOperation() {
        assertNull(NotificationParser.parse("Банк", "32,01 МБ из 72,76 МБ"))
    }

    @Test fun promo_words_areNotOperations() {
        assertNull(NotificationParser.parse("Банк", "Оформите карту и получите 1 000 ₽ на счёт"))
        assertNull(NotificationParser.parse("Банк", "Акция: подключите подписку за 199 ₽"))
        assertNull(NotificationParser.parse("Банк", "Успейте до 31 октября: вклад под 20% годовых"))
        assertNull(NotificationParser.parse("Банк", "Оцените наше приложение и получите 500 бонусов"))
    }

    @Test fun units_areNotAmounts() {
        assertNull(NotificationParser.parse("Банк", "Списание 15 мин разговора"))
        assertNull(NotificationParser.parse("Банк", "Зачисление 5 шт бонусов"))
    }

    @Test fun realCashback_accrual_isStillAnOperation() {
        val op = NotificationParser.parse("Т-Банк", "Начислен кэшбэк 150 ₽. Доступно 1 000 ₽")!!
        assertEquals(15_000L, op.amount.minor)
        assertEquals(TransactionKind.INCOME, op.kind)
    }

    @Test fun realOperations_fromTheLog_stillParse() {
        val purchase = NotificationParser.parse("Ozon Банк", "Покупка в FAST-FUD NUMI. 1073 RUR. Баланс 1983.15 ₽")!!
        assertEquals(-107_300L, purchase.amount.minor)
        assertEquals(198_315L, purchase.balance!!.minor)

        val topUp = NotificationParser.parse("Ozon Банк", "Пополнение через СБП на 1 100 ₽")!!
        assertEquals(110_000L, topUp.amount.minor)
    }

    @Test fun balanceOnly_push_isNotAnOperation() {
        // "Остаток: 98 262,55 ₽; ··2326" has no operation in it: nothing is recorded
        // (a balance-only update is I-25 in ROADMAP).
        assertNull(NotificationParser.parse("Альфа-Банк", "Остаток: 98 262,55 ₽; ··2326"))
    }
}
