package com.kairo.assistant.nlu.llm

import android.content.Context
import android.util.Log
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Singleton engine for on-device LLaMA inference using llama.cpp.
 *
 * Manages the native model lifecycle and provides intent classification
 * and conversational response generation for the Kairo assistant.
 *
 * Usage:
 *   1. Call [initialize] with a Context on app start (loads model from internal storage).
 *   2. Call [classify] with a user transcript to get a ParsedCommand.
 *   3. Call [shutdown] when the app is destroyed.
 */
object LlamaEngine {

    private const val TAG = "LlamaEngine"

    /**
     * Expected filename of the GGUF model in the app's internal files directory.
     * Push the model via:
     *   adb push model.gguf /data/local/tmp/
     *   adb shell "run-as com.kairo.assistant.debug cp /data/local/tmp/model.gguf files/kairo_model.gguf"
     */
    private const val MODEL_FILENAME = "kairo_model.gguf"

    private var llamaModel: org.codeshipping.llamakotlin.LlamaModel? = null
    private var _isLoading = false
    private var _loadError: String? = null

    /** True if the model is loaded and ready for inference. */
    val isAvailable: Boolean get() = llamaModel?.isLoaded == true

    /** True if the model is currently being loaded. */
    val isLoading: Boolean get() = _isLoading

    /** Error message from the last load attempt, or null. */
    val loadError: String? get() = _loadError

    // System prompt matching the Python kairo_agent.py for consistent behavior
    private val SYSTEM_PROMPT = """
You are Kairo, a helpful voice assistant on an Android phone. You work fully offline.

Given a user's voice command, decide if it requires a DEVICE ACTION or a CONVERSATION response.

DEVICE ACTIONS (phone controls):
  - CALL: Make a phone call. Slots: contact_name
  - SEND_SMS: Send a text message. Slots: contact_name, message
  - SET_ALARM: Set an alarm. Slots: time
  - OPEN_APP: Open/launch an app. Slots: app_name
  - GOOGLE_SEARCH: Search Google. Slots: query
  - TOGGLE_TORCH: Toggle flashlight. Slots: state (on/off)
  - TOGGLE_BLUETOOTH: Toggle Bluetooth. Slots: state (on/off)
  - TOGGLE_WIFI: Toggle Wi-Fi. Slots: state (on/off)
  - TOGGLE_INTERNET: Toggle Internet/mobile data. Slots: state (on/off)
  - TOGGLE_AIRPLANE: Toggle Airplane Mode. Slots: state (on/off)
  - OPEN_SETTINGS: Open device settings. No slots.
  - LOCK_DEVICE: Lock the phone screen. No slots.

RULES:
1. If the user wants to control their phone, respond with DEVICE_ACTION.
2. If the user asks a question or wants to chat, respond with CONVERSATION.
3. Understand variations: "ring my mom", "phone dad", "dial home" = CALL.
4. For CONVERSATION: give a concise response (1-3 sentences, for spoken TTS).

RESPOND WITH ONLY THIS JSON, nothing else:
Device action: {"type": "DEVICE_ACTION", "intent": "INTENT_NAME", "slots": {"key": "value"}}
Conversation: {"type": "CONVERSATION", "response": "Your response"}
    """.trimIndent()

    /**
     * Initialize the LLM engine by loading the GGUF model from internal storage.
     *
     * @param context Application context (used to locate the model file).
     */
    suspend fun initialize(context: Context) {
        if (isAvailable || _isLoading) return

        _isLoading = true
        _loadError = null

        withContext(Dispatchers.IO) {
            try {
                val modelFile = java.io.File(context.filesDir, MODEL_FILENAME)

                if (!modelFile.exists()) {
                    _loadError = "Model file not found: ${modelFile.absolutePath}"
                    Log.w(TAG, _loadError!!)
                    Log.i(TAG, "Push a GGUF model via: adb push model.gguf /data/local/tmp/ && " +
                            "adb shell \"run-as ${context.packageName} cp /data/local/tmp/model.gguf files/$MODEL_FILENAME\"")
                    _isLoading = false
                    return@withContext
                }

                Log.i(TAG, "Loading model: ${modelFile.name} (${modelFile.length() / 1024 / 1024}MB)...")

                val model = org.codeshipping.llamakotlin.LlamaModel.load(modelFile.absolutePath) {
                    contextSize = 1024
                    batchSize = 256
                    threads = 4
                    temperature = 0.1f
                    topP = 0.9f
                    topK = 40
                    repeatPenalty = 1.1f
                    maxTokens = 200
                    gpuLayers = 0 // CPU-only for maximum compatibility
                }

                llamaModel = model
                Log.i(TAG, "Model loaded successfully [OK]")

            } catch (e: Exception) {
                _loadError = "Failed to load model: ${e.message}"
                Log.e(TAG, _loadError!!, e)
            } finally {
                _isLoading = false
            }
        }
    }

    /**
     * Classify a user transcript into an intent using the on-device LLM.
     *
     * @param transcript The user's voice transcript to classify.
     * @return A [ParsedCommand] with the classified intent and any extracted slots.
     */
    suspend fun classify(transcript: String): ParsedCommand {
        val model = llamaModel
        if (model == null || !model.isLoaded) {
            Log.w(TAG, "Model not available for classification")
            return ParsedCommand(
                intent = IntentType.UNKNOWN,
                target = null,
                extra = transcript,
                confidence = 0.0f
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                // Format the prompt using ChatML template (compatible with most GGUF models)
                val prompt = formatChatMLPrompt(SYSTEM_PROMPT, "User command: \"$transcript\"")

                Log.d(TAG, "Sending to LLM: $transcript")

                // Collect all tokens from the stream
                val tokens = model.generateStream(prompt).toList()
                val rawOutput = tokens.joinToString("").trim()

                Log.d(TAG, "LLM response: $rawOutput")

                parseLlmResponse(rawOutput, transcript)

            } catch (e: Exception) {
                Log.e(TAG, "LLM inference error", e)
                ParsedCommand(
                    intent = IntentType.UNKNOWN,
                    target = null,
                    extra = transcript,
                    confidence = 0.0f
                )
            }
        }
    }

    /**
     * Format a prompt using the ChatML template.
     */
    private fun formatChatMLPrompt(system: String, user: String): String {
        return buildString {
            append("<|im_start|>system\n")
            append(system)
            append("<|im_end|>\n")
            append("<|im_start|>user\n")
            append(user)
            append("<|im_end|>\n")
            append("<|im_start|>assistant\n")
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
