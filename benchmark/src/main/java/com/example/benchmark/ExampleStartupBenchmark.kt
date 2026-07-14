package com.example.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android Macrobenchmark suite for Kairo.
 * Measures app startup performance and frame jank during navigation.
 */
@RunWith(AndroidJUnit4::class)
class ExampleStartupBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupCold() = benchmarkRule.measureRepeated(
        packageName = "com.kairo.assistant",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.COLD
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun startupWarm() = benchmarkRule.measureRepeated(
        packageName = "com.kairo.assistant",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun navigateAndScrollSettings() = benchmarkRule.measureRepeated(
        packageName = "com.kairo.assistant",
        metrics = listOf(FrameTimingMetric()),
        iterations = 3,
        startupMode = StartupMode.WARM
    ) {
        pressHome()
        startActivityAndWait()

        // 1. Locate Settings button
        val settingsBtn = device.findObject(By.desc("Settings"))
            ?: device.findObject(By.text("Settings"))
            ?: device.findObject(By.descContains("settings"))

        settingsBtn?.click()
        device.waitForIdle()

        // 2. Locate scrollable list and scroll to test frame metrics
        val scrollableList = device.findObject(By.scrollable(true))
        scrollableList?.scroll(Direction.DOWN, 1.0f)
        device.waitForIdle()
        scrollableList?.scroll(Direction.UP, 1.0f)
        device.waitForIdle()
    }
}