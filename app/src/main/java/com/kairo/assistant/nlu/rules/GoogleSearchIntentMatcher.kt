package com.kairo.assistant.nlu.rules

import com.kairo.assistant.nlu.IntentMatcher
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

class GoogleSearchIntentMatcher : IntentMatcher {

    override val intentType: IntentType = IntentType.GOOGLE_SEARCH

    companion object {
        private val PATTERNS = listOf(
            Regex("""^google\s+(.+)""", RegexOption.IGNORE_CASE),
            Regex("""^search\s+google\s+(?:for\s+)?(.+)""", RegexOption.IGNORE_CASE),
            Regex("""^search\s+(?:for\s+)?(.+?)\s+on\s+google""", RegexOption.IGNORE_CASE),
            Regex("""^search\s+(?:for\s+)?(.+?)\s+google""", RegexOption.IGNORE_CASE)
        )
    }

    override fun tryMatch(transcript: String): ParsedCommand? {
        val input = transcript.trim()
        
        if (input.equals("google", ignoreCase = true)) {
            return ParsedCommand(
                intent = intentType,
                target = "",
                extra = "",
                confidence = 0.95f
            )
        }

        for (pattern in PATTERNS) {
            val match = pattern.find(input)
            if (match != null) {
                val query = match.groupValues[1].trim()
                if (query.isNotEmpty()) {
                    return ParsedCommand(
                        intent = intentType,
                        target = query,
                        extra = query,
                        confidence = 0.95f
                    )
                }
            }
        }

        return null
    }
}
