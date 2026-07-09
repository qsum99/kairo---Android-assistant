package com.kairo.assistant.nlu.llm

import android.content.Context
import android.util.Log
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

/**
 * LLM-based parser for intent classification and conversational responses.
 *
 * Uses [LlamaEngine] to run on-device LLaMA inference when the rule-based
 * parser cannot confidently classify a command.
 */
class LlmParser(private val context: Context) {

    companion object {
        private const val TAG = "LlmParser"
    }

    /** Whether the LLM engine is ready for inference and enabled in settings. */
    val isAvailable: Boolean get() {
        val prefs = context.getSharedPreferences("kairo_prefs", Context.MODE_PRIVATE)
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val isLowRam = memoryInfo.totalMem < 4831838208L // 4.5 GB
        
        val isFallbackEnabled = prefs.getBoolean("llm_fallback_enabled", !isLowRam)
        return isFallbackEnabled && LlamaEngine.isAvailable
    }

    /**
     * Parses a transcript using the on-device LLM.
     *
     * @param transcript The raw voice transcript.
     * @return A [ParsedCommand] classified by the LLM, or UNKNOWN if unavailable.
     */
    suspend fun parse(transcript: String): ParsedCommand {
        if (!isAvailable) {
            Log.w(TAG, "LLM not available, returning UNKNOWN")
            return ParsedCommand(
                intent = IntentType.UNKNOWN,
                target = null,
                extra = transcript,
                confidence = 0.0f
            )
        }

        Log.d(TAG, "Classifying via LLM: $transcript")
        return LlamaEngine.classify(transcript)
    }
}

