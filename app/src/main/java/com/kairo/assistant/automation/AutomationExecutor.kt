package com.kairo.assistant.automation

import android.content.Context
import android.util.Log
import com.kairo.assistant.actions.ActionExecutor
import com.kairo.assistant.actions.ActionResult
import com.kairo.assistant.data.local.AutomationRecipeEntity
import com.kairo.assistant.data.local.KairoDatabase
import com.kairo.assistant.nlu.models.ParsedCommand
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

private const val TAG = "AutomationExecutor"

/**
 * Executor for CREATE_AUTOMATION intents.
 *
 * Parses the structured trigger/action JSON from the intent matcher
 * and inserts a new automation recipe into Room DB.
 * The TriggerEngine service will pick it up automatically via Flow.
 */
class AutomationExecutor : ActionExecutor {

    override fun execute(command: ParsedCommand, context: Context): ActionResult {
        val extra = command.extra ?: return ActionResult(
            success = false,
            message = "I couldn't parse the automation. Try again with a clearer command."
        )

        return try {
            val json = JSONObject(extra)
            val triggerType = json.getString("triggerType")
            val triggerConfig = json.getString("triggerConfig")
            val actionType = json.getString("actionType")
            val actionConfig = json.getString("actionConfig")
            val rawTranscript = json.optString("rawTranscript", "")

            // Validate the trigger type
            val validTriggers = setOf("time", "wifi_connect", "wifi_disconnect", "battery_low", "battery_high")
            if (triggerType !in validTriggers) {
                return ActionResult(
                    success = false,
                    message = "I don't know how to trigger on '$triggerType'. I support time, WiFi, and battery triggers."
                )
            }

            // Validate the action type
            val validActions = setOf("toggle_setting", "open_app", "send_sms", "toggle_media")
            if (actionType !in validActions) {
                return ActionResult(
                    success = false,
                    message = "I don't know how to perform '$actionType' actions."
                )
            }

            val recipe = AutomationRecipeEntity(
                name = rawTranscript.ifBlank { "$triggerType → $actionType" },
                triggerType = triggerType,
                triggerConfig = triggerConfig,
                actionType = actionType,
                actionConfig = actionConfig,
                isEnabled = true
            )

            val db = KairoDatabase.getInstance(context)
            val id = runBlocking { db.automationRecipeDao().insert(recipe) }
            Log.i(TAG, "Created automation recipe #$id: ${recipe.name}")

            val triggerDesc = describeTrigger(triggerType, triggerConfig)
            val actionDesc = describeAction(actionType, actionConfig)

            ActionResult(
                success = true,
                message = "Automation created! When $triggerDesc, I'll $actionDesc. I'll run it automatically."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create automation", e)
            ActionResult(
                success = false,
                message = "Failed to create the automation: ${e.message}"
            )
        }
    }

    private fun describeTrigger(type: String, config: String): String {
        val json = JSONObject(config)
        return when (type) {
            "time" -> {
                val hour = json.optInt("hour", 0)
                val minute = json.optInt("minute", 0)
                val period = if (hour < 12) "AM" else "PM"
                val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                "it's $displayHour:${minute.toString().padStart(2, '0')} $period"
            }
            "wifi_connect" -> {
                val ssid = json.optString("ssid", "*")
                if (ssid == "*" || ssid.isBlank()) "I connect to any WiFi" else "I connect to WiFi '$ssid'"
            }
            "wifi_disconnect" -> {
                val ssid = json.optString("ssid", "*")
                if (ssid == "*" || ssid.isBlank()) "I disconnect from WiFi" else "I disconnect from WiFi '$ssid'"
            }
            "battery_low" -> "battery drops to ${json.optInt("level", 20)}%"
            "battery_high" -> "battery reaches ${json.optInt("level", 80)}%"
            else -> type
        }
    }

    private fun describeAction(type: String, config: String): String {
        val json = JSONObject(config)
        return when (type) {
            "toggle_setting" -> {
                val setting = json.optString("setting", "something")
                val state = json.optString("state", "on")
                "$setting $state"
            }
            "open_app" -> "open ${json.optString("package", "an app")}"
            "send_sms" -> "send SMS to ${json.optString("to", "someone")}"
            "toggle_media" -> {
                val state = json.optString("state", "play")
                if (state == "play") "play music" else "pause music"
            }
            else -> "perform an action"
        }
    }
}
