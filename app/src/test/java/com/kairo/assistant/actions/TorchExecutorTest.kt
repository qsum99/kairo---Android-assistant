package com.kairo.assistant.actions

import android.content.Context
import android.hardware.camera2.CameraManager
import com.kairo.assistant.nlu.models.ParsedCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.Mockito.eq

class TorchExecutorTest {

    private lateinit var context: Context
    private lateinit var cameraManager: CameraManager
    private lateinit var executor: TorchExecutor

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        cameraManager = mock(CameraManager::class.java)
        executor = TorchExecutor()

        `when`(context.getSystemService(Context.CAMERA_SERVICE)).thenReturn(cameraManager)
    }

    @Test
    fun testTorchOnSuccess() {
        `when`(cameraManager.cameraIdList).thenReturn(arrayOf("0"))

        val command = ParsedCommand(
            intent = com.kairo.assistant.nlu.models.IntentType.TOGGLE_TORCH,
            target = "on",
            extra = null,
            confidence = 0.95f
        )

        val result = executor.execute(command, context)
        assertTrue(result.success)
        assertEquals("Turning on flashlight", result.message)
        verify(cameraManager).setTorchMode(eq("0"), eq(true))
    }

    @Test
    fun testTorchOffSuccess() {
        `when`(cameraManager.cameraIdList).thenReturn(arrayOf("0"))

        val command = ParsedCommand(
            intent = com.kairo.assistant.nlu.models.IntentType.TOGGLE_TORCH,
            target = "off",
            extra = null,
            confidence = 0.95f
        )

        val result = executor.execute(command, context)
        assertTrue(result.success)
        assertEquals("Turning off flashlight", result.message)
        verify(cameraManager).setTorchMode(eq("0"), eq(false))
    }

    @Test
    fun testNoCameraFound() {
        `when`(cameraManager.cameraIdList).thenReturn(emptyArray())

        val command = ParsedCommand(
            intent = com.kairo.assistant.nlu.models.IntentType.TOGGLE_TORCH,
            target = "on",
            extra = null,
            confidence = 0.95f
        )

        val result = executor.execute(command, context)
        assertFalse(result.success)
        assertEquals("Camera not found on this device.", result.message)
    }

    @Test
    fun testCameraServiceNotAvailable() {
        `when`(context.getSystemService(Context.CAMERA_SERVICE)).thenReturn(null)

        val command = ParsedCommand(
            intent = com.kairo.assistant.nlu.models.IntentType.TOGGLE_TORCH,
            target = "on",
            extra = null,
            confidence = 0.95f
        )

        val result = executor.execute(command, context)
        assertFalse(result.success)
        assertEquals("Flashlight not supported on this device.", result.message)
    }
}
