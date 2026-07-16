package com.example.benchmark

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
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
        startupMode = StartupMode.COLD,
        setupBlock = { grantPermissions() }
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun startupWarm() = benchmarkRule.measureRepeated(
        packageName = "com.kairo.assistant",
        metrics = listOf(StartupTimingMetric()),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = { grantPermissions() }
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun navigateAndScrollSettings() = benchmarkRule.measureRepeated(
        packageName = "com.kairo.assistant",
        metrics = listOf(FrameTimingMetric()),
        iterations = 3,
        startupMode = StartupMode.WARM,
        setupBlock = {
            grantPermissions()
            pressHome()
            startActivityAndWait()
            device.waitForIdle()

            // Handle onboarding if it appears
            device.findObject(By.textContains("Grant"))?.click()
            
            // Navigate to Settings screen in the setup block so that the 
            // measurement block focuses exclusively on scrolling performance.
            // This also prevents the ephemeral Home screen from auto-closing during the trace.
            if (!device.hasObject(By.text("Settings"))) {
                val settingsBtn = device.wait(Until.findObject(By.desc("settings_button")), 5000)
                    ?: device.findObject(By.desc("Settings"))
                    ?: device.findObject(By.text("Settings"))
                settingsBtn?.click()
                device.wait(Until.hasObject(By.text("Settings")), 5000)
            }
        }
    ) {
        // Measure scrolling performance on the Settings screen
        val screenWidth = device.displayWidth
        val screenHeight = device.displayHeight

        device.swipe(
            screenWidth / 2,
            (screenHeight * 0.8f).toInt(),
            screenWidth / 2,
            (screenHeight * 0.2f).toInt(),
            60
        )
        device.waitForIdle()

        device.swipe(
            screenWidth / 2,
            (screenHeight * 0.2f).toInt(),
            screenWidth / 2,
            (screenHeight * 0.8f).toInt(),
            60
        )
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.grantPermissions() {
        val permissions = listOf(
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_CONTACTS",
            "android.permission.CALL_PHONE",
            "android.permission.SEND_SMS",
            "android.permission.READ_PHONE_STATE"
        )
        permissions.forEach { permission ->
            device.executeShellCommand("pm grant com.kairo.assistant $permission")
        }
    }
}
