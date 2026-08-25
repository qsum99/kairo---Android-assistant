package com.kairo.assistant.automation

import android.util.Log
import com.kairo.assistant.nlu.llm.GeminiClient
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand
import org.json.JSONObject

private const val TAG = "AutomationIntentMatcher"

/**
 * Matches compound "when X, do Y" automation intents.
 *
 * Uses Gemini structured JSON output to parse the trigger and action
 * from natural language — regex is too fragile for the variety of
 * ways users phrase compound commands.
 *
 * Example inputs:
 *   "When I connect to office WiFi, turn off Bluetooth"
 *   "Every day at 7am, play music"
 *   "When battery is low, send SMS to mom saying charge me"
 *   "When I connect to home WiFi, open Spotify"
 */
object AutomationIntentMatcher {

    /**
     * Try to match a transcript as an automation creation command.
     * Returns null if the transcript doesn't look like a "when X, do Y" pattern.
     */
    suspend fun match(
        transcript: String,
        apiKey: String,
        model: String
    ): ParsedCommand? {
        val lower = transcript.lowercase().trim()

        // Quick heuristic: must contain a trigger keyword
        val triggerKeywords = listOf("when ", "every ", "whenever ", "if i ", "on connect", "on disconnect")
        if (triggerKeywords.none { lower.contains(it) }) return null

        // Also must contain an action keyword
        val actionKeywords = listOf("turn ", "open ", "send ", "play ", "pause ", "set ", "toggle ", "disable ", "enable ", "lock ", "mute ")
        if (actionKeywords.none { lower.contains(it) }) return null

        Log.i(TAG, "Attempting Gemini parse for automation: $transcript")

        return try {
            val response = GeminiClient.generateContent(
                prompt = buildParsePrompt(transcript),
                apiKey = apiKey,
                model = model,
                systemInstruction = PARSE_SYSTEM_INSTRUCTION,
                jsonMode = true
            )

            parseStructuredResponse(response, transcript)
        } catch (e: Exception) {
            Log.e(TAG, "Gemini parse failed: ${e.message}")
            // Graceful degradation: return a generic agent task instead
            ParsedCommand(
                intent = IntentType.AGENT_TASK,
                target = null,
                extra = transcript,
                confidence = 0.5f
            )
        }
    }

    private fun buildParsePrompt(transcript: String): String {
        return buildString {
            appendLine("Parse this voice command into a trigger and an action for an automation recipe.")
            appendLine()
            appendLine("VOICE COMMAND: \"$transcript\"")
            appendLine()
            appendLine("Respond with ONLY a JSON object in this exact format:")
            appendLine("""{""")
            appendLine("""  "trigger": {""")
            appendLine("""    "type": "time" | "wifi_connect" | "wifi_disconnect" | "battery_low" | "battery_high",""")
            appendLine("""    "config": {""")
            appendLine("""      // For time: {"hour": 7, "minute": 0}""")
            appendLine("""      // For wifi: {"ssid": "OfficeWiFi"} or {"ssid": "*"} for any""")
            appendLine("""      // For battery: {"level": 20}""")
            appendLine("""    }""")
            appendLine("""  },""")
            appendLine("""  "action": {""")
            appendLine("""    "type": "toggle_setting" | "open_app" | "send_sms" | "toggle_media",""")
            appendLine("""    "config": {""")
            appendLine("""      // For toggle_setting: {"setting": "bluetooth" | "wifi" | "airplane" | "hotspot", "state": "on" | "off"}""")
            appendLine("""      // For open_app: {"package": "com.spotify.music"}""")
            appendLine("""      // For send_sms: {"to": "Mom", "message": "charge me"}""")
            appendLine("""      // For toggle_media: {"state": "play" | "pause"}""")
            appendLine("""    }""")
            appendLine("""  }""")
            appendLine("""}""")
            appendLine()
            appendLine("IMPORTANT: Only output the JSON object, nothing else.")
        }
    }

    private fun parseStructuredResponse(response: String, originalTranscript: String): ParsedCommand? {
        return try {
            val trimmed = response.trim()
            val jsonStart = trimmed.indexOf('{')
            val jsonEnd = trimmed.lastIndexOf('}')
            if (jsonStart == -1 || jsonEnd == -1) return null

            val json = JSONObject(trimmed.substring(jsonStart, jsonEnd + 1))
            val trigger = json.getJSONObject("trigger")
            val action = json.getJSONObject("action")

            val triggerType = trigger.getString("type")
            val triggerConfig = trigger.getJSONObject("config").toString()
            val actionType = action.getString("type")
            val actionConfig = action.getJSONObject("config").toString()

            // Pack everything into the extra field for the executor
            val extra = JSONObject().apply {
                put("triggerType", triggerType)
                put("triggerConfig", triggerConfig)
                put("actionType", actionType)
                put("actionConfig", actionConfig)
                put("rawTranscript", originalTranscript)
            }.toString()

            ParsedCommand(
                intent = IntentType.CREATE_AUTOMATION,
                target = triggerType,
                extra = extra,
                confidence = 0.85f
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse structured response: ${e.message}")
            null
        }
    }

    private const val PARSE_SYSTEM_INSTRUCTION = """You are an automation parser.
Given a voice command, extract the trigger (when something happens) and the action (what to do).
Output ONLY a valid JSON object. No explanations, no markdown, no labels."""
}
