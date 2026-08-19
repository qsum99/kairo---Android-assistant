package com.kairo.assistant.agent

import com.kairo.assistant.screen.ScreenLayout
import com.kairo.assistant.screen.ScreenLayoutParser

/**
 * Builds structured prompts optimized for the on-device LLaMA 1B model.
 *
 * The prompts use a strict JSON output format so the small model
 * can reliably produce parseable actions.
 */
object AgentPromptBuilder {

    /**
     * Build a prompt for the agent to decide the next action.
     *
     * @param task The user's original request (e.g., "Send Mom a WhatsApp message")
     * @param screenLayout The current screen's UI tree
     * @param previousAction Description of the last action taken (empty if first step)
     * @param stepNumber Current step number (0-based)
     */
    fun buildNextActionPrompt(
        task: String,
        screenLayout: ScreenLayout,
        previousAction: String,
        stepNumber: Int
    ): String {
        val screenText = ScreenLayoutParser.toInteractiveText(screenLayout, maxElements = 15)

        return buildString {
            appendLine("You are an Android phone automation agent.")
            appendLine("Your job: complete the user's task by choosing ONE action at a time.")
            appendLine()
            appendLine("TASK: $task")
            if (previousAction.isNotBlank()) {
                appendLine("LAST ACTION: $previousAction")
            }
            appendLine("STEP: ${stepNumber + 1}")
            appendLine()
            appendLine("CURRENT SCREEN:")
            appendLine(screenText)
            appendLine()
            appendLine("Choose exactly ONE action. Reply with ONLY a JSON object:")
            appendLine("""{"action":"open","package":"com.google.android.apps.tachyon","done":false,"desc":"Opening Google Meet"}""")
            appendLine("""{"action":"tap","x":540,"y":330,"done":false,"desc":"Tapping New meeting button"}""")
            appendLine("""{"action":"type","text":"Hello","done":false,"desc":"Typing message"}""")
            appendLine("""{"action":"scroll","dir":"down","done":false,"desc":"Scrolling down"}""")
            appendLine("""{"action":"back","done":false,"desc":"Going back"}""")
            appendLine("""{"action":"done","done":true,"desc":"Task completed successfully"}""")
            appendLine()
            appendLine("JSON:")
        }
    }

    /**
     * Build a prompt for screen understanding (non-agent, just Q&A about the screen).
     */
    fun buildScreenQueryPrompt(
        question: String,
        screenLayout: ScreenLayout
    ): String {
        val screenText = ScreenLayoutParser.toText(screenLayout, maxElements = 20)

        return buildString {
            appendLine("The user is looking at their phone screen and asks a question.")
            appendLine()
            appendLine("SCREEN CONTENT:")
            appendLine(screenText)
            appendLine()
            appendLine("USER QUESTION: $question")
            appendLine()
            appendLine("Answer in 1-2 short sentences:")
        }
    }

    /**
     * Build the full LLaMA-3 prompt with system/user/assistant tags.
     */
    fun wrapAsLlama3Prompt(systemMessage: String, userMessage: String): String {
        return "<|begin_of_text|><|start_header_id|>system<|end_header_id|>\n\n$systemMessage<|eot_id|><|start_header_id|>user<|end_header_id|>\n\n$userMessage<|eot_id|><|start_header_id|>assistant<|end_header_id|>\n\n"
    }
}
