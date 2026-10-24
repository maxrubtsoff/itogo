package ru.maxrubtsoff.bankagg.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * U-31: the app's own Baseline Profile — start-up, the four tabs, the capital
 * sections. The printed file goes to app/src/main/baseline-prof.txt.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule val rule = BaselineProfileRule()

    @Test fun generate() = rule.collect(packageName = PACKAGE) {
        pressHome()
        startActivityAndWait()
        toTabs()
        switchTabs(rounds = 2)
        swipeCapital()
    }
}
