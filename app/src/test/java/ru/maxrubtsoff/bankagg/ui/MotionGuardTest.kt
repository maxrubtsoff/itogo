package ru.maxrubtsoff.bankagg.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The motion scale holds (U-55, P8–P9): the system «Remove animations» switch
 * is read in one place, and screens take every duration from `Motion`.
 */
class MotionGuardTest {

    private val root = File("src/main/java/ru/maxrubtsoff/bankagg/ui")

    private fun sources(): List<File> {
        assertTrue("ui sources not found from ${File(".").absolutePath}", root.isDirectory)
        return root.walk().filter { it.isFile && it.extension == "kt" }.toList()
    }

    /** The animation setting is read only by `Motion.enabled`; everyone else asks that. */
    @Test fun animationSettingReadInOnePlace() {
        val found = sources()
            .filter { it.name != "Motion.kt" && "areAnimatorsEnabled" in it.readText() }
            .map { it.name }
        assertTrue("Read the setting through Motion.enabled, not directly: $found", found.isEmpty())
    }
}
