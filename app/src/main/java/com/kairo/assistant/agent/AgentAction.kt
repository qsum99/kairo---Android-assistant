package com.kairo.assistant.agent

import com.kairo.assistant.screen.ScrollDirection

/**
 * Sealed class representing all actions the AI agent can perform.
 * Each action maps to a gesture or system interaction.
 */
sealed class AgentAction {
    /** Tap at specific screen coordinates */
    data class Tap(val x: Int, val y: Int, val description: String = "") : AgentAction()

    /** Type text into the currently focused input field */
    data class TypeText(val text: String) : AgentAction()

    /** Scroll in a direction */
    data class Scroll(val direction: ScrollDirection) : AgentAction()

    /** Swipe between two points */
    data class Swipe(
        val x1: Int, val y1: Int,
        val x2: Int, val y2: Int,
        val description: String = ""
    ) : AgentAction()

    /** Open an app by package name */
    data class OpenApp(val packageName: String) : AgentAction()

    /** Press the system back button */
    data object PressBack : AgentAction()

    /** Press the system home button */
    data object PressHome : AgentAction()

    /** Wait for a specified duration */
    data class Wait(val ms: Long) : AgentAction()

    /** Task is complete */
    data class Complete(val summary: String) : AgentAction()
}

/**
 * Represents the AI's decision for the next step in an autonomous task.
 */
data class AIDecision(
    val action: AgentAction,
    val description: String,
    val isComplete: Boolean,
    val summary: String? = null,
    val confidence: Float = 0.5f
)

/**
 * Status of an ongoing agent task.
 */
enum class AgentStatus {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Progress update for an agent task.
 */
data class AgentProgress(
    val status: AgentStatus = AgentStatus.IDLE,
    val currentStep: Int = 0,
    val maxSteps: Int = 15,
    val currentAction: String = "",
    val taskDescription: String = "",
    val error: String? = null
)
