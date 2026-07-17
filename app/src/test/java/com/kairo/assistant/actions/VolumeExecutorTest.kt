package com.kairo.assistant.actions

import android.content.Context
import android.media.AudioManager
import com.kairo.assistant.nlu.models.IntentType
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

class VolumeExecutorTest {

    private lateinit var context: Context
    private lateinit var audioManager: AudioManager
    private lateinit var executor: VolumeExecutor

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        audioManager = mock(AudioManager::class.java)
        executor = VolumeExecutor()
        
        `when`(context.getSystemService(Context.AUDIO_SERVICE)).thenReturn(audioManager)
    }

    @Test
    fun testSetVolumeSuccess() {
        `when`(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).thenReturn(15)

        val command = ParsedCommand(
            intent = IntentType.SET_VOLUME,
            target = "80", // 80%
            extra = null,
            confidence = 0.95f
        )

        val result = executor.execute(command, context)
        assertTrue(result.success)
        assertEquals("Volume set to 80%.", result.message)

        // 80% of max volume (15) is 12
        verify(audioManager).setStreamVolume(
            eq(AudioManager.STREAM_MUSIC),
            eq(12),
            eq(AudioManager.FLAG_SHOW_UI)
        )
    }

    @Test
    fun testVolumeUpSuccess() {
        val command = ParsedCommand(
            intent = IntentType.VOLUME_UP,
            target = "up",
            extra = null,
            confidence = 0.95f
        )

        val result = executor.execute(command, context)
        assertTrue(result.success)
        assertEquals("Turning volume up.", result.message)

        verify(audioManager).adjustStreamVolume(
            eq(AudioManager.STREAM_MUSIC),
            eq(AudioManager.ADJUST_RAISE),
            eq(AudioManager.FLAG_SHOW_UI)
        )
    }

    @Test
    fun testVolumeDownSuccess() {
        val command = ParsedCommand(
            intent = IntentType.VOLUME_DOWN,
            target = "down",
            extra = null,
            confidence = 0.95f
        )

        val result = executor.execute(command, context)
        assertTrue(result.success)
        assertEquals("Turning volume down.", result.message)

        verify(audioManager).adjustStreamVolume(
            eq(AudioManager.STREAM_MUSIC),
            eq(AudioManager.ADJUST_LOWER),
            eq(AudioManager.FLAG_SHOW_UI)
        )
    }

    @Test
    fun testUnsupportedCommand() {
        val command = ParsedCommand(
            intent = IntentType.OPEN_APP, // Not a volume command
            target = "YouTube",
            extra = null,
            confidence = 0.95f
        )

        val result = executor.execute(command, context)
        assertFalse(result.success)
        assertEquals("Unsupported volume command.", result.message)
    }

    @Test
    fun testAudioManagerNotAvailable() {
        `when`(context.getSystemService(Context.AUDIO_SERVICE)).thenReturn(null)

        val command = ParsedCommand(
            intent = IntentType.VOLUME_UP,
            target = "up",
            extra = null,
            confidence = 0.95f
        )

        val result = executor.execute(command, context)
        assertFalse(result.success)
        assertEquals("Audio service not available.", result.message)
    }
}
