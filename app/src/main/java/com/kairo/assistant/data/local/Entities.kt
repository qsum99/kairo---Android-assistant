package com.kairo.assistant.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores AI chat conversation history for persistence and context.
 */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Stores learned app navigation flows for the App Guidance system.
 * Each record represents a known sequence of steps to accomplish a task in an app.
 */
@Entity(tableName = "app_knowledge")
data class AppKnowledgeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val taskDescription: String,
    val stepsJson: String, // JSON array of step descriptions
    val lastUsed: Long = System.currentTimeMillis(),
    val successCount: Int = 0
)

/**
 * Stores user-created automation recipes (trigger → action chains).
 */
@Entity(tableName = "automation_recipes")
data class AutomationRecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val triggerType: String,     // e.g., "time", "wifi_connect", "battery_low"
    val triggerConfig: String,   // JSON configuration for the trigger
    val actionType: String,      // e.g., "send_sms", "open_app", "toggle_setting"
    val actionConfig: String,    // JSON configuration for the action
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastTriggered: Long? = null
)

/**
 * Stores message drafts for quick access and history.
 */
@Entity(tableName = "draft_history")
data class DraftHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platform: String,       // "whatsapp", "email", "sms", "linkedin"
    val tone: String,           // "professional", "casual", "urgent", "formal", "friendly"
    val userIntent: String,     // What the user wanted to say
    val generatedDraft: String, // The AI-generated draft
    val timestamp: Long = System.currentTimeMillis()
)
