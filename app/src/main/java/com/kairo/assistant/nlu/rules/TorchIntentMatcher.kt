package com.kairo.assistant.nlu.rules

import com.kairo.assistant.nlu.IntentMatcher
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

class TorchIntentMatcher : IntentMatcher {

    override val intentType: IntentType = IntentType.TOGGLE_TORCH

    companion object {
        private val PATTERN = Regex(
            """(?:\b(?:turn\s+|switch\s+)?(?:on|off|enable|disable|toggle|activate|deactivate|start|stop)\b.*\b(?:torch|flashlight|flash\s*light|light|flash)\b)|\b(?:torch|flashlight|flash\s*light|light|flash)\b.*\b(?:on|off|enable|disable|toggle|activate|deactivate|start|stop)\b""",
            RegexOption.IGNORE_CASE
        )
    }

    override fun tryMatch(transcript: String): ParsedCommand? {
        val input = transcript.trim()
        val match = PATTERN.find(input) ?: return null

        val action = if (input.contains("off", ignoreCase = true) || 
            input.contains("disable", ignoreCase = true) || 
            input.contains("deactivate", ignoreCase = true) ||
            input.contains("stop", ignoreCase = true)) "off" else "on"

        return ParsedCommand(
            intent = intentType,
            target = action,
            extra = action,
            confidence = 0.95f
        )
    }
}
