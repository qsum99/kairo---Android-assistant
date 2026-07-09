package com.kairo.assistant.nlu.rules

import com.kairo.assistant.nlu.IntentMatcher
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

class SettingsIntentMatcher : IntentMatcher {

    override val intentType: IntentType = IntentType.OPEN_SETTINGS

    companion object {
        private val PATTERN = Regex(
            """\b(?:open|open\s+up|go\s+to|launch|show|start|access|view|display)\s+(?:the\s+|my\s+)?(?:settings|system\s+settings|control\s+panel|preferences)\b|^(?:settings|system\s+settings|preferences)$""",
            RegexOption.IGNORE_CASE
        )
    }

    override fun tryMatch(transcript: String): ParsedCommand? {
        val input = transcript.trim()
        PATTERN.find(input) ?: return null

        return ParsedCommand(
            intent = intentType,
            target = "settings",
            extra = null,
            confidence = 0.9f
        )
    }
}
