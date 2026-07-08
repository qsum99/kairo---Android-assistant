package com.kairo.assistant.nlu.llm

import android.content.Context
import android.util.Log
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.collect
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
    private const val MODEL_FILENAME = "kairo_model.gguf"

    // Dedicated single thread to prevent JNI threading crashes (SIGSEGV)
    private val llmDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private var llamaModel: org.codeshipping.llamakotlin.LlamaModel? = null
    private var _isLoading = false
    private var _loadError: String? = null
    private val inferenceMutex = Mutex()

    /** True if the model is loaded and ready for inference. */
    val isAvailable: Boolean get() = llamaModel?.isLoaded == true

    /** True if the model is currently being loaded. */
    val isLoading: Boolean get() = _isLoading

    /** Error message from the last load attempt, or null. */
    val loadError: String? get() = _loadError

    /**
     * Initialize the LLM engine.
     */
    suspend fun initialize(context: Context) {
        if (isAvailable || _isLoading) return

        _isLoading = true
        _loadError = null

        withContext(llmDispatcher) {
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
                    batchSize = 1
                    threads = 1
                    gpuLayers = 0
                }

                llamaModel = model
                Log.i(TAG, "Model loaded successfully [OK]")

            } catch (e: Exception) {
                _loadError = "Load failed: ${e.message}"
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
                val prompt = "User: $transcript\nAssistant:"
                val responseBuilder = StringBuilder()
                
                Log.d(TAG, "Running inference on dedicated thread...")

                model.generateStream(prompt).collect { token ->
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
