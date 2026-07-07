package com.kairo.assistant.nlu

import com.kairo.assistant.nlu.models.ParsedCommand

/**
 * Top-level command router that delegates to the rule-based parser.
 *
 * If the rule-based result has confidence > 0.75, it is returned immediately.
 * Otherwise the result is returned as-is (future: could fall through to LLM).
 */
class CommandRouter(
    private val ruleParser: RuleBasedParser
) {

    /**
     * Parses a voice transcript into a [ParsedCommand].
     *
     * @param transcript The raw voice transcript.
     * @return A [ParsedCommand] with the best available interpretation.
     */
    fun parse(transcript: String): ParsedCommand {
        val ruleResult = ruleParser.tryMatch(transcript)

        // High-confidence rule match — return immediately
        if (ruleResult.confidence > 0.75f) {
            return ruleResult
        }

        // Low confidence or UNKNOWN — return as-is for now
        // TODO: Fall through to LlmParser when integrated
        return ruleResult
    }
}
