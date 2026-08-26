package com.kairo.assistant.agent

/**
 * Personality-driven status messages for each agent.
 * These are shown in the overlay and spoken via TTS for character.
 */
object AgentCharters {

    /** Fun status messages when an agent starts working. */
    fun getStartMessage(agent: KairoAgent): String = when (agent) {
        KairoAgent.COORDINATOR -> listOf(
            "\uD83D\uDC3A Routing your request...",
            "\uD83D\uDC3A Let me find the right agent for this...",
            "\uD83D\uDC3A I see all, I route all!"
        ).random()

        KairoAgent.SCOUT -> listOf(
            "\uD83D\uDD0D Investigating...",
            "\uD83D\uDD0D Let me dig into that!",
            "\uD83D\uDD0D Knowledge is power! Looking it up...",
            "\uD83D\uDD0D On the case!"
        ).random()

        KairoAgent.RUNNER -> listOf(
            "\u26A1 On it!",
            "\u26A1 No questions, just action!",
            "\u26A1 Executing now!",
            "\u26A1 Lightning fast, watch this!"
        ).random()

        KairoAgent.BUILDER -> listOf(
            "\uD83D\uDD27 Let's build something!",
            "\uD83D\uDD27 Firing up the engines...",
            "\uD83D\uDD27 Complex task? My specialty!",
            "\uD83D\uDD27 Blueprints ready, building..."
        ).random()

        KairoAgent.SCRIBE -> listOf(
            "\u270D\uFE0F Crafting your words...",
            "\u270D\uFE0F Words have power! Let me write...",
            "\u270D\uFE0F Pen is ready!",
            "\u270D\uFE0F Composing something great..."
        ).random()
    }

    /** Fun status messages when an agent completes work. */
    fun getCompleteMessage(agent: KairoAgent): String = when (agent) {
        KairoAgent.COORDINATOR -> "\uD83D\uDC3A Done!"
        KairoAgent.SCOUT -> listOf(
            "\uD83D\uDD0D Found it!",
            "\uD83D\uDD0D Case closed!",
            "\uD83D\uDD0D Here's what I found!"
        ).random()
        KairoAgent.RUNNER -> listOf(
            "\u26A1 Done in a flash!",
            "\u26A1 Mission accomplished!",
            "\u26A1 That was easy!"
        ).random()
        KairoAgent.BUILDER -> listOf(
            "\uD83D\uDD27 Built and delivered!",
            "\uD83D\uDD27 Construction complete!",
            "\uD83D\uDD27 All systems go!"
        ).random()
        KairoAgent.SCRIBE -> listOf(
            "\u270D\uFE0F Words crafted!",
            "\u270D\uFE0F Here's your masterpiece!",
            "\u270D\uFE0F Eloquently written!"
        ).random()
    }

    /** Short labels for the conversation card header. */
    fun getHeaderLabel(agent: KairoAgent, isWorking: Boolean): String {
        val emoji = agent.emoji
        val name = agent.displayName
        return if (isWorking) "$emoji $name is working..." else "$emoji $name"
    }
}