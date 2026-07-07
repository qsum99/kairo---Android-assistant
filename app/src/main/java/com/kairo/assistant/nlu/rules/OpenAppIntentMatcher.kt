package com.kairo.assistant.nlu.rules

import com.kairo.assistant.data.AppResolver
import com.kairo.assistant.nlu.IntentMatcher
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

/**
 * Matches app-launch intents such as:
 *   "open YouTube", "launch Settings", "start Spotify",
 *   "fire up Chrome", "run Calculator"
 */
class OpenAppIntentMatcher(
    private val appResolver: AppResolver
) : IntentMatcher {

    override val intentType: IntentType = IntentType.OPEN_APP

    companion object {
        // "open/launch/start/run X" or "fire up X"
        private val PATTERN = Regex(
            """(?:open|launch|start|run|fire\s+up)\s+(.+)""",
            RegexOption.IGNORE_CASE
        )
    }

    override fun tryMatch(transcript: String): ParsedCommand? {
        val input = transcript.trim()

        val match = PATTERN.find(input) ?: return null
        val spokenApp = match.groupValues[1].trim()
        if (spokenApp.isEmpty()) return null

        val resolved = appResolver.resolve(spokenApp)
            ?: return ParsedCommand(
                intent = IntentType.OPEN_APP,
                target = spokenApp,
                extra = null,
                confidence = 0.5f
            )

        return ParsedCommand(
            intent = IntentType.OPEN_APP,
            target = resolved.first,    // app label
            extra = resolved.second,    // package name
            confidence = 0.9f
        )
    }
}
