package com.kairo.assistant.nlu.models

/**
 * Represents a parsed user command with intent classification and extracted slots.
 *
 * @param intent The classified intent type.
 * @param target The primary target (e.g., contact name, app name).
 * @param extra Additional data (e.g., phone number, SMS body, disambiguation string).
 * @param confidence Confidence score of the match (0.0 to 1.0).
 */
data class ParsedCommand(
    val intent: IntentType,
    val target: String?,
    val extra: String?,
    val confidence: Float
)
