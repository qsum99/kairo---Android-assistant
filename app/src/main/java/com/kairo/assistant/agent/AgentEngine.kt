package com.kairo.assistant.agent

import android.content.Context
import android.content.Intent
import android.util.Log
import com.kairo.assistant.nlu.llm.LlamaEngine
import com.kairo.assistant.screen.KairoAccessibilityService
import com.kairo.assistant.screen.ScreenLayoutParser
import com.kairo.assistant.screen.ScrollDirection
import com.kairo.assistant.tts.KairoTTS
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

private const val TAG = "AgentEngine"

/**
 * Autonomous agent engine that controls the phone via AccessibilityService.
 *
 * Feedback loop (inspired by PrivateAgent):
 * 1. Read current screen (AccessibilityService UI tree)
 * 2. Ask on-device LLaMA 1B: "What should I do next?"
 * 3. Parse AI's JSON response into an AgentAction
 * 4. Execute the action (tap, type, scroll, etc.)
 * 5. Wait for screen to update
 * 6. Repeat until task is complete or max steps reached
 *
 * 100% on-device — no cloud APIs.
 */
class AgentEngine(private val context: Context) {

    private val _progress = MutableStateFlow(AgentProgress())
    val progress: StateFlow<AgentProgress> = _progress.asStateFlow()

    private var isCancelled = false

    /**
     * Execute an autonomous multi-step task.
     *
     * @param userCommand The natural language task (e.g., "Send WhatsApp to Mom saying hi")
     * @param tts TTS engine for speaking progress updates
     * @param maxSteps Safety limit on number of steps
     */
    suspend fun executeTask(
        userCommand: String,
        tts: KairoTTS? = null,
        maxSteps: Int = 15
    ) {
        isCancelled = false
        var stepCount = 0
        var previousAction = ""

        _progress.value = AgentProgress(
            status = AgentStatus.RUNNING,
            taskDescription = userCommand,
            maxSteps = maxSteps
        )

        Log.i(TAG, "Starting agent task: $userCommand")
        tts?.speak("Starting task: $userCommand")

        val accessibilityService = KairoAccessibilityService.getInstance()
        if (accessibilityService == null) {
            val error = "Accessibility service not running. Please enable it in Settings."
            Log.e(TAG, error)
            tts?.speak(error)
            _progress.value = _progress.value.copy(
                status = AgentStatus.FAILED,
                error = error
            )
            return
        }

        while (stepCount < maxSteps && !isCancelled) {
            try {
                _progress.value = _progress.value.copy(
                    currentStep = stepCount + 1,
                    currentAction = "Reading screen..."
                )

                // 1. Read current screen
                val screenLayout = accessibilityService.getScreenLayout()
                if (screenLayout.elements.isEmpty()) {
                    Log.w(TAG, "Empty screen layout, waiting...")
                    delay(2000)
                    stepCount++
                    continue
                }

                // 2. Build prompt and ask on-device LLaMA
                val prompt = AgentPromptBuilder.buildNextActionPrompt(
                    task = userCommand,
                    screenLayout = screenLayout,
                    previousAction = previousAction,
                    stepNumber = stepCount
                )

                _progress.value = _progress.value.copy(currentAction = "Thinking...")

                val fullPrompt = AgentPromptBuilder.wrapAsLlama3Prompt(
                    systemMessage = "You are a phone automation agent. Output ONLY valid JSON.",
                    userMessage = prompt
                )

                val aiResponse = LlamaEngine.classifyRaw(fullPrompt, maxTokens = 64)
                Log.d(TAG, "AI response: $aiResponse")

                // 3. Parse the AI's decision
                val decision = parseAIResponse(aiResponse)

                // 4. Check if task is complete
                if (decision.isComplete) {
                    val summary = decision.summary ?: "Task completed"
                    Log.i(TAG, "Task complete: $summary")
                    tts?.speak("Done! $summary")
                    _progress.value = _progress.value.copy(
                        status = AgentStatus.COMPLETED,
                        currentAction = summary
                    )
                    return
                }

                // 5. Execute the action
                val actionDesc = decision.description
                Log.i(TAG, "Step ${stepCount + 1}: $actionDesc")
                _progress.value = _progress.value.copy(
                    currentAction = actionDesc
                )

                // Optionally speak each step
                if (stepCount == 0 || stepCount % 3 == 0) {
                    tts?.speak("Step ${stepCount + 1}: $actionDesc")
                }

                executeAction(accessibilityService, decision.action)
                previousAction = actionDesc

                // 6. Wait for screen to update
                delay(1500)
                stepCount++

            } catch (e: Exception) {
                Log.e(TAG, "Agent step failed", e)
                _progress.value = _progress.value.copy(
                    status = AgentStatus.FAILED,
                    error = "Step ${stepCount + 1} failed: ${e.message}"
                )
                tts?.speak("Agent encountered an error: ${e.message}")
                return
            }
        }

        if (isCancelled) {
            _progress.value = _progress.value.copy(status = AgentStatus.CANCELLED)
            tts?.speak("Task cancelled")
        } else {
            _progress.value = _progress.value.copy(
                status = AgentStatus.COMPLETED,
                currentAction = "Reached maximum steps"
            )
            tts?.speak("Reached the maximum number of steps")
        }
    }

    /**
     * Cancel the currently running task.
     */
    fun cancel() {
        isCancelled = true
        _progress.value = _progress.value.copy(status = AgentStatus.CANCELLED)
    }

    /**
     * Reset the agent to idle state.
     */
    fun reset() {
        isCancelled = false
        _progress.value = AgentProgress()
    }

    /**
     * Execute a single agent action via AccessibilityService.
     */
    private suspend fun executeAction(
        service: KairoAccessibilityService,
        action: AgentAction
    ) {
        when (action) {
            is AgentAction.Tap -> service.tap(action.x, action.y)
            is AgentAction.TypeText -> service.typeText(action.text)
            is AgentAction.Scroll -> service.scroll(action.direction)
            is AgentAction.Swipe -> service.swipe(action.x1, action.y1, action.x2, action.y2)
            is AgentAction.PressBack -> service.pressBack()
            is AgentAction.PressHome -> service.pressHome()
            is AgentAction.OpenApp -> openApp(action.packageName)
            is AgentAction.Wait -> delay(action.ms)
            is AgentAction.Complete -> { /* handled above */ }
        }
    }

    /**
     * Open an app by package name.
     */
    private fun openApp(packageName: String) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            } else {
                Log.w(TAG, "Could not find launch intent for $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app: $packageName", e)
        }
    }

    /**
     * Parse the LLM's JSON response into an [AIDecision].
     */
    private fun parseAIResponse(raw: String): AIDecision {
        try {
            // Extract JSON from response
            val jsonStart = raw.indexOf('{')
            val jsonEnd = raw.lastIndexOf('}')
            if (jsonStart == -1 || jsonEnd == -1 || jsonEnd <= jsonStart) {
                throw IllegalArgumentException("No JSON found in: ${raw.take(100)}")
            }

            val jsonStr = raw.substring(jsonStart, jsonEnd + 1)
            val json = JSONObject(jsonStr)

            val actionType = json.optString("action", "").lowercase()
            val isDone = json.optBoolean("done", false)
            val description = json.optString("desc", "Performing action")

            if (isDone || actionType == "done") {
                return AIDecision(
                    action = AgentAction.Complete(description),
                    description = description,
                    isComplete = true,
                    summary = description
                )
            }

            val action = when (actionType) {
                "tap" -> AgentAction.Tap(
                    x = json.optInt("x", 540),
                    y = json.optInt("y", 960),
                    description = description
                )
                "type" -> AgentAction.TypeText(
                    text = json.optString("text", "")
                )
                "scroll" -> {
                    val dir = json.optString("dir", "down")
                    AgentAction.Scroll(
                        direction = when (dir) {
                            "up" -> ScrollDirection.UP
                            "down" -> ScrollDirection.DOWN
                            "left" -> ScrollDirection.LEFT
                            "right" -> ScrollDirection.RIGHT
                            else -> ScrollDirection.DOWN
                        }
                    )
                }
                "swipe" -> AgentAction.Swipe(
                    x1 = json.optInt("x1", 540),
                    y1 = json.optInt("y1", 1200),
                    x2 = json.optInt("x2", 540),
                    y2 = json.optInt("y2", 600),
                    description = description
                )
                "back" -> AgentAction.PressBack
                "home" -> AgentAction.PressHome
                "open" -> AgentAction.OpenApp(
                    packageName = json.optString("package", "")
                )
                "wait" -> AgentAction.Wait(
                    ms = json.optLong("ms", 2000)
                )
                else -> AgentAction.Wait(ms = 1000) // Unknown action, just wait
            }

            return AIDecision(
                action = action,
                description = description,
                isComplete = false
            )

        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse AI response: ${raw.take(200)}", e)
            // Default: wait and retry
            return AIDecision(
                action = AgentAction.Wait(ms = 2000),
                description = "Retrying...",
                isComplete = false
            )
        }
    }
}
