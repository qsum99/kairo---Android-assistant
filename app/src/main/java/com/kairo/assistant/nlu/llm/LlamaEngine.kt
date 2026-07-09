package com.kairo.assistant.nlu.llm

import android.content.Context
import android.util.Log
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Singleton engine for on-device LLaMA inference using llama.cpp.
 */
object LlamaEngine {

    private const val TAG = "LlamaEngine"
    private const val MODEL_FILENAME = "kairo_model_v6.gguf"

    // Dedicated single thread to prevent JNI threading crashes (SIGSEGV)
    private val llmDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private var llamaModel: org.codeshipping.llamakotlin.LlamaModel? = null
    private var _isLoading = false
    private var _loadError: String? = null
    private val inferenceMutex = Mutex()

    private var _downloadProgress = 0f
    private var _isDownloading = false
    private var _downloadError: String? = null

    val downloadProgressFlow = kotlinx.coroutines.flow.MutableStateFlow<Float?>(null)
    val isDownloading: Boolean get() = _isDownloading
    val downloadError: String? get() = _downloadError

    /** True if the model is loaded and ready for inference. */
    val isAvailable: Boolean get() = llamaModel?.isLoaded == true

    /** True if the model is currently being loaded. */
    val isLoading: Boolean get() = _isLoading

    /** Error message from the last load attempt, or null. */
    val loadError: String? get() = _loadError

    /**
     * Download the optimized small model in the background.
     */
    suspend fun downloadModel(context: Context): Boolean {
        if (_isDownloading) return false
        _isDownloading = true
        _downloadError = null
        downloadProgressFlow.value = 0f

        return withContext(Dispatchers.IO) {
            try {
                val tempFile = java.io.File(context.filesDir, "$MODEL_FILENAME.tmp")
                val finalFile = java.io.File(context.filesDir, MODEL_FILENAME)

                if (tempFile.exists()) tempFile.delete()

                Log.i(TAG, "Starting model download...")
                val url = java.net.URL("https://huggingface.co/afrideva/Llama-160M-Chat-v1-GGUF/resolve/main/llama-160m-chat-v1.q8_0.gguf")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                connection.instanceFollowRedirects = true
                connection.connect()

                if (connection.responseCode != java.net.HttpURLConnection.HTTP_OK) {
                    throw java.io.IOException("Server returned HTTP ${connection.responseCode}")
                }

                val fileLength = connection.contentLengthLong
                var totalBytesRead = 0L

                connection.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var lastProgressUpdate = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            if (fileLength > 0) {
                                val progress = totalBytesRead.toFloat() / fileLength
                                _downloadProgress = progress
                                val now = System.currentTimeMillis()
                                if (now - lastProgressUpdate > 100) {
                                    downloadProgressFlow.value = progress
                                    lastProgressUpdate = now
                                }
                            }
                        }
                    }
                }

                downloadProgressFlow.value = 1.0f

                if (tempFile.renameTo(finalFile)) {
                    Log.i(TAG, "Model downloaded successfully to ${finalFile.absolutePath}")
                    _isDownloading = false
                    downloadProgressFlow.value = null
                    
                    // Release download memory buffer spikes before loading
                    System.gc()
                    Runtime.getRuntime().gc()
                    kotlinx.coroutines.delay(3000)
                    System.gc()
                    
                    initialize(context)
                    true
                } else {
                    throw java.io.IOException("Failed to rename temporary model file")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Model download failed", e)
                _downloadError = "Download failed: ${e.message}"
                _isDownloading = false
                downloadProgressFlow.value = null
                false
            }
        }
    }

    /**
     * Initialize the LLM engine.
     */
    suspend fun initialize(context: Context) {
        if (isAvailable || _isLoading) return

        _isLoading = true
        _loadError = null

        withContext(llmDispatcher) {
            try {
                // Delete the old heavy models to free up space
                val oldModelFile1 = java.io.File(context.filesDir, "kairo_model.gguf")
                if (oldModelFile1.exists()) {
                    oldModelFile1.delete()
                    Log.i(TAG, "Deleted old large model file (kairo_model.gguf)")
                }
                val oldModelFile2 = java.io.File(context.filesDir, "kairo_model_v2.gguf")
                if (oldModelFile2.exists()) {
                    oldModelFile2.delete()
                    Log.i(TAG, "Deleted old v2 model file (kairo_model_v2.gguf)")
                }
                val oldModelFile3 = java.io.File(context.filesDir, "kairo_model_v3.gguf")
                if (oldModelFile3.exists()) {
                    oldModelFile3.delete()
                    Log.i(TAG, "Deleted old v3 model file (kairo_model_v3.gguf)")
                }
                val oldModelFile4 = java.io.File(context.filesDir, "kairo_model_v4.gguf")
                if (oldModelFile4.exists()) {
                    oldModelFile4.delete()
                    Log.i(TAG, "Deleted old v4 model file (kairo_model_v4.gguf)")
                }
                val oldModelFile5 = java.io.File(context.filesDir, "kairo_model_v5.gguf")
                if (oldModelFile5.exists()) {
                    oldModelFile5.delete()
                    Log.i(TAG, "Deleted old v5 model file (kairo_model_v5.gguf)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clean up old model files", e)
            }

            try {
                val modelFile = java.io.File(context.filesDir, MODEL_FILENAME)

                if (!modelFile.exists()) {
                    _loadError = "Model file not found"
                    _isLoading = false
                    return@withContext
                }

                Log.i(TAG, "Loading model on dedicated thread...")
                System.gc()

                val model = org.codeshipping.llamakotlin.LlamaModel.load(modelFile.absolutePath) {
                    contextSize = 512
                    batchSize = 512
                    threads = 2
                    gpuLayers = 0
                }

                llamaModel = model
                Log.i(TAG, "Model loaded successfully [OK]")

            } catch (e: Throwable) {
                _loadError = "Load failed: ${e.message ?: e.toString()}"
                Log.e(TAG, _loadError!!, e)
            } finally {
                _isLoading = false
            }
        }
    }

    /**
     * Classify a user transcript.
     */
    suspend fun classify(transcript: String): ParsedCommand = inferenceMutex.withLock {
        val model = llamaModel
        if (model == null || !model.isLoaded) {
            return ParsedCommand(IntentType.UNKNOWN, null, transcript, 0.0f)
        }

        return@withLock withContext(llmDispatcher) {
            try {
                val systemPrompt = "You are Kairo, a device action classifier. Choose from these intents: CALL, SMS, ALARM, WIFI, BLUETOOTH, INTERNET, AIRPLANE_MODE, SETTINGS, LOCK_DEVICE, TORCH, GOOGLE_SEARCH, BING_SEARCH. Output JSON only, e.g.:\n{\"type\":\"DEVICE_ACTION\",\"intent\":\"WIFI\",\"slots\":{\"state\":\"on\"}}\nor for chat:\n{\"type\":\"CONVERSATION\",\"response\":\"hello\"}"
                val prompt = "<|im_start|>system\n$systemPrompt<|im_end|>\n<|im_start|>user\n$transcript<|im_end|>\n<|im_start|>assistant\n"
                val responseBuilder = StringBuilder()
                
                Log.d(TAG, "Running inference on dedicated thread...")

                model.generateStream(prompt)
                    .flowOn(llmDispatcher)
                    .collect { token ->
                        responseBuilder.append(token)
                    }
                
                val rawOutput = responseBuilder.toString().trim()
                Log.d(TAG, "LLM response: $rawOutput")

                if (rawOutput.isEmpty()) {
                    return@withContext ParsedCommand(IntentType.CONVERSATION, null, "...", 0.5f)
                }

                parseLlmResponse(rawOutput, transcript)

            } catch (e: Exception) {
                Log.e(TAG, "Inference error", e)
                ParsedCommand(IntentType.UNKNOWN, null, transcript, 0.0f)
            }
        }
    }

    /**
     * Parse the LLM's JSON response into a [ParsedCommand].
     */
    private fun parseLlmResponse(raw: String, originalText: String): ParsedCommand {
        try {
            // Extract JSON from the response (LLM might wrap it in extra text)
            val jsonStart = raw.indexOf('{')
            val jsonEnd = raw.lastIndexOf('}')
            if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) {
                throw IllegalArgumentException("No JSON found in response")
            }

            val jsonStr = raw.substring(jsonStart, jsonEnd + 1)
            val data = JSONObject(jsonStr)
            val type = data.optString("type", "").uppercase()

            return when (type) {
                "DEVICE_ACTION" -> {
                    val intentStr = data.optString("intent", "UNKNOWN").uppercase()
                    val intent = try {
                        IntentType.valueOf(intentStr)
                    } catch (_: IllegalArgumentException) {
                        IntentType.UNKNOWN
                    }

                    // Extract slots
                    val slotsObj = data.optJSONObject("slots")
                    val target = slotsObj?.optString("contact_name")
                        ?: slotsObj?.optString("app_name")
                        ?: slotsObj?.optString("query")
                    val extra = slotsObj?.optString("time")
                        ?: slotsObj?.optString("duration")
                        ?: slotsObj?.optString("message")
                        ?: slotsObj?.optString("state")
                        ?: slotsObj?.optString("direction")

                    Log.d(TAG, "-> DEVICE: $intent | target=$target extra=$extra")

                    ParsedCommand(
                        intent = intent,
                        target = target,
                        extra = extra,
                        confidence = 0.75f
                    )
                }

                "CONVERSATION" -> {
                    val response = data.optString("response", "I'm not sure how to help with that.")
                    Log.d(TAG, "-> CHAT: ${response.take(80)}")

                    ParsedCommand(
                        intent = IntentType.CONVERSATION,
                        target = null,
                        extra = response,
                        confidence = 0.75f
                    )
                }

                else -> {
                    throw IllegalArgumentException("Unknown type: $type")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Parse error: ${e.message} | raw: ${raw.take(200)}")

            // Try to salvage something from the raw text as a conversational response
            val cleaned = raw.replace(Regex("[{}\\[\\]\"]"), "").trim()
            if (cleaned.length > 5) {
                return ParsedCommand(
                    intent = IntentType.CONVERSATION,
                    target = null,
                    extra = cleaned,
                    confidence = 0.4f
                )
            }

            return ParsedCommand(
                intent = IntentType.UNKNOWN,
                target = null,
                extra = originalText,
                confidence = 0.0f
            )
        }
    }

    /**
     * Release native model resources.
     */
    fun shutdown() {
        try {
            llamaModel?.close()
            llamaModel = null
            Log.i(TAG, "Model released")
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown", e)
        }
    }
}
