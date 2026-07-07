package com.kairo.assistant.nlu

import android.util.Log
import com.kairo.assistant.nlu.llm.LlmParser
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

/**
 * Top-level command router that delegates to the rule-based parser first,
 * then falls through to the LLM-based parser for unrecognized commands.
 *
 * If the rule-based result has confidence > 0.75, it is returned immediately.
 * Otherwise, the LLM parser is used for intent classification or conversational response.
 */
class CommandRouter(
    private val ruleParser: RuleBasedParser,
    private val llmParser: LlmParser? = null
) {

    companion object {
        private const val TAG = "CommandRouter"
    }

    /**
     * Parses a voice transcript into a [ParsedCommand].
     *
     * Flow: Rule-based parser → (if low confidence) → LLM parser → fallback UNKNOWN
     *
     * @param transcript The raw voice transcript.
     * @return A [ParsedCommand] with the best available interpretation.
     */
    suspend fun parse(transcript: String): ParsedCommand {
        val ruleResult = ruleParser.tryMatch(transcript)

        // High-confidence rule match — return immediately
        if (ruleResult.confidence > 0.75f) {
            Log.d(TAG, "Rule-based match: ${ruleResult.intent} (conf=${ruleResult.confidence})")
            return ruleResult
        }

        // Low confidence or UNKNOWN — try LLM if available
        if (llmParser != null && llmParser.isAvailable) {
            Log.d(TAG, "Rule-based low confidence (${ruleResult.confidence}), falling through to LLM")
            val llmResult = llmParser.parse(transcript)

            // Use LLM result if it's more confident or provides a conversation response
            if (llmResult.intent != IntentType.UNKNOWN || llmResult.confidence > ruleResult.confidence) {
                Log.d(TAG, "LLM result: ${llmResult.intent} (conf=${llmResult.confidence})")
                return llmResult
            }
        }

        // Fallback: return rule-based result (even if low confidence)
        Log.d(TAG, "Returning rule-based fallback: ${ruleResult.intent}")
        return ruleResult
    }
}

