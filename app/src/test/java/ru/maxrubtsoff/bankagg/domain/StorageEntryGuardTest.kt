package ru.maxrubtsoff.bankagg.domain

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * B-91: while the data is closed, no way into the app may reach the database
 * (`AppContainer.db` throws then). Every entry point checks `isReady` first; the
 * screen of the last resort is what the user sees instead of a crash.
 */
class StorageEntryGuardTest {

    private val root = File("src/main/java/ru/maxrubtsoff/bankagg")

    private fun source(path: String): String {
        val f = File(root, path)
        assertTrue("not found: ${f.absolutePath}", f.isFile)
        return f.readText()
    }

    @Test fun backgroundEntryPointsCheckReadiness() {
        listOf(
            "service/BankNotificationListener.kt",
            "data/notify/ReminderWorker.kt",
            "data/notify/CapitalSnapshotWorker.kt",
            "data/notify/NewBankActionReceiver.kt",
            "ui/widget/CashbackWidget.kt",
            "App.kt"
        ).forEach { assertTrue("$it must check isReady / storage status", Regex("""isReady|StorageStatus\.Ready""").containsMatchIn(source(it))) }
    }

    @Test fun onResumeChoresWaitForOpenData() {
        val src = source("ui/MainActivity.kt")
        val body = src.substring(src.indexOf("override fun onResume()"))
        val guard = body.indexOf("isReady")
        assertTrue("onResume must check isReady", guard >= 0)
        listOf("checkReminders", "refreshRates", "refreshWidget").forEach {
            assertTrue("$it runs before the isReady check in onResume", body.indexOf(it) > guard)
        }
    }

    @Test fun nothingElseInMainActivityTouchesDataOutsideTheReadyBranch() {
        val src = source("ui/MainActivity.kt")
        // The lock and settings (preferences) are fine; the app's screens are built only when the data is open.
        val ready = src.indexOf("storage is StorageStatus.Ready")
        assertTrue(ready >= 0)
        assertTrue("AppRoot must be inside the Ready branch", src.indexOf("AppRoot(", ready) in ready..ready + 200)
    }
}
