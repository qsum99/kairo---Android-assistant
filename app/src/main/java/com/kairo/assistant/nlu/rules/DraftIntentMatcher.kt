package com.kairo.assistant.nlu.rules

import com.kairo.assistant.nlu.IntentMatcher
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

/**
 * Matches message drafting intents such as:
 *   "draft a WhatsApp message to my boss saying I'll be late"
 *   "write an email to the client about the project update"
 *   "compose a LinkedIn message introducing myself"
 *   "draft a casual text to mom saying happy birthday"
 */
class DraftIntentMatcher : IntentMatcher {

    override val intentType: IntentType = IntentType.DRAFT_MESSAGE

    companion object {
        // "draft/write/compose a [platform] message [to recipient] saying/about X"
        private val DRAFT_FULL_PATTERN = Regex(
            """(?:draft|write|compose|create|generate)\s+(?:an?\s+)?(?:(whatsapp|email|e-mail|sms|text|linkedin)\s+)?(?:message|msg|mail|letter|text|note)\s+(?:to\s+(.+?)\s+)?(?:saying|about|that|regarding|for)\s+(.+)""",
            RegexOption.IGNORE_CASE
        )

        // "draft a [tone] [platform] message saying X"
        private val DRAFT_TONE_PATTERN = Regex(
            """(?:draft|write|compose)\s+(?:an?\s+)?(?:(professional|casual|urgent|formal|friendly)\s+)?(?:(whatsapp|email|e-mail|sms|text|linkedin)\s+)?(?:message|msg|mail|letter|text)\s+(?:saying|about|that)\s+(.+)""",
            RegexOption.IGNORE_CASE
        )

        // Simple: "draft/write message: X" or "draft: X"
        private val DRAFT_SIMPLE_PATTERN = Regex(
            """(?:draft|compose)\s*:?\s+(.+)""",
            RegexOption.IGNORE_CASE
        )

        // "write to [recipient] on [platform] saying X"
        private val WRITE_TO_PATTERN = Regex(
            """(?:write|draft)\s+(?:to\s+(.+?)\s+)?(?:on\s+(.+?)\s+)?(?:saying|about|that)\s+(.+)""",
            RegexOption.IGNORE_CASE
        )

        // Keywords that indicate a draft intent (for lower-confidence matching)
        private val DRAFT_KEYWORDS = listOf("draft", "compose", "write a message", "write an email", "write a mail")
    }

    override fun tryMatch(transcript: String): ParsedCommand? {
        val input = transcript.trim()

        // Try full pattern first
        DRAFT_FULL_PATTERN.find(input)?.let { match ->
            val platform = match.groupValues[1].ifBlank { "whatsapp" }
            val recipient = match.groupValues[2].trim().ifBlank { null }
            val content = match.groupValues[3].trim()
            return buildCommand(platform, "professional", content, recipient)
        }

        // Try tone-aware pattern
        DRAFT_TONE_PATTERN.find(input)?.let { match ->
            val tone = match.groupValues[1].ifBlank { "professional" }
            val platform = match.groupValues[2].ifBlank { "whatsapp" }
            val content = match.groupValues[3].trim()
            return buildCommand(platform, tone, content, null)
        }

        // Try write-to pattern
        WRITE_TO_PATTERN.find(input)?.let { match ->
            val recipient = match.groupValues[1].trim().ifBlank { null }
            val platform = match.groupValues[2].ifBlank { "whatsapp" }
            val content = match.groupValues[3].trim()
            return buildCommand(platform, "professional", content, recipient)
        }

        // Try simple pattern (lowest priority)
        val lower = input.lowercase()
        if (DRAFT_KEYWORDS.any { lower.startsWith(it) }) {
            DRAFT_SIMPLE_PATTERN.find(input)?.let { match ->
                val content = match.groupValues[1].trim()
                if (content.isNotBlank()) {
                    return buildCommand("whatsapp", "professional", content, null)
                }
            }
        }

        return null
    }

    private fun buildCommand(
        platform: String,
        tone: String,
        content: String,
        recipient: String?
    ): ParsedCommand {
        val normalizedPlatform = when {
            platform.contains("email", true) || platform.contains("mail", true) -> "email"
            platform.contains("sms", true) || platform.contains("text", true) -> "sms"
            platform.contains("linkedin", true) -> "linkedin"
            else -> "whatsapp"
        }

        // Pack intent|tone|recipient into extra
        val extra = buildString {
            append(content)
            append("|")
            append(tone.lowercase())
            if (!recipient.isNullOrBlank()) {
                append("|")
                append(recipient)
            }
        }

        return ParsedCommand(
            intent = IntentType.DRAFT_MESSAGE,
            target = normalizedPlatform,
            extra = extra,
            confidence = 0.90f
        )
    }
}
