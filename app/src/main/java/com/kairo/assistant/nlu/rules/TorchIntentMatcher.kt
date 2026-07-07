package com.kairo.assistant.nlu.rules

import com.kairo.assistant.nlu.IntentMatcher
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

class TorchIntentMatcher : IntentMatcher {

    override val intentType: IntentType = IntentType.TOGGLE_TORCH

    companion object {
        private val PATTERN = Regex(
            """^(?:turn\s+)?(?:on|off|enable|disable)\s+(?:the\s+)?(?:torch|flashlight)$|^(?:torch|flashlight)\s+(?:on|off)$""",
            RegexOption.IGNORE_CASE
        )
    }

    override fun tryMatch(transcript: String): ParsedCommand? {
        val input = transcript.trim().lowercase()
        if (!PATTERN.matches(input)) return null

        val action = if (input.contains("on") || input.contains("enable")) "on" else "off"

        return ParsedCommand(
            intent = intentType,
            target = action,
            extra = action,
            confidence = 0.95f
        )
    }
}
