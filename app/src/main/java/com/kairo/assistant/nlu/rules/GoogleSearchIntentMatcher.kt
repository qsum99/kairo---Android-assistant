package com.kairo.assistant.nlu.rules

import com.kairo.assistant.nlu.IntentMatcher
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

class GoogleSearchIntentMatcher : IntentMatcher {

    override val intentType: IntentType = IntentType.GOOGLE_SEARCH

    companion object {
        private val PATTERN = Regex(
            """^google(?:\s+(.+))?$""",
            RegexOption.IGNORE_CASE
        )
    }

    override fun tryMatch(transcript: String): ParsedCommand? {
        val input = transcript.trim()
        val match = PATTERN.find(input) ?: return null
        val query = match.groupValues.getOrNull(1)?.trim() ?: ""

        return ParsedCommand(
            intent = intentType,
            target = query,
            extra = query,
            confidence = 0.95f
        )
    }
}
