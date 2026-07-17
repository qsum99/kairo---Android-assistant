package com.kairo.assistant.actions

import android.content.Context
import android.net.wifi.WifiManager
import com.kairo.assistant.nlu.models.ParsedCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyBoolean
import org.mockito.Mockito.any

class WifiExecutorTest {

    private lateinit var context: Context
    private lateinit var appContext: Context
    private lateinit var wifiManager: WifiManager
    private lateinit var executor: WifiExecutor

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        appContext = mock(Context::class.java)
        wifiManager = mock(WifiManager::class.java)
        executor = WifiExecutor()

        `when`(context.applicationContext).thenReturn(appContext)
        `when`(appContext.getSystemService(Context.WIFI_SERVICE)).thenReturn(wifiManager)
    }

    @Test
    fun testToggleWifiProgrammaticSuccessOn() {
        `when`(wifiManager.setWifiEnabled(true)).thenReturn(true)

        val command = ParsedCommand(
            intent = com.kairo.assistant.nlu.models.IntentType.TOGGLE_WIFI,
            target = "on",
            extra = null,
            confidence = 0.95f
        )

        val result = executor.execute(command, context)
        assertTrue(result.success)
        assertEquals("Wi-Fi is now turned on.", result.message)
        verify(wifiManager).setWifiEnabled(true)
    }

    @Test
    fun testToggleWifiProgrammaticSuccessOff() {
        `when`(wifiManager.setWifiEnabled(false)).thenReturn(true)

        val command = ParsedCommand(
            intent = com.kairo.assistant.nlu.models.IntentType.TOGGLE_WIFI,
            target = "off",
            extra = null,
            confidence = 0.95f
        )

        val result = executor.execute(command, context)
        assertTrue(result.success)
        assertEquals("Wi-Fi is now turned off.", result.message)
        verify(wifiManager).setWifiEnabled(false)
    }

    @Test
    fun testToggleWifiFallbackToSettings() {
        // Mock setWifiEnabled to fail (return false), simulating Android 10+ restriction
        `when`(wifiManager.setWifiEnabled(anyBoolean())).thenReturn(false)

        val command = ParsedCommand(
            intent = com.kairo.assistant.nlu.models.IntentType.TOGGLE_WIFI,
            target = "on",
            extra = null,
            confidence = 0.95f
        )

        val result = executor.execute(command, context)
        assertTrue(result.success)
        assertTrue(result.message.contains("Opening Wi-Fi settings"))
        verify(context).startActivity(any())
    }
}
