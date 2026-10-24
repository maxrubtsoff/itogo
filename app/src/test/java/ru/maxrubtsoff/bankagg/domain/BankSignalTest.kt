package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.maxrubtsoff.bankagg.domain.model.BankSignal

class BankSignalTest {

    private fun bank(text: String, title: String? = null, pkg: String = "com.some.bank") =
        BankSignal.looksLikeBank(pkg, title, text)

    @Test fun realBankOperations_pass() {
        assertTrue(bank("Пополнение на 5 000 ₽, счет RUB. Максим Р. Доступно 107 409,81 ₽"))
        assertTrue(bank("Оплата 450 ₽ Магнит. Доступно 106 959,81 ₽"))
        assertTrue(bank("СЧЁТ1234 10:15 Покупка 1 250р MAGNIT Баланс: 48 300,15р"))
        assertTrue(bank("Покупка, карта *1234. 500 ₽. Пятёрочка. Доступно 12 345,67 ₽"))
        assertTrue(bank("Покупка 2 000,50 RUR в YANDEX.TAXI. Карта *5678. Баланс: 9 999,50 RUR"))
        assertTrue(bank("Покупка 12.99 USD APPLE.COM. Доступно 150.00 USD"))
        assertTrue(bank("Списание \$25 по подписке"))
        assertTrue(bank("Оплата 12,50 BYN. Доступно: 340,00 BYN", title = "BSB Bank"))
        assertTrue(bank("Покупка 1,234.56 ₽"))
        assertTrue(bank("Пополнение 500 рублей"))
    }

    @Test fun messengerWithAFile_isNotABank() {
        assertFalse(bank("Загрузка Screen_Recording_20261022_173327_.mp4", title = "Избранное", pkg = "ru.oneme.app"))
        // Even from an unknown app: a file name is not an amount.
        assertFalse(bank("Загрузка Screen_Recording_20261022_173327_.mp4", title = "Избранное"))
    }

    @Test fun codesOrdersDatesAndUpdates_areNotBanks() {
        assertFalse(bank("Ваш код 1234. Никому не сообщайте"))
        assertFalse(bank("Заказ №12345 доставлен в пункт выдачи"))
        assertFalse(bank("Обновление 2026.10.22 готово к установке"))
        assertFalse(bank("Напоминание: встреча 22.10 в 15:00"))
        assertFalse(bank("Вход в приложение 21.10.2026 в 10:15"))
    }

    @Test fun moneyWithoutABankWord_isNotEnough() {
        assertFalse(bank("Скидка 500 ₽ на первый заказ"))
    }

    @Test fun bankWordWithoutMoney_isNotEnough() {
        assertFalse(bank("Ваша карта готова, заберите в отделении"))
        assertFalse(bank("Оплата 25,40. Остаток 100,00"))
    }

    @Test fun messengersAndSystemApps_areNeverBanks() {
        val payment = "Оплата 450 ₽ Магнит. Доступно 106 959,81 ₽"
        assertFalse(bank(payment, pkg = "org.telegram.messenger"))
        assertFalse(bank(payment, pkg = "com.whatsapp"))
        assertFalse(bank(payment, pkg = "com.android.systemui"))
        assertTrue(bank(payment, pkg = "com.unknown.wallet"))
    }
}
