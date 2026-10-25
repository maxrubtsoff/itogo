package ru.maxrubtsoff.bankagg.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val PACKAGE = "ru.maxrubtsoff.bankagg"

const val WAIT = 5_000L

/** Past the first launch (slides, «Что храним», a bank) to the bottom bar. */
fun MacrobenchmarkScope.toTabs() {
    if (device.wait(Until.hasObject(By.text("Платежи")), 2_000)) return
    device.findObject(By.text("Пропустить"))?.click()
    device.wait(Until.hasObject(By.text("Дальше")), WAIT)
    device.findObject(By.text("Дальше"))?.click()
    if (device.wait(Until.hasObject(By.text("Т-Банк")), WAIT)) {
        device.findObject(By.text("Т-Банк"))?.click()
        device.findObject(By.text("Готово"))?.click()
    }
    device.wait(Until.hasObject(By.text("Платежи")), WAIT)
}

/** The bottom bar, all four tabs, [rounds] times — what U-31 is about. */
fun MacrobenchmarkScope.switchTabs(rounds: Int = 3) {
    repeat(rounds) {
        listOf("Капитал", "Платежи", "Настройки", "Обзор").forEach { tab ->
            tap(tab)
            device.waitForIdle()
        }
    }
}

/** A tab label may be redrawn between finding and clicking it: find again. */
fun MacrobenchmarkScope.tap(text: String) {
    repeat(3) {
        try {
            device.wait(Until.findObject(By.text(text)), WAIT)?.click() ?: return
            return
        } catch (_: androidx.test.uiautomator.StaleObjectException) {
        }
    }
}

/** «Капитал», its sections swiped across and back. */
fun MacrobenchmarkScope.swipeCapital() {
    tap("Капитал")
    device.waitForIdle()
    val w = device.displayWidth
    val h = device.displayHeight
    repeat(3) { device.swipe(w * 4 / 5, h / 2, w / 5, h / 2, 20); device.waitForIdle() }
    repeat(3) { device.swipe(w / 5, h / 2, w * 4 / 5, h / 2, 20); device.waitForIdle() }
}

@Suppress("unused")
private val directionKeep = Direction.LEFT

/**
 * U-55: fills an empty app with [BenchmarkData]-sized example data (10 banks,
 * 30 accounts, 15 deposits, 6 months of cashback, 20 subscriptions). The
 * receiver exists only in the benchmark build and does nothing in an app that
 * already has banks, so a repeat — and a phone with real data — is safe.
 * `am broadcast` returns when the receiver is done.
 */
fun MacrobenchmarkScope.seedData() {
    device.executeShellCommand("am broadcast -n $PACKAGE/ru.maxrubtsoff.bankagg.benchmark.BenchmarkSeedReceiver")
}

/** The first tap on [text] if the screen has it within [WAIT]; false when it never showed up (the scenario goes on). */
fun MacrobenchmarkScope.tapIfThere(text: String): Boolean {
    val found = device.wait(Until.findObject(By.text(text)), WAIT) ?: return false
    return try { found.click(); true } catch (_: androidx.test.uiautomator.StaleObjectException) { false }
}

/** The same for a description (an icon button). */
fun MacrobenchmarkScope.tapDescIfThere(desc: String): Boolean {
    val found = device.wait(Until.findObject(By.desc(desc)), WAIT) ?: return false
    return try { found.click(); true } catch (_: androidx.test.uiautomator.StaleObjectException) { false }
}

/** [rounds] flings down and back up: the long lists of the filled app. */
fun MacrobenchmarkScope.scrollLists(rounds: Int = 3) {
    val w = device.displayWidth
    val h = device.displayHeight
    repeat(rounds) {
        repeat(3) { device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 12); device.waitForIdle() }
        repeat(3) { device.swipe(w / 2, h / 4, w / 2, h * 3 / 4, 12); device.waitForIdle() }
    }
}

/** «Платежи»: the cashback page, then (swiped over) the subscriptions. */
fun MacrobenchmarkScope.scrollPayments() {
    tap("Платежи")
    device.waitForIdle()
    scrollLists()
    tap("Подписки")
    device.waitForIdle()
    scrollLists()
}

/** «Капитал» → the accounts section and the others, each scrolled. */
fun MacrobenchmarkScope.scrollCapital() {
    tap("Капитал")
    device.waitForIdle()
    scrollLists()
}

/** «Настройки» → «Банки»: the list of the user's banks. */
fun MacrobenchmarkScope.scrollBanks() {
    tap("Настройки")
    device.waitForIdle()
    if (tapIfThere("Банки")) {
        device.waitForIdle()
        scrollLists()
        device.pressBack()
    }
}

/** «Платежи» → «+» → «Вручную» → «Категория»: the form and the category sheet opening. */
fun MacrobenchmarkScope.openCashbackForm() {
    tap("Платежи")
    device.waitForIdle()
    if (!tapDescIfThere("Добавить кэшбэк")) return
    if (!tapIfThere("Вручную")) return
    device.waitForIdle()
    // The form may ask for the bank first; the sheet is opened by the «Категория» button when it is there.
    if (tapIfThere("Категория")) device.waitForIdle()
    device.pressBack()
    device.pressBack()
}

/** «Настройки» → «Обучение» → the first tour starting. */
fun MacrobenchmarkScope.startTour() {
    tap("Настройки")
    device.waitForIdle()
    if (!tapIfThere("Обучение")) return
    if (tapIfThere("Знакомство с приложением")) {
        device.waitForIdle()
        Thread.sleep(1_500)
        device.pressBack()
    }
    device.pressBack()
}

/** «Обзор»: the eye hides the amounts behind dust and shows them again, [rounds] times. */
fun MacrobenchmarkScope.toggleAmounts(rounds: Int = 3) {
    tap("Обзор")
    device.waitForIdle()
    repeat(rounds) {
        if (!tapDescIfThere("Скрыть суммы")) return
        Thread.sleep(800)
        if (!tapDescIfThere("Показать суммы")) return
        device.waitForIdle()
    }
}
