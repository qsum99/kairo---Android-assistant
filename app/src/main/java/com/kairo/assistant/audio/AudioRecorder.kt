package com.kairo.assistant.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AudioRecorder {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val TAG = "AudioRecorder"
    }

    @SuppressLint("MissingPermission")
    fun startRecording(): Flow<ShortArray> = flow {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size: $bufferSize")
            return@flow
        }

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            audioRecord?.release()
            audioRecord = null
            return@flow
        }

        audioRecord?.startRecording()
        isRecording = true
        Log.d(TAG, "Recording started")

        val buffer = ShortArray(bufferSize / 2)
        try {
            while (isRecording) {
                val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: break
                if (readCount > 0) {
                    emit(buffer.copyOf(readCount))
                }
            }
        } finally {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            Log.d(TAG, "Recording stopped and resources released")
        }
    }

    fun stopRecording() {
        isRecording = false
        audioRecord?.release()
        audioRecord = null
    }
}
