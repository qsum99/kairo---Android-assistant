package com.kairo.assistant.wakeword

import android.content.Context
import android.util.Log
import com.rementia.openwakeword.lib.WakeWordEngine
import com.rementia.openwakeword.lib.model.DetectionMode
import com.rementia.openwakeword.lib.model.WakeWordModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Wake word detector using the offline OpenWakeWord ONNX Runtime library.
 * Processes audio locally without internet connection.
 */
class WakeWordDetector(private val context: Context) {

    companion object {
        private const val TAG = "Detector"
        private const val WAKE_WORD_THRESHOLD = 0.85f // Raised to 0.85f to filter out low/moderate confidence speech triggers
        private const val LOG_FILE_NAME = "wakeword_logs.txt"
    }

    fun interface OnWakeWordDetectedListener {
        fun onWakeWordDetected(transcript: String)
    }

    private var engine: WakeWordEngine? = null
    private var listener: OnWakeWordDetectedListener? = null
    private var isRunning = false
    private var coroutineScope: CoroutineScope? = null
    private var collectJob: Job? = null
    private var scoresJob: Job? = null

    fun setOnWakeWordDetectedListener(listener: OnWakeWordDetectedListener) {
        this.listener = listener
    }

    private fun logToFile(message: String, throwable: Throwable? = null) {
        try {
            val file = java.io.File(context.filesDir, "wakeword_logs.txt")
            val logText = "[${java.util.Date()}] [Detector] $message\n" + 
                    (throwable?.stackTraceToString() ?: "") + "\n"
            file.appendText(logText)
            Log.d(TAG, "Log written: $message")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log to file", e)
        }
    }

    /**
     * Start continuous wake-word listening.
     */
    fun start() {
        logToFile("Detector start() requested. isRunning = $isRunning")
        if (isRunning) {
            Log.d(TAG, "Already running — ignoring duplicate start()")
            return
        }

        // 1. Check microphone permission
        val hasPermission = android.content.pm.PackageManager.PERMISSION_GRANTED ==
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.RECORD_AUDIO
                )
        logToFile("Microphone permission granted = $hasPermission")
        if (!hasPermission) {
            logToFile("CRITICAL: Cannot start wake word detection without RECORD_AUDIO permission!")
            return
        }

        // 2. Verify asset files exist
        val requiredAssets = listOf("melspectrogram.onnx", "embedding_model.onnx", "kairo.onnx")
        for (assetName in requiredAssets) {
            try {
                context.assets.open(assetName).use { 
                    logToFile("Asset check: $assetName found.")
                }
            } catch (e: Exception) {
                logToFile("CRITICAL ERROR: Asset file '$assetName' is missing or unreadable!", e)
                return
            }
        }

        isRunning = true
        coroutineScope = CoroutineScope(Dispatchers.Default)

        val prefs = context.getSharedPreferences("kairo_prefs", Context.MODE_PRIVATE)
        val dynamicThreshold = prefs.getFloat("wakeword_threshold", 0.90f)

        // Initialize the offline OpenWakeWord ONNX pipeline with the placeholder model
        val models = listOf(
            WakeWordModel(
                name = "Kairo",
                modelPath = "kairo.onnx", // Uses assets/kairo.onnx
                threshold = dynamicThreshold
            )
        )

        try {
            logToFile("Initializing WakeWordEngine...")
            engine = WakeWordEngine(
                context = context,
                models = models,
                detectionMode = DetectionMode.SINGLE_BEST,
                detectionCooldownMs = 2000L,
                scope = coroutineScope!!
            )

            // Start processing audio frames
            logToFile("Starting WakeWordEngine...")
            val startTime = System.currentTimeMillis()
            engine?.start()
            logToFile("OpenWakeWord engine started successfully")
 
            // Collect detections asynchronously
            collectJob = coroutineScope?.launch {
                try {
                    engine?.detections?.collect { detection ->
                        val elapsed = System.currentTimeMillis() - startTime
                        if (elapsed < 2000L) {
                            logToFile("Ignoring startup warm-up detection (elapsed: ${elapsed}ms): Score = ${detection.score}")
                            return@collect
                        }
                        logToFile("🎤 Wake word detected! Model: ${detection.model.name}, Score: ${detection.score}")
                        listener?.onWakeWordDetected(detection.model.name)
                    }
                } catch (e: Exception) {
                    logToFile("Error inside detections flow collector", e)
                }
            }
 
            // Collect raw scores flow for debug diagnostics
            scoresJob = coroutineScope?.launch {
                try {
                    engine?.scores?.collect { score ->
                        val elapsed = System.currentTimeMillis() - startTime
                        if (elapsed < 2000L) return@collect // Skip logging during warm-up
                        if (score.score > 0.005f) {
                            logToFile("Score update: ${score.model.name} confidence = ${score.score}")
                        }
                    }
                } catch (e: Exception) {
                    logToFile("Error inside scores flow collector", e)
                }
            }

        } catch (e: Throwable) {
            logToFile("CRITICAL: Failed to initialize OpenWakeWord engine", e)
            stop()
        }
    }

    /**
     * Stop listening and release resources.
     */
    fun stop() {
        logToFile("Detector stop() requested. isRunning = $isRunning")
        isRunning = false
        collectJob?.cancel()
        collectJob = null
        scoresJob?.cancel()
        scoresJob = null
        try {
            engine?.release()
        } catch (e: Exception) {
            logToFile("Error releasing engine", e)
            Log.w(TAG, "Error releasing engine", e)
        }
        engine = null
        coroutineScope?.cancel()
        coroutineScope = null
        logToFile("Stopped and released OpenWakeWord engine")
    }

    fun isRunning(): Boolean = isRunning
}
