package com.kairo.assistant.nlu.rules

import com.kairo.assistant.data.ContactResolver
import com.kairo.assistant.nlu.IntentMatcher
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

/**
 * Matches call-related intents such as:
 *   "call Mom", "ring John", "phone Sarah", "dial 911",
 *   "give Mom a call", "give John a ring"
 */
class CallIntentMatcher(
    private val contactResolver: ContactResolver
) : IntentMatcher {

    override val intentType: IntentType = IntentType.CALL

    companion object {
        // "call/ring/phone/dial X"
        private val DIRECT_PATTERN = Regex(
            """(?:call|ring|phone|dial)\s+(.+)""",
            RegexOption.IGNORE_CASE
        )

        // "give X a call/ring"
        private val GIVE_PATTERN = Regex(
            """give\s+(.+?)\s+a\s+(?:call|ring)""",
            RegexOption.IGNORE_CASE
        )
    }

    override fun tryMatch(transcript: String): ParsedCommand? {
        val input = transcript.trim()

        val spokenName = DIRECT_PATTERN.find(input)?.groupValues?.get(1)?.trim()
            ?: GIVE_PATTERN.find(input)?.groupValues?.get(1)?.trim()
            ?: return null

        val matches = contactResolver.resolveMultiple(spokenName)

        return when {
            matches.isEmpty() -> {
                ParsedCommand(
                    intent = IntentType.CALL,
                    target = spokenName,
                    extra = "not_found",
                    confidence = 0.9f
                )
            }
            matches.size == 1 -> {
                val resolved = matches[0]
                ParsedCommand(
                    intent = IntentType.CALL,
                    target = resolved.first,
                    extra = resolved.second,
                    confidence = 0.9f
                )
            }
            else -> {
                val disambiguateString = "disambiguate|" + matches.joinToString("|") { "${it.first}:${it.second}" }
                ParsedCommand(
                    intent = IntentType.CALL,
                    target = spokenName,
                    extra = disambiguateString,
                    confidence = 0.9f
                )
            }
        }
    }
}
