package com.kairo.assistant.actions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import com.kairo.assistant.data.local.DraftHistoryEntity
import com.kairo.assistant.data.local.KairoDatabase
import com.kairo.assistant.intelligence.drafting.MessageDrafter
import com.kairo.assistant.nlu.models.ParsedCommand
import kotlinx.coroutines.runBlocking

private const val TAG = "DraftMessageExecutor"

/**
 * Executes DRAFT_MESSAGE intents by generating an AI-powered message draft
 * and copying it to the clipboard.
 *
 * The extra field of ParsedCommand is expected to contain the user's intent
 * (what they want to say), and 	arget contains the platform (whatsapp/email/sms/linkedin).
 *
 * Optionally includes tone in extra with pipe delimiter: "intent|tone|recipient"
 */
class DraftMessageExecutor : ActionExecutor {

    override fun execute(command: ParsedCommand, context: Context): ActionResult {
        val prefs = context.getSharedPreferences("kairo_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""

        if (apiKey.isBlank()) {
            return ActionResult(
                success = false,
                message = "Please set your Gemini API key in Settings to use message drafting."
            )
        }

        val rawModel = prefs.getString("gemini_model", "gemini-3.1-flash-lite") ?: "gemini-3.1-flash-lite"

        // Parse platform from target
        val platformStr = (command.target ?: "whatsapp").lowercase().trim()
        val platform = when {
            platformStr.contains("email") || platformStr.contains("mail") -> MessageDrafter.Platform.EMAIL
            platformStr.contains("sms") || platformStr.contains("text") -> MessageDrafter.Platform.SMS
            platformStr.contains("linkedin") -> MessageDrafter.Platform.LINKEDIN
            else -> MessageDrafter.Platform.WHATSAPP
        }

        // Parse intent, tone, and recipient from extra
        val parts = (command.extra ?: "").split("|")
        val userIntent = parts.getOrElse(0) { "Hello" }.trim()
        val toneStr = parts.getOrElse(1) { "professional" }.lowercase().trim()
        val recipientContext = parts.getOrElse(2) { "" }.trim().ifBlank { null }

        val tone = when {
            toneStr.contains("casual") -> MessageDrafter.Tone.CASUAL
            toneStr.contains("urgent") -> MessageDrafter.Tone.URGENT
            toneStr.contains("formal") -> MessageDrafter.Tone.FORMAL
            toneStr.contains("friendly") -> MessageDrafter.Tone.FRIENDLY
            else -> MessageDrafter.Tone.PROFESSIONAL
        }

        return try {
            val draft = runBlocking {
                MessageDrafter.draft(
                    platform = platform,
                    tone = tone,
                    userIntent = userIntent,
                    recipientContext = recipientContext,
                    apiKey = apiKey,
                    model = rawModel
                )
            }

            if (draft.isBlank()) {
                return ActionResult(false, "The AI returned an empty draft. Please try again.")
            }

            // Copy to clipboard
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Kairo Draft", draft)
            clipboard.setPrimaryClip(clip)

            // Save to draft history
            try {
                val db = KairoDatabase.getInstance(context)
                runBlocking {
                    db.draftHistoryDao().insert(
                        DraftHistoryEntity(
                            platform = platform.name.lowercase(),
                            tone = tone.name.lowercase(),
                            userIntent = userIntent,
                            generatedDraft = draft
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save draft to history", e)
            }

            ActionResult(
                success = true,
                message = "Here's your ${platform.displayName} draft (${tone.displayName} tone):\n\n$draft\n\nCopied to clipboard!"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Draft generation failed", e)
            ActionResult(false, "Failed to generate draft: ${e.message}")
        }
    }
}
