package ru.maxrubtsoff.bankagg.benchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val PACKAGE = "ru.maxrubtsoff.bankagg"

private const val WAIT = 5_000L

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
private fun MacrobenchmarkScope.tap(text: String) {
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
