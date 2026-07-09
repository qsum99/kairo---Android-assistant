package com.kairo.assistant.nlu.rules

import com.kairo.assistant.nlu.IntentMatcher
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand
import kotlin.random.Random

class GreetingIntentMatcher : IntentMatcher {

    override val intentType: IntentType = IntentType.CONVERSATION

    companion object {
        private val PATTERN = Regex(
            """^(?:hello|hi|hey|whats?\s+up|good\s+(?:morning|afternoon|evening)|yo|greetings|hola|howdy|hey\s+there|hello\s+there|hi\s+there|sup)\s*[!?.]*$""",
            RegexOption.IGNORE_CASE
        )

        private val GREETING_RESPONSES = listOf(
            "Hello! How can I help you today?",
            "Hi there! What can I do for you?",
            "Hey! How is it going?",
            "Hello! Kairo is here and ready to help.",
            "Hey! Kairo is listening, what do you need?",
            "What's up! How can I assist you today?"
        )
    }

    override fun tryMatch(transcript: String): ParsedCommand? {
        val input = transcript.trim()

        if (PATTERN.matches(input)) {
            val randomResponse = GREETING_RESPONSES[Random.nextInt(GREETING_RESPONSES.size)]
            return ParsedCommand(
                intent = IntentType.CONVERSATION,
                target = null,
                extra = randomResponse,
                confidence = 0.95f
            )
        }
        return null
    }
}
