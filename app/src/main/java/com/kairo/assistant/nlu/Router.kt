package com.kairo.assistant.nlu

import android.util.Log
import com.kairo.assistant.nlu.llm.LlmParser
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand
import com.kairo.assistant.nlu.rules.AgentIntentMatcher
import com.kairo.assistant.nlu.rules.ScreenAwareIntentMatcher
import com.kairo.assistant.automation.AutomationIntentMatcher

/**
 * Top-level command router that delegates to matchers in priority order:
 *
 * 1. Screen-aware matcher (highest priority — "what's on my screen?")
 * 2. Agent matcher ("do X for me", multi-step commands)
 * 3. Rule-based parser (17 fast intent matchers)
 * 4. LLM fallback (on-device LLaMA for complex/conversational)
 */
class CommandRouter(
    private val ruleParser: RuleBasedParser,
    private val llmParser: LlmParser? = null,
    private val geminiApiKey: String? = null,
    private val geminiModel: String? = null
) {

    companion object {
        private const val TAG = "CommandRouter"
    }

    /**
     * Parses a voice transcript into a [ParsedCommand].
     *
     * Flow: Screen matcher → Agent matcher → Automation matcher → Rule parser → LLM → fallback
     */
    suspend fun parse(transcript: String): ParsedCommand {
        // 1. Check for screen-aware queries ("what's on my screen?", "explain this")
        val screenMatch = ScreenAwareIntentMatcher.match(transcript)
        if (screenMatch != null) {
            Log.d(TAG, "Screen-aware match: ${screenMatch.intent}")
            return screenMatch
        }

        // 2. Check for agent tasks ("send WhatsApp to Mom", multi-step commands)
        val agentMatch = AgentIntentMatcher.match(transcript)
        if (agentMatch != null) {
            Log.d(TAG, "Agent task match: ${agentMatch.intent}")
            return agentMatch
        }

        // 3. Check for automation ("when X, do Y") — requires Gemini API
        if (geminiApiKey != null && geminiModel != null) {
            val automationMatch = AutomationIntentMatcher.match(transcript, geminiApiKey, geminiModel)
            if (automationMatch != null) {
                Log.d(TAG, "Automation match: ${automationMatch.intent}")
                return automationMatch
            }
        }

        // 4. Rule-based parser (fast intent matching)
        val ruleResult = ruleParser.tryMatch(transcript)

        // High-confidence rule match — return immediately
        if (ruleResult.confidence > 0.75f) {
            Log.d(TAG, "Rule-based match: ${ruleResult.intent} (conf=${ruleResult.confidence})")
            return ruleResult
        }

        // 4. Low confidence or UNKNOWN — try LLM if available
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

