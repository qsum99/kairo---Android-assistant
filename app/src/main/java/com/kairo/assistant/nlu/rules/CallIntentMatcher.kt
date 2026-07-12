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
        private val PATTERNS = listOf(
            Regex("""\b(?:call|ring|phone|dial|contact|phone\s+up)\s+(.+)""", RegexOption.IGNORE_CASE),
            Regex("""give\s+(.+?)\s+a\s+(?:call|ring)""", RegexOption.IGNORE_CASE),
            Regex("""(?:make|place)\s+(?:a\s+)?call\s+to\s+(.+)""", RegexOption.IGNORE_CASE),
            Regex("""(?:connect\s+me\s+to|get\s+in\s+touch\s+with)\s+(.+)""", RegexOption.IGNORE_CASE)
        )
    }

    override fun tryMatch(transcript: String): ParsedCommand? {
        val input = transcript.trim()

        val simSuffixRegex = Regex("""(?:\bwith\b|\busing\b|\bon\b|\bvia\b)\s+sim\s*(\d)\b""", RegexOption.IGNORE_CASE)
        val simMatch = simSuffixRegex.find(input)
        val requestedSimIndex = simMatch?.groupValues?.get(1)?.toIntOrNull() // 1 or 2
        val cleanedInput = if (simMatch != null) input.replace(simSuffixRegex, "").trim() else input

        var spokenName: String? = null
        for (pattern in PATTERNS) {
            val match = pattern.find(cleanedInput)
            if (match != null) {
                spokenName = match.groupValues[1].trim()
                break
            }
        }
        if (spokenName == null || spokenName.isEmpty()) return null

        val matches = contactResolver.resolveMultiple(spokenName)
        val cleanNumber = spokenName.replace(Regex("""[\s\-\(\)]"""), "")
        val isPhoneNumber = cleanNumber.isNotEmpty() && cleanNumber.all { it.isDigit() || it == '+' }

        val simSuffix = if (requestedSimIndex != null) "|requested_sim|$requestedSimIndex" else ""

        return when {
            isPhoneNumber -> {
                ParsedCommand(
                    intent = IntentType.CALL,
                    target = spokenName,
                    extra = cleanNumber + simSuffix,
                    confidence = 0.9f
                )
            }
            matches.isEmpty() -> {
                ParsedCommand(
                    intent = IntentType.CALL,
                    target = spokenName,
                    extra = "not_found" + simSuffix,
                    confidence = 0.9f
                )
            }
            else -> {
                val resolved = matches[0]
                ParsedCommand(
                    intent = IntentType.CALL,
                    target = resolved.first,
                    extra = resolved.second + simSuffix,
                    confidence = 0.9f
                )
            }
        }
    }
}
