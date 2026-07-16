package com.example.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates Baseline Profiles for Kairo to optimize app startup time
 * by pre-compiling start paths.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = "com.kairo.assistant",
        includeInStartupProfile = true
    ) {
        // Grant permissions via adb shell to bypass PermissionScreen
        device.executeShellCommand("pm grant com.kairo.assistant android.permission.RECORD_AUDIO")
        device.executeShellCommand("pm grant com.kairo.assistant android.permission.READ_CONTACTS")
        device.executeShellCommand("pm grant com.kairo.assistant android.permission.CALL_PHONE")
        device.executeShellCommand("pm grant com.kairo.assistant android.permission.SEND_SMS")
        device.executeShellCommand("pm grant com.kairo.assistant android.permission.READ_PHONE_STATE")
        
        // Core user flows for startup
        pressHome()
        startActivityAndWait()
    }
}
