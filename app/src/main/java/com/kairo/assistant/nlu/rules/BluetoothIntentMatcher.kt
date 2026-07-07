package com.kairo.assistant.nlu.rules

import com.kairo.assistant.nlu.IntentMatcher
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

class BluetoothIntentMatcher : IntentMatcher {

    override val intentType: IntentType = IntentType.TOGGLE_BLUETOOTH

    companion object {
        private val PATTERN = Regex(
            """(?:turn\s+on|turn\s+off|enable|disable|toggle)\s+bluetooth""",
            RegexOption.IGNORE_CASE
        )
    }

    override fun tryMatch(transcript: String): ParsedCommand? {
        val input = transcript.trim()
        val match = PATTERN.find(input) ?: return null

        val action = when {
            input.contains("on", ignoreCase = true) || input.contains("enable", ignoreCase = true) -> "on"
            input.contains("off", ignoreCase = true) || input.contains("disable", ignoreCase = true) -> "off"
            else -> "toggle"
        }

        return ParsedCommand(
            intent = intentType,
            target = action,
            extra = null,
            confidence = 0.9f
        )
    }
}
