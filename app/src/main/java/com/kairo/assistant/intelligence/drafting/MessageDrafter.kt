package com.kairo.assistant.intelligence.drafting

import android.content.Context
import android.util.Log
import com.kairo.assistant.nlu.llm.GeminiClient

private const val TAG = "MessageDrafter"

/**
 * AI-powered message composer that generates platform-appropriate drafts.
 *
 * Supports WhatsApp, Email, SMS, and LinkedIn with customizable tone settings.
 * Uses Gemini Cloud API for high-quality natural language generation.
 */
object MessageDrafter {

    /**
     * Supported messaging platforms with format-specific instructions.
     */
    enum class Platform(val displayName: String, val formatHint: String) {
        WHATSAPP("WhatsApp", "Keep it conversational, use short paragraphs. No subject line. Can use emojis sparingly if casual tone."),
        EMAIL("Email", "Include a subject line on the first line prefixed with 'Subject: '. Use proper greeting and sign-off. Professional formatting with paragraphs."),
        SMS("SMS", "Keep it very short and concise, under 160 characters if possible. No greeting/sign-off needed."),
        LINKEDIN("LinkedIn", "Professional networking tone. Keep it concise but polished. Use proper greeting.")
    }

    /**
     * Tone presets for message generation.
     */
    enum class Tone(val displayName: String, val instruction: String) {
        PROFESSIONAL("Professional", "Use formal, business-appropriate language. Be clear, concise, and respectful."),
        CASUAL("Casual", "Use friendly, relaxed language. Contractions are fine. Keep it warm and approachable."),
        URGENT("Urgent", "Convey urgency and importance. Be direct and action-oriented. Highlight time-sensitivity."),
        FORMAL("Formal", "Use highly formal language. No contractions. Proper salutations and closings."),
        FRIENDLY("Friendly", "Warm and personable tone. Show genuine interest and care. Approachable but still clear.")
    }

    /**
     * Generate a message draft using Gemini AI.
     *
     * @param platform Target messaging platform
     * @param tone Desired message tone
     * @param userIntent What the user wants to communicate (natural language)
     * @param recipientContext Optional context about the recipient (e.g., "my boss", "a client")
     * @param apiKey Gemini API key
     * @param model Gemini model to use
     * @return The generated draft text
     */
    suspend fun draft(
        platform: Platform,
        tone: Tone,
        userIntent: String,
        recipientContext: String? = null,
        apiKey: String,
        model: String = GeminiClient.DEFAULT_MODEL
    ): String {
        val prompt = buildDraftPrompt(platform, tone, userIntent, recipientContext)

        return try {
            val response = GeminiClient.generateContent(
                prompt = prompt,
                apiKey = apiKey,
                model = model,
                systemInstruction = SYSTEM_INSTRUCTION,
                jsonMode = false // We want natural text, not JSON
            )
            cleanDraftResponse(response)
        } catch (e: Exception) {
            Log.e(TAG, "Draft generation failed", e)
            throw DraftException("Could not generate draft: ${e.message}")
        }
    }

    private fun buildDraftPrompt(
        platform: Platform,
        tone: Tone,
        userIntent: String,
        recipientContext: String?
    ): String = buildString {
        appendLine("Generate a ${platform.displayName} message with the following requirements:")
        appendLine()
        appendLine("USER WANTS TO SAY: $userIntent")
        if (!recipientContext.isNullOrBlank()) {
            appendLine("RECIPIENT: $recipientContext")
        }
        appendLine()
        appendLine("PLATFORM FORMAT: ${platform.formatHint}")
        appendLine("TONE: ${tone.displayName} - ${tone.instruction}")
        appendLine()
        appendLine("RULES:")
        appendLine("- Output ONLY the message text, nothing else")
        appendLine("- Do NOT include labels like 'Message:' or 'Draft:'")
        appendLine("- Do NOT include explanations before or after the message")
        appendLine("- Make it sound natural and human-written")
        appendLine("- Match the platform's typical communication style")
    }

    private fun cleanDraftResponse(response: String): String {
        var cleaned = response.trim()
        // Remove common LLM artifacts
        cleaned = cleaned.removePrefix("Message:").removePrefix("Draft:")
            .removePrefix("Here's").removePrefix("Here is")
            .trimStart(':', ' ', '\n')
        // Remove surrounding quotes if present
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length - 1)
        }
        return cleaned.trim()
    }

    private const val SYSTEM_INSTRUCTION = """You are a professional message writing assistant. 
Your ONLY job is to output the message text itself - no labels, no explanations, no meta-commentary.
Write naturally as if a human wrote it. Match the requested platform and tone precisely."""
}

class DraftException(message: String) : Exception(message)
