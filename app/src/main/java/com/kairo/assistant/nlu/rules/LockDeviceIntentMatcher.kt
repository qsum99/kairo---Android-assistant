package com.kairo.assistant.nlu.rules

import com.kairo.assistant.nlu.IntentMatcher
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

class LockDeviceIntentMatcher : IntentMatcher {

    override val intentType: IntentType = IntentType.LOCK_DEVICE

    companion object {
        private val PATTERN = Regex(
            """\b(?:lock|secure)\s+(?:the\s+|my\s+)?(?:screen|device|phone)\b""",
            RegexOption.IGNORE_CASE
        )
    }

    override fun tryMatch(transcript: String): ParsedCommand? {
        val input = transcript.trim()
        PATTERN.find(input) ?: return null

        return ParsedCommand(
            intent = intentType,
            target = null,
            extra = null,
            confidence = 0.95f
        )
    }
}
