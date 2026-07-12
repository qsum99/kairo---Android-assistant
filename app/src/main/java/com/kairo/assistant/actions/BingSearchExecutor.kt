package com.kairo.assistant.actions

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.kairo.assistant.nlu.models.ParsedCommand
import java.net.URLEncoder

class BingSearchExecutor : ActionExecutor {
    override fun execute(command: ParsedCommand, context: Context): ActionResult {
        val query = command.target ?: ""
        val url = if (query.isBlank()) {
            "https://www.bing.com"
        } else {
            "https://www.bing.com/search?q=${URLEncoder.encode(query, "UTF-8")}"
        }
        return try {
            val bingAppIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                setPackage("com.microsoft.bing")
            }
            context.startActivity(bingAppIntent)
            val successMessage = if (query.isBlank()) "Opening Bing app" else "Searching Bing app for $query"
            ActionResult(true, successMessage)
        } catch (e: Exception) {
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(browserIntent)
                val successMessage = if (query.isBlank()) "Opening Bing in browser" else "Searching Bing for $query"
                ActionResult(true, successMessage)
            } catch (fallbackException: Exception) {
                ActionResult(false, "Failed to open Bing search: ${fallbackException.message}")
            }
        }
    }
}
