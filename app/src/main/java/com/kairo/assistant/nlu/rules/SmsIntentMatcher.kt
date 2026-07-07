package com.kairo.assistant.nlu.rules

import com.kairo.assistant.data.ContactResolver
import com.kairo.assistant.nlu.IntentMatcher
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

/**
 * Matches SMS-related intents such as:
 *   "text Mom saying I'll be late"
 *   "message John that I'm on my way"
 *   "send Sarah a message saying hello"
 *   "send a text to Mom with the message I'll be home soon"
 */
class SmsIntentMatcher(
    private val contactResolver: ContactResolver
) : IntentMatcher {

    override val intentType: IntentType = IntentType.SEND_SMS

    companion object {
        // "text/message X saying/that Y"
        private val SIMPLE_PATTERN = Regex(
            """(?:text|message)\s+(.+?)\s+(?:saying|that\s+says?|that)\s+(.+)""",
            RegexOption.IGNORE_CASE
        )

        // "send X a message/text saying/with the message Y"
        private val SEND_PATTERN = Regex(
            """send\s+(.+?)\s+(?:a\s+)?(?:message|text|sms)\s+(?:saying|that\s+says?|that|with\s+the\s+message)\s+(.+)""",
            RegexOption.IGNORE_CASE
        )

        // "send a text/message to X saying/with the message Y"
        private val SEND_TO_PATTERN = Regex(
            """send\s+(?:a\s+)?(?:message|text|sms)\s+to\s+(.+?)\s+(?:saying|that\s+says?|that|with\s+the\s+message)\s+(.+)""",
            RegexOption.IGNORE_CASE
        )
    }

    override fun tryMatch(transcript: String): ParsedCommand? {
        val input = transcript.trim()

        val (spokenName, body) = extractNameAndBody(input) ?: return null

        val matches = contactResolver.resolveMultiple(spokenName)

        return when {
            matches.isEmpty() -> {
                ParsedCommand(
                    intent = IntentType.SEND_SMS,
                    target = spokenName,
                    extra = "not_found|$body",
                    confidence = 0.85f
                )
            }
            matches.size == 1 -> {
                val resolved = matches[0]
                ParsedCommand(
                    intent = IntentType.SEND_SMS,
                    target = resolved.first,
                    extra = "${resolved.second}|$body",
                    confidence = 0.85f
                )
            }
            else -> {
                val disambiguateString = "disambiguate|$body|" + matches.joinToString("|") { "${it.first}:${it.second}" }
                ParsedCommand(
                    intent = IntentType.SEND_SMS,
                    target = spokenName,
                    extra = disambiguateString,
                    confidence = 0.85f
                )
            }
        }
    }

    private fun extractNameAndBody(input: String): Pair<String, String>? {
        // Try each pattern in order of specificity
        for (pattern in listOf(SEND_TO_PATTERN, SEND_PATTERN, SIMPLE_PATTERN)) {
            val match = pattern.find(input)
            if (match != null) {
                val name = match.groupValues[1].trim()
                val body = match.groupValues[2].trim()
                if (name.isNotEmpty() && body.isNotEmpty()) {
                    return Pair(name, body)
                }
            }
        }
        return null
    }
}
