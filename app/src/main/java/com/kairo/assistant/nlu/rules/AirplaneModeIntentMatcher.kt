package com.kairo.assistant.nlu.rules

import com.kairo.assistant.nlu.IntentMatcher
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

class AirplaneModeIntentMatcher : IntentMatcher {

    override val intentType: IntentType = IntentType.TOGGLE_AIRPLANE

    companion object {
        private val PATTERN = Regex(
            """(?:\b(?:turn\s+|switch\s+)?(?:on|off|enable|disable|toggle|activate|deactivate)\b.*\b(?:airplane|aeroplane)\s+mode\b)|\b(?:airplane|aeroplane)\s+mode\b.*\b(?:on|off|enable|disable|toggle|activate|deactivate)\b""",
            RegexOption.IGNORE_CASE
        )
    }

    override fun tryMatch(transcript: String): ParsedCommand? {
        val input = transcript.trim()
        val match = PATTERN.find(input) ?: return null

        val action = when {
            input.contains(Regex("\\b(?:on|enable|activate)\\b", RegexOption.IGNORE_CASE)) -> "on"
            input.contains(Regex("\\b(?:off|disable|deactivate)\\b", RegexOption.IGNORE_CASE)) -> "off"
            else -> "toggle"
        }

        return ParsedCommand(
            intent = intentType,
            target = action,
            extra = null,
            confidence = 0.95f
        )
    }
}
