package com.kairo.assistant.nlu.rules

import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

/**
 * Intent matcher for screen-context queries.
 * Detects when the user wants to know about what's on their screen.
 */
object ScreenAwareIntentMatcher {

    private val SCREEN_QUERY_PATTERNS = listOf(
        "what's on my screen",
        "what is on my screen",
        "whats on my screen",
        "what do you see",
        "what can you see",
        "read my screen",
        "read this screen",
        "describe my screen",
        "describe this screen",
        "what's this",
        "what is this",
        "what does this say",
        "read this for me",
        "read this",
        "what's showing",
        "what is showing",
        "what am i looking at",
        "tell me what's on screen",
        "screen",
    )

    private val SCREEN_EXPLAIN_PATTERNS = listOf(
        "explain this",
        "explain what's on my screen",
        "help me with this",
        "help me understand this",
        "what does this mean",
        "what does this error mean",
        "explain this error",
        "what went wrong",
        "why is this happening",
        "how do i fix this",
        "what should i do",
    )

    /**
     * Try to match the transcript against screen-aware patterns.
     * Returns null if no match.
     */
    fun match(transcript: String): ParsedCommand? {
        val lower = transcript.lowercase().trim()

        // Check SCREEN_QUERY patterns
        for (pattern in SCREEN_QUERY_PATTERNS) {
            if (lower.contains(pattern)) {
                return ParsedCommand(
                    intent = IntentType.SCREEN_QUERY,
                    target = null,
                    extra = transcript,
                    confidence = 0.85f
                )
            }
        }

        // Check SCREEN_EXPLAIN patterns
        for (pattern in SCREEN_EXPLAIN_PATTERNS) {
            if (lower.contains(pattern)) {
                return ParsedCommand(
                    intent = IntentType.SCREEN_EXPLAIN,
                    target = null,
                    extra = transcript,
                    confidence = 0.85f
                )
            }
        }

        return null
    }
}
