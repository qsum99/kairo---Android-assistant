package com.kairo.assistant.actions

import android.content.Context
import android.util.Log
import com.kairo.assistant.agent.AgentPromptBuilder
import com.kairo.assistant.nlu.llm.LlamaEngine
import com.kairo.assistant.nlu.models.ParsedCommand
import com.kairo.assistant.screen.KairoAccessibilityService
import com.kairo.assistant.screen.ScreenLayoutParser
import kotlinx.coroutines.runBlocking

private const val TAG = "ScreenQueryExecutor"

/**
 * Executor for SCREEN_QUERY and SCREEN_EXPLAIN intents.
 * Reads the current screen via AccessibilityService,
 * sends the layout + question to on-device LLaMA, and returns the answer.
 */
class ScreenQueryExecutor : ActionExecutor {

    override fun execute(command: ParsedCommand, context: Context): ActionResult {
        val accessibilityService = KairoAccessibilityService.getInstance()
        if (accessibilityService == null) {
            return ActionResult(
                success = false,
                message = "Accessibility service is not running. Please enable Kairo in Settings > Accessibility."
            )
        }

        if (!LlamaEngine.isAvailable) {
            return ActionResult(
                success = false,
                message = "The AI model is not loaded yet. Please wait for it to finish loading."
            )
        }

        val question = command.extra ?: "What's on the screen?"

        return try {
            // 1. Read current screen
            val screenLayout = accessibilityService.getScreenLayout()

            if (screenLayout.elements.isEmpty()) {
                return ActionResult(
                    success = true,
                    message = "I can see the screen but there are no readable elements. It might be a media player or game."
                )
            }

            // 2. Build prompt for the LLM
            val prompt = AgentPromptBuilder.buildScreenQueryPrompt(
                question = question,
                screenLayout = screenLayout
            )

            val fullPrompt = AgentPromptBuilder.wrapAsLlama3Prompt(
                systemMessage = "You are a helpful screen reader assistant. Describe what you see concisely.",
                userMessage = prompt
            )

            // 3. Get answer from on-device LLaMA
            val answer = runBlocking {
                LlamaEngine.classifyRaw(fullPrompt, maxTokens = 128)
            }

            if (answer.isBlank()) {
                ActionResult(
                    success = true,
                    message = "I can see ${screenLayout.elements.size} elements on the screen from ${screenLayout.packageName.substringAfterLast('.')}, but couldn't generate a detailed description."
                )
            } else {
                Log.d(TAG, "Screen query answer: $answer")
                ActionResult(success = true, message = answer)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Screen query failed", e)
            ActionResult(
                success = false,
                message = "Failed to read the screen: ${e.message}"
            )
        }
    }
}
