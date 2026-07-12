package com.kairo.assistant.nlu.rules

import com.kairo.assistant.nlu.IntentMatcher
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

class BingSearchIntentMatcher : IntentMatcher {

    override val intentType: IntentType = IntentType.BING_SEARCH

    companion object {
        private val PATTERNS = listOf(
            Regex("""^(?:bing\s+app|bing)\s+(.+)""", RegexOption.IGNORE_CASE),
            Regex("""^search\s+(?:on\s+)?(?:bing\s+app|bing)\s+(?:for\s+)?(.+)""", RegexOption.IGNORE_CASE),
            Regex("""^search\s+(?:for\s+)?(.+?)\s+on\s+(?:bing\s+app|bing)""", RegexOption.IGNORE_CASE),
            Regex("""^search\s+(?:for\s+)?(.+?)\s+(?:bing\s+app|bing)""", RegexOption.IGNORE_CASE)
        )
    }

    override fun tryMatch(transcript: String): ParsedCommand? {
        val input = transcript.trim()
        
        if (input.equals("bing", ignoreCase = true) || input.equals("bing app", ignoreCase = true)) {
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
