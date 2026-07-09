package com.kairo.assistant.nlu.rules

import com.kairo.assistant.nlu.IntentMatcher
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

class ExitIntentMatcher : IntentMatcher {

    override val intentType: IntentType = IntentType.EXIT

    companion object {
        private val PATTERN = Regex(
            """^(?:bye|goodbye|okay\s+bye|okay\s+goodbye|good\s+bye|bye\s+bye|see\s+you\s+soon|see\s+you\s+later|see\s+you|exit|quit|talk\s+to\s+you\s+later|catch\s+you\s+later)\s*[!?.]*$""",
            RegexOption.IGNORE_CASE
        )
    }

    override fun tryMatch(transcript: String): ParsedCommand? {
        val input = transcript.trim()

        if (PATTERN.matches(input)) {
            return ParsedCommand(
                intent = IntentType.EXIT,
                target = null,
                extra = null,
                confidence = 0.95f
            )
        }
        return null
    }
}
