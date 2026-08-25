package com.kairo.assistant.agent

import android.content.Context
import android.content.Intent
import android.util.Log
import com.kairo.assistant.nlu.llm.GeminiClient
import com.kairo.assistant.nlu.llm.LlamaEngine
import com.kairo.assistant.screen.KairoAccessibilityService
import com.kairo.assistant.screen.ScreenLayout
import com.kairo.assistant.screen.ScreenLayoutParser
import com.kairo.assistant.screen.ScrollDirection
import com.kairo.assistant.screen.ScreenElement
import com.kairo.assistant.tts.KairoTTS
import com.kairo.assistant.intelligence.learning.AppKnowledgeManager
import com.kairo.assistant.intelligence.learning.FlowReplayer
import com.kairo.assistant.intelligence.learning.RecordedStep
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
        maxSteps: Int = 15,
        knowledgeManager: AppKnowledgeManager? = null
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

        val prefs = context.getSharedPreferences("kairo_prefs", Context.MODE_PRIVATE)
        val llmBackend = prefs.getString("llm_backend", "gemini") ?: "gemini"
        val geminiApiKey = prefs.getString("gemini_api_key", "") ?: ""
        val rawModel = prefs.getString("gemini_model", "gemini-3.1-flash-lite") ?: "gemini-3.1-flash-lite"
        val geminiModel = if (rawModel.contains("3.5") || rawModel.contains("2.5")) "gemini-3.1-flash-lite" else rawModel

        // Ensure on-device LLM is loaded if in local mode
        if (llmBackend == "local" && !LlamaEngine.isAvailable) {
            _progress.value = _progress.value.copy(currentAction = "Initializing AI Brain...")
            LlamaEngine.initialize(context)
        }

        // ── Phase B: Try replaying a learned flow before hitting the LLM ──
        if (knowledgeManager != null) {
            _progress.value = _progress.value.copy(currentAction = "Checking for learned shortcut...")
            val learnedFlow = knowledgeManager.findFlow(userCommand)
            if (learnedFlow != null) {
                Log.i(TAG, "Found learned flow: ${learnedFlow.steps.size} steps, successCount=${learnedFlow.successCount}")
                _progress.value = _progress.value.copy(currentAction = "Replaying learned shortcut...")
                tts?.speak("Using a known shortcut")

                val replaySuccess = FlowReplayer.replay(learnedFlow, accessibilityService) { idx, desc, _ ->
                    _progress.value = _progress.value.copy(currentStep = idx + 1, currentAction = desc)
                }

                if (replaySuccess) {
                    knowledgeManager.recordSuccess(learnedFlow.packageName, userCommand, learnedFlow.steps)
                    tts?.speak("Done!")
                    _progress.value = _progress.value.copy(
                        status = AgentStatus.COMPLETED,
                        currentAction = "Task completed (learned shortcut)"
                    )
                    return
                } else {
                    Log.w(TAG, "Replay failed, falling back to LLM mode")
                    knowledgeManager.recordFailure(learnedFlow.packageName, userCommand)
                    tts?.speak("Shortcut didn't work, figuring it out the long way")
                }
            }
        }

        var consecutiveRetries = 0
        var consecutiveNoChange = 0
        val recordedSteps = mutableListOf<RecordedStep>()

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
                    delay(1500)
                    stepCount++
                    continue
                }

                // 2. Build prompt and ask LLM (Gemini Cloud API or on-device LLaMA)
                val prompt = AgentPromptBuilder.buildNextActionPrompt(
                    task = userCommand,
                    screenLayout = screenLayout,
                    previousAction = previousAction,
                    stepNumber = stepCount
                )

                val fullPrompt = AgentPromptBuilder.wrapAsLlama3Prompt(
                    systemMessage = "You are a phone automation agent. Output ONLY valid JSON.",
                    userMessage = prompt
                )

                val aiResponse = if (llmBackend == "gemini" && geminiApiKey.isNotBlank()) {
                    _progress.value = _progress.value.copy(currentAction = "Querying Gemini API...")
                    try {
                        GeminiClient.generateContent(
                            prompt = prompt,
                            apiKey = geminiApiKey,
                            model = geminiModel,
                            systemInstruction = "You are an autonomous Android automation agent. Output ONLY a valid single JSON action object. Do NOT output conversational text or markdown. Reply strictly with JSON.",
                            jsonMode = true
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Gemini API failed, falling back to local model: ${e.message}")
                        if (LlamaEngine.isAvailable) {
                            LlamaEngine.classifyRaw(fullPrompt, maxTokens = 128)
                        } else {
                            ""
                        }
                    }
                } else {
                    _progress.value = _progress.value.copy(currentAction = "Thinking (On-Device)...")
                    if (LlamaEngine.isAvailable) {
                        LlamaEngine.classifyRaw(fullPrompt, maxTokens = 128)
                    } else {
                        ""
                    }
                }
                Log.d(TAG, "AI response ($llmBackend): $aiResponse")

                // 3. Parse the AI's decision or use smart heuristic fallback
                val decision = if (aiResponse.isNotBlank()) {
                    try {
                        parseAIResponse(aiResponse)
                    } catch (e: Exception) {
                        Log.w(TAG, "JSON parsing failed, falling back to smart heuristics: ${e.message}")
                        smartHeuristicFallback(userCommand, screenLayout, stepCount)
                    }
                } else {
                    Log.i(TAG, "Using smart heuristics fallback for step $stepCount")
                    smartHeuristicFallback(userCommand, screenLayout, stepCount)
                }

                // 4. Check if task is complete
                if (decision.isComplete) {
                    val summary = decision.summary ?: "Task completed"
                    Log.i(TAG, "Task complete: $summary")
                    tts?.speak("Done! $summary")
                    _progress.value = _progress.value.copy(
                        status = AgentStatus.COMPLETED,
                        currentAction = summary
                    )
                    // Phase B: Save the learned flow for future replay
                    if (knowledgeManager != null && recordedSteps.isNotEmpty()) {
                        val pkg = accessibilityService.currentPackage
                        knowledgeManager.recordSuccess(pkg, userCommand, recordedSteps)
                        Log.i(TAG, "Saved learned flow: ${recordedSteps.size} steps in $pkg")
                    }
                    return
                }

                // 5. Execute the action
                val actionDesc = decision.description
                Log.i(TAG, "Step ${stepCount + 1}: $actionDesc")
                _progress.value = _progress.value.copy(
                    currentAction = actionDesc
                )

                if (actionDesc.startsWith("Retrying")) {
                    consecutiveRetries++
                    if (consecutiveRetries >= 3) {
                        // Avoid endless retry loops
                        Log.w(TAG, "Too many retries, attempting heuristics")
                        val fallbackDecision = smartHeuristicFallback(userCommand, screenLayout, stepCount)
                        executeAction(accessibilityService, fallbackDecision.action)
                        previousAction = fallbackDecision.description
                        consecutiveRetries = 0
                        accessibilityService.waitForMeaningfulChange(
                            accessibilityService.signatureOf(screenLayout), 2500
                        )
                        stepCount++
                        continue
                    }
                } else {
                    consecutiveRetries = 0
                }

                // Optionally speak each step
                if (stepCount == 0 || stepCount % 3 == 0) {
                    tts?.speak(actionDesc)
                }

                // 5b. Execute the action, then wait for a *meaningful* screen change
                // (signature change stable for a settle window — not just any event)
                val baselineSignature = accessibilityService.signatureOf(screenLayout)
                executeAction(accessibilityService, decision.action)

                val screenChanged = if (decision.action is AgentAction.Wait) {
                    // Explicit waits already consumed their own duration — short check only
                    accessibilityService.waitForMeaningfulChange(baselineSignature, 600)
                } else {
                    accessibilityService.waitForMeaningfulChange(baselineSignature, 2500)
                }

                // 6. Feed the real outcome into the next reasoning step
                if (screenChanged) {
                    consecutiveNoChange = 0
                    previousAction = "$actionDesc (screen updated)"
                } else {
                    consecutiveNoChange++
                    Log.w(TAG, "Action had no visible effect ($consecutiveNoChange in a row)")
                    previousAction =
                        "$actionDesc (NO EFFECT: the screen did not change. Do NOT repeat this exact action — try a different approach)"
                    if (consecutiveNoChange >= 2) {
                        // Escalate: let heuristics force a different class of action
                        Log.w(TAG, "Two consecutive no-effect actions, escalating to heuristics")
                        val escalation = smartHeuristicFallback(userCommand, screenLayout, stepCount)
                        if (escalation.action !is AgentAction.Wait) {
                            val escalationBaseline = accessibilityService.currentSignature()
                            executeAction(accessibilityService, escalation.action)
                            accessibilityService.waitForMeaningfulChange(escalationBaseline, 2500)
                            previousAction = "Escalation: ${escalation.description}"
                        }
                        consecutiveNoChange = 0
                    }
                }

                // Phase B: Record the step for future replay
                if (screenChanged && decision.action !is AgentAction.Wait && decision.action !is AgentAction.Complete) {
                    recordedSteps.add(recordStep(decision.action, screenLayout))
                }

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
                currentAction = "Task completed"
            )
            tts?.speak("Task execution finished")
        }
    }

    /**
     * Smart heuristic fallback for common actions when offline LLM output is not available.
     */
    private fun smartHeuristicFallback(
        task: String,
        screenLayout: ScreenLayout,
        stepNumber: Int
    ): AIDecision {
        val lowerTask = task.lowercase()

        // 1. WhatsApp tasks
        if (lowerTask.contains("whatsapp") || lowerTask.contains("message") || lowerTask.contains("text")) {
            val isWhatsAppOpen = screenLayout.packageName.contains("whatsapp")

            if (!isWhatsAppOpen) {
                return AIDecision(
                    action = AgentAction.OpenApp("com.whatsapp"),
                    description = "Opening WhatsApp...",
                    isComplete = false
                )
            }

            if (isWhatsAppOpen) {
                // Check for send button
                val sendButton = screenLayout.elements.find {
                    it.contentDescription?.lowercase()?.contains("send") == true ||
                    it.text?.lowercase()?.contains("send") == true
                }
                if (sendButton != null) {
                    return AIDecision(
                        action = AgentAction.Tap(sendButton.bounds.centerX(), sendButton.bounds.centerY(), "Tapping Send"),
                        description = "Tapping Send button",
                        isComplete = false
                    )
                }

                // Check for edit text / message box
                val editBox = screenLayout.elements.find { it.isEditable }
                if (editBox != null) {
                    // Extract message text from command
                    val msgText = extractMessageBody(task)
                    return AIDecision(
                        action = AgentAction.TypeText(msgText),
                        description = "Typing message: \"$msgText\"",
                        isComplete = false
                    )
                }

                // Check if target contact name appears on screen
                val targetContact = extractTargetName(task)
                if (targetContact.isNotBlank()) {
                    val contactElement = screenLayout.elements.find {
                        it.text?.lowercase()?.contains(targetContact.lowercase()) == true ||
                        it.contentDescription?.lowercase()?.contains(targetContact.lowercase()) == true
                    }
                    if (contactElement != null) {
                        return AIDecision(
                            action = AgentAction.Tap(contactElement.bounds.centerX(), contactElement.bounds.centerY(), "Selecting $targetContact"),
                            description = "Opening chat with $targetContact",
                            isComplete = false
                        )
                    }

                    // Look for search button
                    val searchButton = screenLayout.elements.find {
                        it.contentDescription?.lowercase()?.contains("search") == true ||
                        it.text?.lowercase()?.contains("search") == true
                    }
                    if (searchButton != null && stepNumber <= 2) {
                        return AIDecision(
                            action = AgentAction.Tap(searchButton.bounds.centerX(), searchButton.bounds.centerY(), "Tapping Search"),
                            description = "Tapping search to find $targetContact",
                            isComplete = false
                        )
                    }
                }
            }
        }

        // 2. Google Meet / Meeting tasks
        if (lowerTask.contains("meet") || lowerTask.contains("meeting")) {
            val isMeetOpen = screenLayout.packageName.contains("tachyon") || screenLayout.packageName.contains("meet")

            if (!isMeetOpen) {
                return AIDecision(
                    action = AgentAction.OpenApp("com.google.android.apps.tachyon"),
                    description = "Opening Google Meet...",
                    isComplete = false
                )
            }

            if (isMeetOpen) {
                // Check for "Start instant meeting" or "Start an instant meeting"
                val instantMeetingBtn = screenLayout.elements.find {
                    val label = (it.text ?: it.contentDescription ?: "").lowercase()
                    label.contains("instant meeting") || label.contains("start an instant")
                }
                if (instantMeetingBtn != null) {
                    return AIDecision(
                        action = AgentAction.Tap(instantMeetingBtn.bounds.centerX(), instantMeetingBtn.bounds.centerY(), "Starting instant meeting"),
                        description = "Tapping Start Instant Meeting",
                        isComplete = false
                    )
                }

                // Check for "New", "New meeting", or "Create" button
                val newMeetingBtn = screenLayout.elements.find {
                    val label = (it.text ?: it.contentDescription ?: "").lowercase()
                    label == "new" || label.contains("new meeting") || label.contains("create meeting") || label.contains("new call")
                }
                if (newMeetingBtn != null) {
                    return AIDecision(
                        action = AgentAction.Tap(newMeetingBtn.bounds.centerX(), newMeetingBtn.bounds.centerY(), "Tapping New Meeting"),
                        description = "Tapping New Meeting button",
                        isComplete = false
                    )
                }

                // Check for "Join now" or "Join"
                val joinBtn = screenLayout.elements.find {
                    val label = (it.text ?: it.contentDescription ?: "").lowercase()
                    label.contains("join now") || label == "join"
                }
                if (joinBtn != null) {
                    return AIDecision(
                        action = AgentAction.Tap(joinBtn.bounds.centerX(), joinBtn.bounds.centerY(), "Joining meeting"),
                        description = "Joining meeting",
                        isComplete = true,
                        summary = "Meeting created and joined successfully"
                    )
                }
            }
        }

        // 3. Settings tasks
        if (lowerTask.contains("settings")) {
            val isSettingsOpen = screenLayout.packageName.contains("settings")
            if (!isSettingsOpen) {
                return AIDecision(
                    action = AgentAction.OpenApp("com.android.settings"),
                    description = "Opening Settings...",
                    isComplete = false
                )
            }
        }

        // 4. Fallback to first clickable element or complete
        if (stepNumber >= 4) {
            return AIDecision(
                action = AgentAction.Complete("Action sequence executed"),
                description = "Task completed",
                isComplete = true,
                summary = "Task completed"
            )
        }

        return AIDecision(
            action = AgentAction.Wait(ms = 1500),
            description = "Analyzing screen...",
            isComplete = false
        )
    }

    private fun extractTargetName(command: String): String {
        val lower = command.lowercase()
        val toIdx = lower.indexOf(" to ")
        if (toIdx != -1) {
            val rest = command.substring(toIdx + 4).trim()
            val words = rest.split(" ")
            return words.firstOrNull()?.trim() ?: ""
        }
        return ""
    }

    private fun extractMessageBody(command: String): String {
        val lower = command.lowercase()
        val sayingIdx = lower.indexOf(" saying ")
        if (sayingIdx != -1) {
            return command.substring(sayingIdx + 8).trim()
        }
        val textIdx = lower.indexOf(" text ")
        if (textIdx != -1) {
            return command.substring(textIdx + 6).trim()
        }
        return "Hello!"
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
     * Open an app by package name or common app alias.
     */
    private fun openApp(packageNameOrAlias: String) {
        try {
            val resolvedPkg = resolvePackage(packageNameOrAlias)
            val launchIntent = context.packageManager.getLaunchIntentForPackage(resolvedPkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            } else {
                Log.w(TAG, "Could not find launch intent for $packageNameOrAlias (resolved: $resolvedPkg)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app: $packageNameOrAlias", e)
        }
    }

    private fun resolvePackage(name: String): String {
        if (name.contains(".")) return name
        val lower = name.lowercase().trim()
        return when {
            lower.contains("meet") -> "com.google.android.apps.tachyon"
            lower.contains("whatsapp") -> "com.whatsapp"
            lower.contains("youtube") -> "com.google.android.youtube"
            lower.contains("chrome") -> "com.android.chrome"
            lower.contains("map") -> "com.google.android.apps.maps"
            lower.contains("setting") -> "com.android.settings"
            lower.contains("camera") -> "com.motorola.camera3"
            lower.contains("play") || lower.contains("store") -> "com.android.vending"
            lower.contains("photo") -> "com.google.android.apps.photos"
            lower.contains("gmail") || lower.contains("mail") -> "com.google.android.gm"
            else -> name
        }
    }

    // ── Phase B: Step recording helpers ─────────────────────────────────

    /**
     * Convert a just-executed AgentAction into a RecordedStep for future replay.
     * Resolves tap coordinates back to the element that was tapped so we
     * store semantic anchors instead of brittle pixel positions.
     */
    private fun recordStep(action: AgentAction, layout: ScreenLayout): RecordedStep {
        return when (action) {
            is AgentAction.Tap -> {
                val element = findElementAt(layout, action.x, action.y)
                RecordedStep(
                    action = "tap",
                    elementText = element?.text?.toString() ?: element?.contentDescription?.toString(),
                    elementClass = element?.className?.toString()?.substringAfterLast('.'),
                    nearText = findNearTextAnchor(layout, element),
                    fallbackX = action.x,
                    fallbackY = action.y
                )
            }
            is AgentAction.TypeText -> {
                val focused = layout.elements.find { it.isEditable }
                RecordedStep(
                    action = "type",
                    typeText = action.text,
                    elementText = focused?.text?.toString() ?: focused?.contentDescription?.toString(),
                    elementClass = focused?.className?.toString()?.substringAfterLast('.'),
                    fallbackX = focused?.bounds?.centerX() ?: 540,
                    fallbackY = focused?.bounds?.centerY() ?: 960
                )
            }
            is AgentAction.Scroll -> RecordedStep(
                action = "scroll",
                scrollDir = action.direction.name.lowercase()
            )
            is AgentAction.PressBack -> RecordedStep(action = "back")
            else -> RecordedStep(action = "wait")
        }
    }

    /**
     * Find the ScreenElement whose bounds contain the given coordinates.
     */
    private fun findElementAt(layout: ScreenLayout, x: Int, y: Int): ScreenElement? {
        return layout.elements.find { it.bounds.contains(x, y) }
    }

    /**
     * Find the nearest text-bearing element to serve as a disambiguation anchor.
     * This becomes the `nearText` in the recorded step — when multiple "Add"
     * buttons exist, the replay engine picks the one closest to this anchor.
     */
    private fun findNearTextAnchor(layout: ScreenLayout, target: ScreenElement?): String? {
        if (target == null) return null
        val tx = target.bounds.centerX()
        val ty = target.bounds.centerY()
        return layout.elements
            .filter { it !== target }
            .filter { !it.text.isNullOrBlank() || !it.contentDescription.isNullOrBlank() }
            .minByOrNull {
                val dx = it.bounds.centerX() - tx
                val dy = it.bounds.centerY() - ty
                dx * dx + dy * dy
            }
            ?.let { it.text?.toString() ?: it.contentDescription?.toString() }
    }

    /**
     * Parse the LLM's JSON response into an [AIDecision].
     */
    private fun parseAIResponse(raw: String): AIDecision {
        val trimmed = raw.trim()
        val jsonStart = trimmed.indexOf('{')
        if (jsonStart == -1) {
            throw IllegalArgumentException("No JSON found in response")
        }

        var jsonStr = trimmed.substring(jsonStart)
        val jsonEnd = jsonStr.lastIndexOf('}')
        if (jsonEnd != -1) {
            jsonStr = jsonStr.substring(0, jsonEnd + 1)
        } else {
            jsonStr = "$jsonStr}"
        }

        val json = try {
            JSONObject(jsonStr)
        } catch (e: Exception) {
            // Regex fallback for partially truncated JSON
            JSONObject().apply {
                Regex("\"action\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.let { put("action", it.groupValues[1]) }
                Regex("\"desc\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.let { put("desc", it.groupValues[1]) }
                Regex("\"x\"\\s*:\\s*(\\d+)").find(raw)?.let { put("x", it.groupValues[1].toIntOrNull() ?: 540) }
                Regex("\"y\"\\s*:\\s*(\\d+)").find(raw)?.let { put("y", it.groupValues[1].toIntOrNull() ?: 960) }
                Regex("\"text\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.let { put("text", it.groupValues[1]) }
                Regex("\"dir\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.let { put("dir", it.groupValues[1]) }
                Regex("\"package\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.let { put("package", it.groupValues[1]) }
                if (raw.contains("\"done\": true") || raw.contains("\"done\":true")) put("done", true)
            }
        }

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
            else -> AgentAction.Wait(ms = 1000)
        }

        return AIDecision(
            action = action,
            description = description,
            isComplete = false
        )
    }
}
