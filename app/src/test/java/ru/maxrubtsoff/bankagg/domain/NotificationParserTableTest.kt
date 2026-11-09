package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.TransactionKind
import ru.maxrubtsoff.bankagg.domain.parser.NotificationParser
import ru.maxrubtsoff.bankagg.domain.parser.OpDirection

/** U-76: a wide table of realistic pushes; each row is the whole expectation, `null` — not an operation. */
class NotificationParserTableTest {

    private data class Case(
        val name: String, val title: String?, val text: String,
        val dir: OpDirection?, val kind: TransactionKind? = null, val minor: Long? = null, val balance: Long? = null, val cur: String = "RUB"
    )

    private val O = OpDirection.OUT
    private val I = OpDirection.IN
    private val EXP = TransactionKind.EXPENSE
    private val INC = TransactionKind.INCOME
    private val TRF = TransactionKind.TRANSFER

    private val ops = listOf(
        // Cash: out and in
        Case("sber withdrawal", "Сбербанк", "Снятие наличных 5 000 ₽ Баланс: 12 345,67 ₽", O, EXP, -500_000, 1_234_567),
        Case("tbank atm", "Т-Банк", "Снятие в банкомате 3 000 ₽. Доступно 7 000 ₽", O, EXP, -300_000, 700_000),
        Case("cash deposit", "Сбербанк", "Взнос наличными 10 000 ₽ Баланс: 30 000 ₽", I, null, 1_000_000, 3_000_000),
        // Transfers (between own accounts: no direction in the text, so no operation)
        Case("own accounts out", "Т-Банк", "Перевод между счетами 15 000 ₽. Доступно 5 000 ₽", null),
        Case("kubyshka", "Т-Банк", "Перевод в Кубышку 20 000 ₽, счет RUB Баланс: 59 342,18 ₽", O, TRF, -2_000_000, 5_934_218),
        Case("savings transfer", "Т-Банк", "Перевод в накопительный счет 1 000 ₽ Доступно 4 000 ₽", O, TRF, -100_000, 400_000),
        Case("sbp out", "Т-Банк", "Перевод по СБП 2 500 ₽ Иван П. Доступно 10 000 ₽", O, TRF, -250_000, 1_000_000),
        Case("to other bank card", "Сбербанк", "Перевод на карту другого банка 700 ₽ Баланс: 5 000 ₽", O, TRF, -70_000, 500_000),
        Case("from person", "Сбербанк", "Перевод от Ивана И. 1 500 ₽ Баланс: 6 500 ₽", I, TRF, 150_000, 650_000),
        Case("transfer na credit", "Т-Банк", "Перевод на 1 100 ₽ Доступно 2 100 ₽", I, null, 110_000, 210_000),
        // Fees, refunds, cashback
        Case("fee", "Т-Банк", "Комиссия 59 ₽ за обслуживание. Доступно 941 ₽", O, EXP, -5_900, 94_100),
        Case("refund", "Сбербанк", "Возврат покупки 1 200 ₽ Баланс: 4 200 ₽", I, INC, 120_000, 420_000),
        Case("cashback", "Т-Банк", "Кэшбэк 150 ₽ начислен. Доступно 2 150 ₽", I, INC, 15_000, 215_000),
        // Purchases and currency
        Case("purchase", "Т-Банк", "Покупка 440 ₽. Магнит. Доступно 1 000 ₽", O, EXP, -44_000, 100_000),
        Case("usd purchase", "Т-Банк", "Покупка 12,50 $. Shop. Доступно 100 $", O, EXP, -1_250, 10_000, "USD"),
        Case("usd amount rub account", "Т-Банк", "Покупка 10 $. Shop. Доступно 1 000 ₽", O, EXP, -1_000, 100_000, "USD"),
        // Balance labels
        Case("label Баланс", "Сбербанк", "Покупка 300 ₽ Баланс: 1 700 ₽", O, EXP, -30_000, 170_000),
        Case("label Доступно", "Т-Банк", "Покупка 300 ₽ Доступно 1 700 ₽", O, EXP, -30_000, 170_000),
        Case("label Остаток", "Альфа-Банк", "Покупка 300 ₽ Остаток 1 700 ₽", O, EXP, -30_000, 170_000),
        // Number shapes
        Case("space groups", "Т-Банк", "Покупка 1 234,56 ₽. Доступно 10 000 ₽", O, EXP, -123_456, 1_000_000),
        Case("comma-dot", "Т-Банк", "Покупка 1,234.56 ₽. Доступно 10,000.00 ₽", O, EXP, -123_456, 1_000_000),
        Case("nbsp", "Т-Банк", "Покупка 1 234,56 ₽. Доступно 10 000 ₽", O, EXP, -123_456, 1_000_000),
        Case("nnbsp", "Т-Банк", "Покупка 1 234,56 ₽. Доступно 10 000 ₽", O, EXP, -123_456, 1_000_000),
        Case("dot cents", "Т-Банк", "Покупка 440.00 ₽. Доступно 1 000.00 ₽", O, EXP, -44_000, 100_000),
        Case("no cents", "Т-Банк", "Покупка 440 ₽. Доступно 1000 ₽", O, EXP, -44_000, 100_000)
    )

    private fun opif(title: String) =
        title to "Купите любой ОПИФ и мы вернём до 1500 ₽ с учётом суммы пополнения 👉"

    private val negatives = listOf(
        "sber utilities promo" to ("Сбербанк" to "Платите за ЖКХ с картой Сбербанка и получайте до 5% бонусами"),
        "tbank 25 brands" to ("Т-Банк" to "25 брендов с кэшбэком до 30% — выберите категории на месяц"),
        "ozon" to ("Ozon" to "Скидка 500 ₽ на первый заказ. Промокод: START"),
        "alfa fuel" to ("Альфа-Банк" to "Заправляйтесь на АЗС и получайте кэшбэк до 10% в категории Авто"),
        "tatenergosbyt bill" to ("Татэнергосбыт" to "6106.66 ₽ Татэнергосбыт Оплатите в приложении"),
        "no amount" to ("Т-Банк" to "Вход в приложение выполнен с нового устройства"),
        "bare number" to ("Т-Банк" to "Ваш код 1234. Никому не сообщайте"),
        // B-128: «купите / вернём до» is an advertisement whatever the title says.
        "alfa opif promo" to opif("Альфа-Банк"),
        "alfa opif promo cashback title" to opif("Кешбэк за ОПИФ"),
        "alfa opif promo refund title" to opif("Возврат до 1500 ₽"),
        "alfa opif promo from title" to opif("Подарок от Альфа-Банка"),
        "up to amount no balance" to ("Т-Банк" to "Вернём до 3 000 ₽ за первую покупку"),
        "emoji promo" to ("Т-Банк" to "Пополните счёт на 1 000 ₽ и получите подарок 🎁"),
        "bare amount balance" to ("Сбербанк" to "100 000 ₽ — Баланс: 79 821,66 ₽ Счёт карты МИР •• 0402")
    )

    @Test fun table() {
        val bad = ops.mapNotNull { c ->
            val p = NotificationParser.parse(c.title, c.text)
            when {
                c.dir == null -> if (p != null) "${c.name}: expected null, got $p" else null
                p == null -> "${c.name}: got null"
                p.direction != c.dir -> "${c.name}: dir ${p.direction}"
                c.kind != null && p.kind != c.kind -> "${c.name}: kind ${p.kind}"
                p.amount.minor != c.minor -> "${c.name}: amount ${p.amount.minor}"
                p.balance?.minor != c.balance -> "${c.name}: balance ${p.balance?.minor}"
                p.currency != c.cur -> "${c.name}: cur ${p.currency}"
                else -> null
            }
        }
        assertEquals(bad.joinToString("\n"), 0, bad.size)
    }

    @Test fun negatives() {
        negatives.forEach { (name, tt) -> assertNull(name, NotificationParser.parse(tt.first, tt.second)) }
    }
}
