package com.kairo.assistant.agent

import com.kairo.assistant.nlu.models.IntentType

/**
 * The 5 Kairo agents, each with a unique character, color, and responsibility.
 *
 * Kairo (Coordinator) routes all user input to the right specialist agent.
 * Agents can be chained: Scout -> Scribe for "draft a message about what's on my screen."
 */
enum class KairoAgent(
    val displayName: String,
    val emoji: String,
    val colorHex: Long,
    val charter: String,
    val description: String,
    val ttsStyle: String
) {
    COORDINATOR(
        displayName = "Kairo",
        emoji = "K",  // Kairo initial
        colorHex = 0xFF00D2FF,
        charter = "I see all, I route all",
        description = "Central coordinator that orchestrates all agents",
        ttsStyle = "confident and commanding"
    ),
    SCOUT(
        displayName = "Scout",
        emoji = "\uD83D\uDD0D",  // Magnifying glass
        colorHex = 0xFF4A9EFF,
        charter = "Knowledge is power!",
        description = "Research agent — finds answers, reads screens, searches the web",
        ttsStyle = "curious and analytical"
    ),
    RUNNER(
        displayName = "Runner",
        emoji = "\u26A1",  // Lightning bolt
        colorHex = 0xFF00E676,
        charter = "No questions, just action!",
        description = "Execution agent — toggles, calls, sends, controls the device",
        ttsStyle = "quick and decisive"
    ),
    BUILDER(
        displayName = "Builder",
        emoji = "\uD83D\uDD27",  // Wrench
        colorHex = 0xFFFF9800,
        charter = "Let's build something!",
        description = "Work agent — complex multi-step tasks, app automation, ordering",
        ttsStyle = "methodical and focused"
    ),
    SCRIBE(
        displayName = "Scribe",
        emoji = "\u270D\uFE0F",  // Writing hand
        colorHex = 0xFF8B5CF6,
        charter = "Words have power",
        description = "Drafting agent — crafts messages, emails, LinkedIn posts",
        ttsStyle = "articulate and thoughtful"
    );

    companion object {
        /** Route an IntentType to the responsible agent. */
        fun forIntent(intent: IntentType): KairoAgent = when (intent) {
            // Coordinator handles exit and unknowns
            IntentType.EXIT, IntentType.UNKNOWN -> COORDINATOR

            // Scout handles research, understanding, and conversations
            IntentType.GOOGLE_SEARCH, IntentType.BING_SEARCH,
            IntentType.SCREEN_QUERY, IntentType.SCREEN_EXPLAIN,
            IntentType.CONVERSATION -> SCOUT

            // Runner handles direct device actions
            IntentType.CALL, IntentType.SEND_SMS,
            IntentType.TOGGLE_BLUETOOTH, IntentType.TOGGLE_WIFI,
            IntentType.TOGGLE_INTERNET, IntentType.TOGGLE_AIRPLANE,
            IntentType.TOGGLE_HOTSPOT, IntentType.TOGGLE_TORCH,
            IntentType.SET_ALARM, IntentType.OPEN_APP,
            IntentType.OPEN_SETTINGS, IntentType.LOCK_DEVICE,
            IntentType.MEDIA_PLAY, IntentType.MEDIA_PAUSE,
            IntentType.VOLUME_UP, IntentType.VOLUME_DOWN,
            IntentType.SET_VOLUME -> RUNNER

            // Builder handles complex multi-step work
            IntentType.AGENT_TASK, IntentType.ORDER_ITEM,
            IntentType.CREATE_AUTOMATION -> BUILDER

            // Scribe handles message crafting
            IntentType.DRAFT_MESSAGE -> SCRIBE
        }

        /** Get only the satellite agents (excluding coordinator). */
        val satellites: List<KairoAgent>
            get() = listOf(SCOUT, RUNNER, BUILDER, SCRIBE)
    }
}