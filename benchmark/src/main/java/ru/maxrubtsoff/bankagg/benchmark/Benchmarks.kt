package ru.maxrubtsoff.bankagg.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * U-31: start-up and frame timing on the benchmark build (the release as
 * installed). Results land in benchmark/build/outputs; the numbers before and
 * after go to docs/analysis/performance.md.
 */
@RunWith(AndroidJUnit4::class)
class Benchmarks {

    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun coldStart() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.COLD,
        iterations = 5,
        setupBlock = { pressHome() }
    ) {
        startActivityAndWait()
    }

    @Test fun tabs() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { pressHome(); startActivityAndWait(); toTabs() }
    ) {
        switchTabs()
    }

    @Test fun capitalSections() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { pressHome(); startActivityAndWait(); toTabs() }
    ) {
        swipeCapital()
    }
}

/**
 * U-55: the same screens on a full phone — 10 banks, 30 accounts, 15 deposits,
 * 6 months of cashback, 20 subscriptions ([seedData]). Run on an emulator or a
 * test phone: on an app that already has banks nothing is seeded and the
 * numbers are those of the real data.
 */
@RunWith(AndroidJUnit4::class)
class FilledBenchmarks {

    @get:Rule val rule = MacrobenchmarkRule()

    private fun measure(block: androidx.benchmark.macro.MacrobenchmarkScope.() -> Unit) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { pressHome(); seedData(); startActivityAndWait(); toTabs() },
        measureBlock = block
    )

    @Test fun scrollCapital() = measure { scrollCapital() }

    @Test fun scrollPayments() = measure { scrollPayments() }

    @Test fun scrollBanks() = measure { scrollBanks() }

    @Test fun cashbackFormAndCategorySheet() = measure { openCashbackForm() }

    @Test fun startTour() = measure { startTour() }

    @Test fun hideAndShowAmounts() = measure { toggleAmounts() }

    /**
     * Recognising a screenshot: the picker is the system's, so the scenario
     * cannot reach a picture by itself. A hook is needed to measure it: a
     * benchmark-only intent that hands a picture from the app's assets to the
     * review screen. Until it exists the test is skipped, not failed.
     */
    @Test fun recognizeShot() {
        org.junit.Assume.assumeTrue("no benchmark hook for the screenshot picker yet", false)
    }
}
