package com.kairo.assistant.nlu.rules

import com.kairo.assistant.nlu.IntentMatcher
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

class MediaIntentMatcher : IntentMatcher {

    override val intentType: IntentType = IntentType.UNKNOWN // Handled dynamically in tryMatch

    companion object {
        private val PLAY_PATTERN = Regex(
            """\b(?:play\s+music|resume\s+music|play\s+song|resume\s+song|resume|play|start\s+music|unpause\s*(?:music)?)\b""",
            RegexOption.IGNORE_CASE
        )
        private val PAUSE_PATTERN = Regex(
            """\b(?:pause\s+music|stop\s+music|pause\s+song|stop\s+song|pause|stop|halt\s+music)\b""",
            RegexOption.IGNORE_CASE
        )
    }

    override fun tryMatch(transcript: String): ParsedCommand? {
        val input = transcript.trim()

        if (PLAY_PATTERN.matchEntire(input) != null || PLAY_PATTERN.find(input) != null) {
            // Exclude video platforms if someone asks to play a video/youtube
            if (input.contains("youtube", ignoreCase = true) || input.contains("video", ignoreCase = true)) {
                return null
            }
            return ParsedCommand(
                intent = IntentType.MEDIA_PLAY,
                target = "play",
                extra = null,
                confidence = 0.9f
            )
        }

        if (PAUSE_PATTERN.matchEntire(input) != null || PAUSE_PATTERN.find(input) != null) {
            return ParsedCommand(
                intent = IntentType.MEDIA_PAUSE,
                target = "pause",
                extra = null,
                confidence = 0.9f
            )
        }

        return null
    }
}
