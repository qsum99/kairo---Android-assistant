package com.kairo.assistant.actions

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import com.kairo.assistant.nlu.models.ParsedCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.eq

class LockDeviceExecutorTest {

    private lateinit var context: Context
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var executor: LockDeviceExecutor

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        devicePolicyManager = mock(DevicePolicyManager::class.java)
        executor = LockDeviceExecutor()

        `when`(context.getSystemService(Context.DEVICE_POLICY_SERVICE)).thenReturn(devicePolicyManager)
    }

    @Test
    fun testLockDeviceSuccessWhenAdminActive() {
        `when`(devicePolicyManager.isAdminActive(any(ComponentName::class.java))).thenReturn(true)

        val command = ParsedCommand(
            intent = com.kairo.assistant.nlu.models.IntentType.LOCK_DEVICE,
            target = null,
            extra = null,
            confidence = 0.95f
        )

        val result = executor.execute(command, context)
        assertTrue(result.success)
        assertEquals("Locking screen", result.message)
        verify(devicePolicyManager).lockNow()
    }

    @Test
    fun testLockDeviceRequestAdminWhenNotActive() {
        `when`(devicePolicyManager.isAdminActive(any(ComponentName::class.java))).thenReturn(false)

        val command = ParsedCommand(
            intent = com.kairo.assistant.nlu.models.IntentType.LOCK_DEVICE,
            target = null,
            extra = null,
            confidence = 0.95f
        )

        val result = executor.execute(command, context)
        assertFalse(result.success)
        assertTrue(result.message.contains("Device Administrator permission is required"))
        verify(context).startActivity(any())
    }

    @Test
    fun testDevicePolicyManagerNotSupported() {
        `when`(context.getSystemService(Context.DEVICE_POLICY_SERVICE)).thenReturn(null)

        val command = ParsedCommand(
            intent = com.kairo.assistant.nlu.models.IntentType.LOCK_DEVICE,
            target = null,
            extra = null,
            confidence = 0.95f
        )

        val result = executor.execute(command, context)
        assertFalse(result.success)
        assertEquals("Device policy manager not supported.", result.message)
    }
}
