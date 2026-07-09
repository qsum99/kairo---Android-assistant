package com.kairo.assistant.nlu.rules

import com.kairo.assistant.nlu.IntentMatcher
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

class BluetoothIntentMatcher : IntentMatcher {

    override val intentType: IntentType = IntentType.TOGGLE_BLUETOOTH

    companion object {
        private val PATTERN = Regex(
            """(?:\b(?:turn\s+|switch\s+)?(?:on|off|enable|disable|toggle|activate|deactivate)\b.*\b(?:bluetooth|bt)\b)|\b(?:bluetooth|bt)\b.*\b(?:on|off|enable|disable|toggle|activate|deactivate)\b""",
            RegexOption.IGNORE_CASE
        )
    }

    override fun tryMatch(transcript: String): ParsedCommand? {
        val input = transcript.trim()
        val match = PATTERN.find(input) ?: return null

        val action = when {
            input.contains("on", ignoreCase = true) || 
            input.contains("enable", ignoreCase = true) ||
            input.contains("activate", ignoreCase = true) -> "on"
            
            input.contains("off", ignoreCase = true) || 
            input.contains("disable", ignoreCase = true) ||
            input.contains("deactivate", ignoreCase = true) -> "off"
            
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
