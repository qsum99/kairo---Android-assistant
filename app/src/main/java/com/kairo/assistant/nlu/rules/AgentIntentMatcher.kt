package com.kairo.assistant.nlu.rules

import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

/**
 * Intent matcher for autonomous agent tasks.
 * Detects when the user wants Kairo to perform multi-step automation.
 */
object AgentIntentMatcher {

    private val AGENT_TRIGGER_PATTERNS = listOf(
        "agent",
        "do this for me",
        "do that for me",
        "automate",
        "auto do",
    )

    private val AGENT_APP_PATTERNS = listOf(
        "open (\\w+) and (.+)" to "APP_AND_ACTION",
        "go to (\\w+) and (.+)" to "APP_AND_ACTION",
        "send (?:a )?whatsapp (?:message )?to (.+?) (?:saying|that) (.+)" to "WHATSAPP_MSG",
        "send (.+?) a whatsapp (?:saying|that) (.+)" to "WHATSAPP_MSG",
        "order (.+) (?:from|on) (.+)" to "ORDER",
        "search (?:for )?(.+) (?:on|in) (.+)" to "SEARCH_IN_APP",
        "play (.+) on (.+)" to "PLAY_IN_APP",
    )

    // Multi-step conjunctions
    private val MULTI_STEP_MARKERS = listOf(
        " and then ",
        " then ",
        " after that ",
        " also ",
        " and also ",
        " next ",
        ", then ",
    )

    /**
     * Try to match the transcript as an agent task.
     * Returns null if no match.
     */
    fun match(transcript: String): ParsedCommand? {
        val lower = transcript.lowercase().trim()

        // Check explicit agent triggers
        for (trigger in AGENT_TRIGGER_PATTERNS) {
            if (lower.startsWith(trigger) || lower.contains("kairo $trigger")) {
                return ParsedCommand(
                    intent = IntentType.AGENT_TASK,
                    target = null,
                    extra = transcript,
                    confidence = 0.9f
                )
            }
        }

        // Check app-specific patterns (regex)
        for ((pattern, _) in AGENT_APP_PATTERNS) {
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            if (regex.containsMatchIn(lower)) {
                return ParsedCommand(
                    intent = IntentType.AGENT_TASK,
                    target = null,
                    extra = transcript,
                    confidence = 0.85f
                )
            }
        }

        // Check for multi-step commands (contains conjunctions)
        for (marker in MULTI_STEP_MARKERS) {
            if (lower.contains(marker)) {
                // Only trigger agent if both parts look like actions
                val parts = lower.split(marker)
                if (parts.size >= 2 && parts.all { it.length > 5 }) {
                    return ParsedCommand(
                        intent = IntentType.AGENT_TASK,
                        target = null,
                        extra = transcript,
                        confidence = 0.75f
                    )
                }
            }
        }

        return null
    }
}
