package com.kairo.assistant.nlu.rules

import com.kairo.assistant.nlu.IntentMatcher
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

/**
 * Matches alarm-related intents such as:
 *   "set alarm for 7:30 am"
 *   "set an alarm for 6 pm"
 *   "wake me up at 5:45 am"
 *   "set alarm for 14:00"
 */
class AlarmIntentMatcher : IntentMatcher {

    override val intentType: IntentType = IntentType.SET_ALARM

    companion object {
        // "set (an) alarm for HH:MM am/pm" or "wake me up at HH:MM am/pm"
        private val PATTERN = Regex(
            """(?:set\s+(?:an?\s+)?alarm\s+(?:for|at|to)|wake\s+me\s+up\s+(?:at|for|to)|alarm\s+(?:for|at|to))\s+(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""",
            RegexOption.IGNORE_CASE
        )
    }

    override fun tryMatch(transcript: String): ParsedCommand? {
        val input = transcript.trim()

        val match = PATTERN.find(input) ?: return null

        val hourRaw = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].let { if (it.isEmpty()) 0 else it.toIntOrNull() ?: 0 }
        val amPm = match.groupValues[3].lowercase().ifEmpty { null }

        val hour24 = convertTo24Hour(hourRaw, amPm) ?: return null

        if (hour24 !in 0..23 || minute !in 0..59) return null

        val timeFormatted = String.format("%02d:%02d", hour24, minute)

        return ParsedCommand(
            intent = IntentType.SET_ALARM,
            target = timeFormatted,
            extra = null,
            confidence = 0.9f
        )
    }

    /**
     * Converts an hour value with optional am/pm to 24-hour format.
     */
    private fun convertTo24Hour(hour: Int, amPm: String?): Int? {
        return when {
            amPm == null -> {
                // Assume 24-hour format if no am/pm specified
                if (hour in 0..23) hour else null
            }
            amPm == "am" -> {
                when {
                    hour == 12 -> 0       // 12 AM = 00:00
                    hour in 1..11 -> hour
                    else -> null
                }
            }
            amPm == "pm" -> {
                when {
                    hour == 12 -> 12      // 12 PM = 12:00
                    hour in 1..11 -> hour + 12
                    else -> null
                }
            }
            else -> null
        }
    }
}
