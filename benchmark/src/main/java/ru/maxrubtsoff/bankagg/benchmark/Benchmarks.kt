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
