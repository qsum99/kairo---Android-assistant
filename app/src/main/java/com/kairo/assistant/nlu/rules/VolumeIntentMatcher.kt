package com.kairo.assistant.nlu.rules

import com.kairo.assistant.nlu.IntentMatcher
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

class VolumeIntentMatcher : IntentMatcher {

    override val intentType: IntentType = IntentType.UNKNOWN // Handled dynamically in tryMatch

    companion object {
        private val VOLUME_UP_PATTERN = Regex(
            """\b(?:volume\s+up|incre[a]?se\s+(?:the\s+)?volume|louder|raise\s+(?:the\s+)?volume|make\s+it\s+louder|up\s+(?:the\s+)?volume)\b""",
            RegexOption.IGNORE_CASE
        )
        private val VOLUME_DOWN_PATTERN = Regex(
            """\b(?:volume\s+down|decr[e]?a?se\s+(?:the\s+)?volume|lower\s+(?:the\s+)?volume|down\s+(?:the\s+)?volume|quieter|make\s+it\s+quieter|reduce\s+(?:the\s+)?volume)\b""",
            RegexOption.IGNORE_CASE
        )
        private val SET_VOLUME_PATTERN = Regex(
            """\b(?:set|turn|change|reduce|incre[a]?se|lower|put|reduse|incress)\s+(?:the\s+)?volume\s+(?:to\s+)?(\d{1,3})\s*%?|\bvolume\s+(?:to\s+)?(\d{1,3})\s*%?|\b(?:set|turn|reduce|incre[a]?se|lower|put|reduse|incress)\s+(?:to\s+)?(\d{1,3})\s*%""",
            RegexOption.IGNORE_CASE
        )
    }

    override fun tryMatch(transcript: String): ParsedCommand? {
        val input = transcript.trim()

        val setMatch = SET_VOLUME_PATTERN.find(input)
        if (setMatch != null) {
            val valueStr = setMatch.groupValues.getOrNull(1)?.takeIf { it.isNotEmpty() }
                ?: setMatch.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
                ?: setMatch.groupValues.getOrNull(3)
            val percentage = valueStr?.toIntOrNull()
            if (percentage != null && percentage in 0..100) {
                return ParsedCommand(
                    intent = IntentType.SET_VOLUME,
                    target = percentage.toString(),
                    extra = null,
                    confidence = 0.95f
                )
            }
        }

        if (VOLUME_UP_PATTERN.matchEntire(input) != null || VOLUME_UP_PATTERN.find(input) != null) {
            return ParsedCommand(
                intent = IntentType.VOLUME_UP,
                target = "up",
                extra = null,
                confidence = 0.95f
            )
        }

        if (VOLUME_DOWN_PATTERN.matchEntire(input) != null || VOLUME_DOWN_PATTERN.find(input) != null) {
            return ParsedCommand(
                intent = IntentType.VOLUME_DOWN,
                target = "down",
                extra = null,
                confidence = 0.95f
            )
        }

        return null
    }
}
