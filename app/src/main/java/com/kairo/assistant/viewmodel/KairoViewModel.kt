package com.kairo.assistant.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kairo.assistant.actions.ActionDispatcher
import com.kairo.assistant.data.AppResolver
import com.kairo.assistant.data.ContactResolver
import com.kairo.assistant.nlu.CommandRouter
import com.kairo.assistant.nlu.RuleBasedParser
import com.kairo.assistant.nlu.llm.LlamaEngine
import com.kairo.assistant.nlu.llm.LlmParser
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand
import com.kairo.assistant.stt.SpeechToTextManager
import com.kairo.assistant.tts.KairoTTS
import com.kairo.assistant.ui.components.AssistantStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "KairoViewModel"

/**
 * Represents a single conversation turn.
 */
data class ConversationItem(
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * UI state for the main Kairo screen.
 */
data class KairoUiState(
    val status: AssistantStatus = AssistantStatus.IDLE,
    val transcript: String = "",
    val response: String = "",
    val conversationHistory: List<ConversationItem> = emptyList(),
    val disambiguationOptions: List<Pair<String, String>> = emptyList(),
    val simOptions: List<Pair<String, String>> = emptyList(),
    val shouldExit: Boolean = false,
    val llmStatus: String = "", // "loading", "ready", "unavailable", or error message
    val llmDownloadProgress: Float? = null,
    val isLlmDownloading: Boolean = false
)

/**
 * Main ViewModel orchestrating the full pipeline:
 * Mic → STT → Rule Parser (→ LLM fallback) → Action Executor → TTS
 */
class KairoViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(KairoUiState())
    val uiState: StateFlow<KairoUiState> = _uiState.asStateFlow()

    private var pendingIntent: IntentType? = null
    private var pendingSmsBody: String? = null
    private var pendingSpokenName: String? = null
    private var pendingSimNumber: String? = null

    // ── Components (initialized lazily) ──
    private val contactResolver: ContactResolver by lazy {
        ContactResolver(application)
    }
    private val appResolver: AppResolver by lazy {
        AppResolver(application)
    }
    private val ruleParser: RuleBasedParser by lazy {
        RuleBasedParser(contactResolver, appResolver)
    }
    private val llmParser: LlmParser by lazy {
        LlmParser(application)
    }
    private val router: CommandRouter by lazy {
        CommandRouter(ruleParser, llmParser)
    }
    private val dispatcher: ActionDispatcher by lazy {
        ActionDispatcher()
    }
    private val tts: KairoTTS by lazy {
        KairoTTS(application)
    }
    private val sttManager: SpeechToTextManager by lazy {
        SpeechToTextManager(application)
    }

    private var activeContextRef: java.lang.ref.WeakReference<Context>? = null
    private var isStoppingIntentionally = false

    fun updateActiveContext(context: Context) {
        this.activeContextRef = java.lang.ref.WeakReference(context)
    }

    private fun getActiveContext(): Context {
        return activeContextRef?.get() ?: getApplication<Application>()
    }

    init {
        // Pre-warm SpeechToTextManager to bind Google Speech Services IPC immediately on launch
        sttManager.prewarm()

        // Asynchronously pre-load contacts and apps in background to prevent blocking Main thread
        viewModelScope.launch(Dispatchers.IO) {
            try {
                contactResolver.loadContacts()
                appResolver.loadApps()
                Log.d(TAG, "Contacts and Apps pre-loaded successfully on background thread")
            } catch (e: Exception) {
                Log.e(TAG, "Error pre-loading contacts or apps", e)
            }
        }

        // Collect model download progress
        viewModelScope.launch(Dispatchers.IO) {
            LlamaEngine.downloadProgressFlow.collect { progress ->
                _uiState.update {
                    it.copy(
                        llmDownloadProgress = progress,
                        isLlmDownloading = progress != null,
                        llmStatus = if (progress != null) "downloading (${(progress * 100).toInt()}%)" else it.llmStatus
                    )
                }
            }
        }

        // Initialize the on-device LLM in the background, delayed to prioritize UI launch rendering
        viewModelScope.launch(Dispatchers.IO) {
            try {
                kotlinx.coroutines.delay(2000) // Delay model load to allow smooth Compose rendering on launch
                val prefs = application.getSharedPreferences("kairo_prefs", Context.MODE_PRIVATE)
                val activityManager = application.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val memoryInfo = android.app.ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memoryInfo)
                val isLowRam = memoryInfo.totalMem < 4831838208L // 4.5 GB
                
                val isFallbackEnabled = prefs.getBoolean("llm_fallback_enabled", !isLowRam)
                
                if (!isFallbackEnabled) {
                    _uiState.update { it.copy(llmStatus = if (isLowRam) "disabled (low RAM)" else "disabled") }
                    Log.d(TAG, "LLM fallback is disabled. Skipping initialization.")
                    return@launch
                }

                _uiState.update { it.copy(llmStatus = "loading") }
                Log.d(TAG, "Initializing on-device LLM...")
                LlamaEngine.initialize(application)
                if (LlamaEngine.isAvailable) {
                    _uiState.update { it.copy(llmStatus = "ready") }
                    Log.d(TAG, "LLM ready for inference")
                } else {
                    val error = LlamaEngine.loadError ?: "Model not available"
                    _uiState.update { it.copy(llmStatus = error) }
                    Log.w(TAG, "LLM not available: $error")
                }
            } catch (e: Throwable) {
                Log.e(TAG, "LLM initialization failed", e)
                _uiState.update { it.copy(llmStatus = "error: ${e.message ?: e.toString()}") }
            }
        }
    }

    /**
     * Download the on-device model file in the background.
     */
    fun downloadLlmModel() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(llmStatus = "downloading (0%)") }
            val success = LlamaEngine.downloadModel(getApplication())
            if (success) {
                _uiState.update { it.copy(llmStatus = "ready") }
            } else {
                val err = LlamaEngine.downloadError ?: "Download failed"
                _uiState.update { it.copy(llmStatus = err) }
            }
        }
    }

    /**
     * Toggles LLM fallback and manages its lifecycle.
     */
    fun setLlmFallbackEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val prefs = context.getSharedPreferences("kairo_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("llm_fallback_enabled", enabled).apply()
            
            if (enabled) {
                try {
                    _uiState.update { it.copy(llmStatus = "loading") }
                    Log.d(TAG, "Initializing LLM on user request...")
                    LlamaEngine.initialize(context)
                    if (LlamaEngine.isAvailable) {
                        _uiState.update { it.copy(llmStatus = "ready") }
                    } else {
                        val error = LlamaEngine.loadError ?: "Model not available"
                        _uiState.update { it.copy(llmStatus = error) }
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "LLM initialization failed", e)
                    _uiState.update { it.copy(llmStatus = "error: ${e.message ?: e.toString()}") }
                }
            } else {
                Log.d(TAG, "Disabling LLM and releasing resources...")
                LlamaEngine.shutdown()
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                val memoryInfo = android.app.ActivityManager.MemoryInfo()
                activityManager.getMemoryInfo(memoryInfo)
                val isLowRam = memoryInfo.totalMem < 4831838208L
                _uiState.update { it.copy(llmStatus = if (isLowRam) "disabled (low RAM)" else "disabled") }
            }
        }
    }
    /**
     * Called when the user taps the mic button.
     * Toggles between listening and idle states.
     */
    fun onMicButtonClicked() {
        if (_uiState.value.status == AssistantStatus.LISTENING) {
            stopListening()
        } else {
            startListening()
        }
    }

    fun submitQuery(queryText: String) {
        if (queryText.isNotBlank()) {
            tts.stop()
            _uiState.update {
                it.copy(
                    transcript = queryText,
                    response = "",
                    status = AssistantStatus.PROCESSING
                )
            }
            viewModelScope.launch(Dispatchers.Main) {
                processTranscript(queryText)
            }
        }
    }

    fun resetExitState() {
        _uiState.update {
            it.copy(shouldExit = false)
        }
    }

    private fun startListening() {
        isStoppingIntentionally = false
        val prefs = getApplication<Application>().getSharedPreferences("kairo_prefs", Context.MODE_PRIVATE)
        val micMuted = prefs.getBoolean("mic_muted", false)
        if (micMuted) {
            val responseText = "Microphone is muted. Please unmute to speak."
            _uiState.update {
                it.copy(
                    status = AssistantStatus.IDLE,
                    response = responseText
                )
            }
            tts.speak(responseText)
            return
        }

        val ttsWasSpeaking = tts.isSpeaking()
        tts.stop() // Silence active voice feedback immediately before activating microphone
        _uiState.update {
            it.copy(
                status = AssistantStatus.LISTENING,
                transcript = ""
            )
        }

        viewModelScope.launch(Dispatchers.Main) {
            if (ttsWasSpeaking) {
                kotlinx.coroutines.delay(50) // Small 50ms buffer only if TTS was actively speaking
            }
            sttManager.startListening(
                onResult = { text ->
                    Log.d(TAG, "STT result: $text")
                    if (text.isNotBlank()) {
                        processTranscript(text)
                    } else {
                        _uiState.update {
                            it.copy(
                                status = AssistantStatus.IDLE,
                                response = "I didn't catch that. Try again?"
                            )
                        }
                    }
                },
                onPartialResult = { partial ->
                    Log.d(TAG, "STT partial: $partial")
                    _uiState.update {
                        it.copy(
                            transcript = partial,
                            response = "" // Clear previous response bubble as soon as new voice input is registered
                        )
                    }
                },
                onError = { error ->
                    if (!isStoppingIntentionally) {
                        Log.e(TAG, "STT error: $error")
                        val isTransient = error.contains("timeout", ignoreCase = true) || 
                                         error.contains("no match", ignoreCase = true) ||
                                         error.contains("busy", ignoreCase = true)
                        
                        if (isTransient) {
                            viewModelScope.launch(Dispatchers.Main) {
                                val prompt = "I didn't catch that. Please say it again."
                                _uiState.update {
                                    it.copy(
                                        status = AssistantStatus.IDLE,
                                        response = prompt
                                    )
                                }
                                tts.speak(prompt)
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    status = AssistantStatus.ERROR,
                                    response = error
                                )
                            }
                            // Reset to idle after a brief delay
                            viewModelScope.launch {
                                kotlinx.coroutines.delay(2000)
                                _uiState.update {
                                    if (it.status == AssistantStatus.ERROR) {
                                        it.copy(status = AssistantStatus.IDLE)
                                    } else it
                                }
                            }
                        }
                    } else {
                        Log.d(TAG, "Ignored STT error from intentional stop: $error")
                    }
                }
            )
        }
    }

    fun stopListening() {
        isStoppingIntentionally = true
        sttManager.stopListening()
        _uiState.update {
            it.copy(status = AssistantStatus.IDLE)
        }
    }

    /**
     * Process a recognized transcript through the NLU pipeline:
     * Parse → Execute → TTS confirm
     */
    private fun processTranscript(text: String) {
        val currentState = _uiState.value
        if (currentState.simOptions.isNotEmpty()) {
            val options = currentState.simOptions
            if (options.isNotEmpty()) {
                val cleanedText = text.trim().lowercase()
                val index = when {
                    cleanedText.contains("first") || cleanedText.contains("1st") || cleanedText.contains("one") -> 0
                    cleanedText.contains("second") || cleanedText.contains("2nd") || cleanedText.contains("two") -> 1
                    cleanedText.contains("third") || cleanedText.contains("3rd") || cleanedText.contains("three") -> 2
                    cleanedText.contains("last") -> options.size - 1
                    else -> {
                        val labels = options.map { it.first }
                        val best = com.kairo.assistant.nlu.FuzzyMatch.bestMatch(text, labels, 0.6f)
                        if (best != null) options.indexOfFirst { it.first == best.first } else -1
                    }
                }
                if (index in options.indices) {
                    onSimSelected(options[index])
                    return
                } else {
                    viewModelScope.launch(Dispatchers.IO) {
                        val errorPrompt = "I didn't get that. Please select SIM 1 or SIM 2."
                        _uiState.update {
                            it.copy(
                                status = AssistantStatus.SPEAKING,
                                response = errorPrompt
							)
						}
                        launch(Dispatchers.Main) {
                            tts.speak(errorPrompt) {
                                viewModelScope.launch(Dispatchers.Main) {
                                    startListening()
                                }
                            }
                        }
                    }
                    return
                }
            }
        }

        if (currentState.disambiguationOptions.isNotEmpty()) {
            val options = currentState.disambiguationOptions
            if (options.isNotEmpty()) {
                val cleanedText = text.trim().lowercase()
                val index = when {
                    cleanedText.contains("first") || cleanedText.contains("1st") || cleanedText.contains("one") -> 0
                    cleanedText.contains("second") || cleanedText.contains("2nd") || cleanedText.contains("two") -> 1
                    cleanedText.contains("third") || cleanedText.contains("3rd") || cleanedText.contains("three") -> 2
                    cleanedText.contains("last") -> options.size - 1
                    else -> {
                        val names = options.map { it.first }
                        val best = com.kairo.assistant.nlu.FuzzyMatch.bestMatch(text, names, 0.6f)
                        if (best != null) options.indexOfFirst { it.first == best.first } else -1
                    }
                }
                if (index in options.indices) {
                    onDisambiguationSelected(options[index])
                    return
                } else {
                    viewModelScope.launch(Dispatchers.IO) {
                        val errorPrompt = "I didn't get that. Please select one on screen or say first or second."
                        _uiState.update {
                            it.copy(
                                status = AssistantStatus.SPEAKING,
                                response = errorPrompt
                            )
                        }
                        launch(Dispatchers.Main) {
                            tts.speak(errorPrompt)
                        }
                        kotlinx.coroutines.delay(2500)
                        _uiState.update {
                            it.copy(status = AssistantStatus.DISAMBIGUATING, response = currentState.response)
                        }
                    }
                    return
                }
            }
        }

        _uiState.update {
            it.copy(
                status = AssistantStatus.PROCESSING,
                transcript = text
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Parse the transcript
                val command = router.parse(text)
                Log.d(TAG, "Parsed command: $command")

                val extra = command.extra ?: ""
                if (extra.startsWith("disambiguate")) {
                    val parts = extra.split("|")
                    val isSms = command.intent == IntentType.SEND_SMS
                    val smsBody = if (isSms) parts.getOrNull(1) ?: "" else ""
                    val contactStartIndex = if (isSms) 2 else 1
                    
                    val options = parts.drop(contactStartIndex).mapNotNull { part ->
                        val subParts = part.split(":")
                        if (subParts.size >= 2) {
                            Pair(subParts[0], subParts[1])
                        } else null
                    }
                    
                    if (options.isNotEmpty()) {
                        pendingIntent = command.intent
                        pendingSmsBody = if (isSms) smsBody else null
                        pendingSpokenName = command.target
                        
                        val namesList = options.joinToString(" or ") { it.first }
                        val disambiguationPrompt = "Did you mean $namesList?"
                        
                        _uiState.update {
                            it.copy(
                                status = AssistantStatus.DISAMBIGUATING,
                                response = disambiguationPrompt,
                                disambiguationOptions = options
                            )
                        }
                        
                        launch(Dispatchers.Main) {
                            tts.speak(disambiguationPrompt) {
                                viewModelScope.launch(Dispatchers.Main) {
                                    startListening()
                                }
                            }
                        }
                        return@launch
                    }
                }

                // 2. Execute the action
                val context = getActiveContext()
                val result = when (command.intent) {
                    IntentType.UNKNOWN -> {
                        logUnknownCommand(text)
                        com.kairo.assistant.actions.ActionResult(
                            success = false,
                            message = "I didn't recognize that request. I can help you call contacts, send SMS messages, open applications, or run offline chat queries."
                        )
                    }
                    IntentType.CONVERSATION -> {
                        // LLM generated a conversational response — speak it
                        val response = command.extra ?: "I couldn't process that query. Please try rephrasing it."
                        com.kairo.assistant.actions.ActionResult(
                            success = true,
                            message = response
                        )
                    }
                    IntentType.EXIT -> {
                        val farewells = listOf(
                            "Goodbye! Have a nice day!",
                            "Bye! See you soon!",
                            "Goodbye! Have a great day!",
                            "Bye! Talk to you later!"
                        )
                        val response = farewells[kotlin.random.Random.nextInt(farewells.size)]
                        com.kairo.assistant.actions.ActionResult(
                            success = true,
                            message = response
                        )
                    }
                    else -> {
                        dispatcher.dispatch(command, context)
                    }
                }

                Log.d(TAG, "Action result: $result")

                handleActionResult(result, text, command.intent)

            } catch (e: Exception) {
                Log.e(TAG, "Error processing transcript", e)
                _uiState.update {
                    it.copy(
                        status = AssistantStatus.ERROR,
                        response = "Something went wrong: ${e.message}"
                    )
                }
                kotlinx.coroutines.delay(2000)
                _uiState.update {
                    if (it.status == AssistantStatus.ERROR) {
                        it.copy(status = AssistantStatus.IDLE)
                    } else it
                }
            }
        }
    }

    fun onDisambiguationSelected(contact: Pair<String, String>) {
        val intent = pendingIntent ?: return
        val smsBody = pendingSmsBody
        val spoken = pendingSpokenName
        
        if (spoken != null) {
            val context = getActiveContext()
            val prefs = context.getSharedPreferences("kairo_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("contact_pref_${spoken.trim().lowercase()}", contact.first).apply()
        }
        
        _uiState.update {
            it.copy(
                status = AssistantStatus.PROCESSING,
                disambiguationOptions = emptyList()
            )
        }
        
        pendingIntent = null
        pendingSmsBody = null
        pendingSpokenName = null
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getActiveContext()
                val finalCommand = if (intent == IntentType.SEND_SMS) {
                    ParsedCommand(
                        intent = IntentType.SEND_SMS,
                        target = contact.first,
                        extra = "${contact.second}|$smsBody",
                        confidence = 0.9f
                    )
                } else {
                    ParsedCommand(
                        intent = IntentType.CALL,
                        target = contact.first,
                        extra = contact.second,
                        confidence = 0.9f
                    )
                }
                
                val result = dispatcher.dispatch(finalCommand, context)
                
                handleActionResult(result, "Selected ${contact.first}", finalCommand.intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error executing disambiguated option", e)
                _uiState.update {
                    it.copy(status = AssistantStatus.ERROR, response = e.message ?: "Error")
                }
                kotlinx.coroutines.delay(2000)
                _uiState.update {
                    if (it.status == AssistantStatus.ERROR) {
                        it.copy(status = AssistantStatus.IDLE)
                    } else it
                }
            }
        }
    }

    fun onDisambiguationCancelled() {
        pendingIntent = null
        pendingSmsBody = null
        _uiState.update {
            it.copy(
                status = AssistantStatus.IDLE,
                disambiguationOptions = emptyList(),
                response = "Cancelled"
            )
        }
    }

    /**
     * Called after permissions are granted to refresh data caches.
     */
    fun onPermissionsGranted() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                contactResolver.refresh()
                appResolver.refresh()
                Log.d(TAG, "Refreshed contact and app resolvers after permission grant")
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing resolvers", e)
            }
        }
    }

    fun onSimSelected(sim: Pair<String, String>) {
        val target = pendingSpokenName ?: ""
        val number = pendingSimNumber ?: ""

        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val prefs = context.getSharedPreferences("kairo_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("contact_sim_pref_${target.trim().lowercase()}", sim.second).apply()
            Log.d(TAG, "Saved SIM preference: ${sim.first} for contact: $target")
        }

        _uiState.update {
            it.copy(
                status = AssistantStatus.PROCESSING,
                simOptions = emptyList()
            )
        }

        pendingSpokenName = null
        pendingSimNumber = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getActiveContext()
                val finalCommand = ParsedCommand(
                    intent = IntentType.CALL,
                    target = target,
                    extra = "$number|selected_sim|${sim.second}",
                    confidence = 0.9f
                )
                val result = dispatcher.dispatch(finalCommand, context)
                handleActionResult(result, "Selected SIM ${sim.first}", finalCommand.intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error executing call after SIM selection", e)
            }
        }
    }

    fun onSimCancelled() {
        pendingSpokenName = null
        pendingSimNumber = null
        _uiState.update {
            it.copy(
                status = AssistantStatus.IDLE,
                simOptions = emptyList(),
                response = "Cancelled"
            )
        }
    }

    private fun handleActionResult(
        result: com.kairo.assistant.actions.ActionResult,
        userText: String,
        intentType: IntentType
    ) {
        if (!result.success && result.message.startsWith("select_sim")) {
            val parts = result.message.split("|", limit = 4)
            val targetName = parts.getOrNull(1) ?: ""
            val number = parts.getOrNull(2) ?: ""
            val simListRaw = parts.getOrNull(3) ?: ""
            val simOptions = simListRaw.split(";").mapNotNull { simStr ->
                val subParts = simStr.split("|")
                if (subParts.size >= 2) {
                    Pair(subParts[0], subParts[1])
                } else null
            }
            if (simOptions.isNotEmpty()) {
                pendingSpokenName = targetName
                pendingSimNumber = number

                val simPrompt = "Which SIM would you like to use?"

                _uiState.update {
                    it.copy(
                        status = AssistantStatus.DISAMBIGUATING_SIM,
                        response = simPrompt,
                        simOptions = simOptions
                    )
                }

                viewModelScope.launch(Dispatchers.Main) {
                    tts.speak(simPrompt) {
                        viewModelScope.launch(Dispatchers.Main) {
                            startListening()
                        }
                    }
                }
                return
            }
        }

        if (!result.success) {
            _uiState.update {
                it.copy(
                    status = AssistantStatus.SPEAKING,
                    response = result.message,
                    conversationHistory = it.conversationHistory + listOf(
                        ConversationItem(isUser = true, text = userText),
                        ConversationItem(isUser = false, text = result.message)
                    )
                )
            }

            viewModelScope.launch(Dispatchers.Main) {
                tts.speak(result.message) {
                    viewModelScope.launch(Dispatchers.Main) {
                        startListening()
                    }
                }
            }
            return
        }

        val isImmediateExitIntent = result.success && (
            result.message.startsWith("Opening") || 
            result.message.startsWith("Searching") || 
            result.message.startsWith("Bye") || 
            result.message.startsWith("Goodbye")
        )

        _uiState.update {
            it.copy(
                status = AssistantStatus.SPEAKING,
                response = result.message,
                conversationHistory = it.conversationHistory + listOf(
                    ConversationItem(isUser = true, text = userText),
                    ConversationItem(isUser = false, text = result.message)
                )
            )
        }

        // Only auto-exit for explicit exits or when launching an external activity (where the overlay would block the app)
        val shouldAutoExit = when (intentType) {
            IntentType.EXIT,
            IntentType.OPEN_APP,
            IntentType.OPEN_SETTINGS,
            IntentType.SET_ALARM,
            IntentType.GOOGLE_SEARCH,
            IntentType.BING_SEARCH,
            IntentType.CALL -> true
            else -> false
        }

        if (!shouldAutoExit) {
            viewModelScope.launch(Dispatchers.Main) {
                tts.speak(result.message) {
                    viewModelScope.launch(Dispatchers.Main) {
                        _uiState.update {
                            if (it.status == AssistantStatus.SPEAKING) {
                                it.copy(status = AssistantStatus.IDLE)
                            } else it
                        }
                    }
                }
            }
        } else if (isImmediateExitIntent) {
            viewModelScope.launch(Dispatchers.Main) {
                tts.speak(result.message) {
                    viewModelScope.launch(Dispatchers.Main) {
                        _uiState.update {
                            if (it.status == AssistantStatus.SPEAKING) {
                                it.copy(status = AssistantStatus.IDLE, shouldExit = true)
                            } else it
                        }
                    }
                }
            }
        } else {
            viewModelScope.launch(Dispatchers.Main) {
                var speechCompleted = false
                var minTimeElapsed = false

                val triggerExit = {
                    if (speechCompleted && minTimeElapsed) {
                        _uiState.update {
                            if (it.status == AssistantStatus.SPEAKING) {
                                it.copy(status = AssistantStatus.IDLE, shouldExit = true)
                            } else it
                        }
                    }
                }

                // Minimum screen display duration of 3 seconds to allow reading
                viewModelScope.launch(Dispatchers.IO) {
                    kotlinx.coroutines.delay(3000)
                    minTimeElapsed = true
                    viewModelScope.launch(Dispatchers.Main) {
                        triggerExit()
                    }
                }

                // Speak and wait for speech completion + 1 second post-speech buffer
                tts.speak(result.message) {
                    viewModelScope.launch(Dispatchers.Main) {
                        kotlinx.coroutines.delay(1000)
                        speechCompleted = true
                        triggerExit()
                    }
                }
            }
        }
    }

    private fun logUnknownCommand(text: String) {
        try {
            val context = getApplication<Application>()
            val file = java.io.File(context.filesDir, "unknown_commands.txt")
            file.appendText("$text\n")
            Log.d(TAG, "Logged unknown command: $text")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log unknown command", e)
        }
    }

    fun stopSpeaking() {
        viewModelScope.launch(Dispatchers.Main) {
            tts.stop()
            _uiState.update {
                if (it.status == AssistantStatus.SPEAKING) {
                    it.copy(status = AssistantStatus.IDLE)
                } else it
            }
        }
    }

    fun startListeningAutomatic() {
        if (_uiState.value.status != AssistantStatus.LISTENING) {
            onMicButtonClicked()
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts.shutdown()
        sttManager.destroy()
        LlamaEngine.shutdown()
    }
}
