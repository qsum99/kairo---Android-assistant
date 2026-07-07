package com.kairo.assistant.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.kairo.assistant.nlu.models.ParsedCommand
import java.net.URLEncoder

class BingSearchExecutor : ActionExecutor {
    override fun execute(command: ParsedCommand, context: Context): ActionResult {
        val query = command.target ?: ""
        return try {
            val url = if (query.isBlank()) {
                "https://www.bing.com"
            } else {
                "https://www.bing.com/search?q=${URLEncoder.encode(query, "UTF-8")}"
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            val successMessage = if (query.isBlank()) "Opening Bing" else "Searching Bing for $query"
            ActionResult(true, successMessage)
        } catch (e: Exception) {
            ActionResult(false, "Failed to open Bing search: ${e.message}")
        }
    }
}
