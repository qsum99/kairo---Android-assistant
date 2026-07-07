package com.kairo.assistant.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.kairo.assistant.nlu.models.ParsedCommand

class SmsExecutor : ActionExecutor {

    override fun execute(command: ParsedCommand, context: Context): ActionResult {
        val extra = command.extra
        if (extra.isNullOrBlank()) {
            return ActionResult(false, "No SMS details provided.")
        }

        val parts = extra.split("|")
        val number = parts[0].trim()
        val body = parts.getOrNull(1)?.trim() ?: ""

        if (number == "not_found") {
            return ActionResult(false, "I couldn't find a contact named ${command.target ?: "that user"}.")
        }
        if (number == "disambiguate") {
            return ActionResult(false, "Multiple matches found for ${command.target}. Please disambiguate first.")
        }
        if (number.isEmpty()) {
            return ActionResult(false, "No phone number provided for SMS.")
        }

        return try {
            val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
                putExtra("sms_body", body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(smsIntent)
            ActionResult(true, "Opening SMS to $number")
        } catch (e: Exception) {
            Log.e("SmsExecutor", "Error sending SMS", e)
            ActionResult(false, "Failed to open SMS: ${e.message}")
        }
    }
}
