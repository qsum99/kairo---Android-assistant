package com.kairo.assistant.nlu.llm

import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

/**
 * Placeholder LLM-based parser for future integration.
 * Currently returns UNKNOWN for all transcripts.
 */
class LlmParser {

    /**
     * Attempts to parse the transcript using an LLM.
     * Currently a stub that always returns UNKNOWN.
     */
    fun parse(transcript: String): ParsedCommand {
        // TODO: Integrate with a local or remote LLM for advanced intent parsing
        return ParsedCommand(
            intent = IntentType.UNKNOWN,
            target = null,
            extra = transcript,
            confidence = 0.0f
        )
    }
}
