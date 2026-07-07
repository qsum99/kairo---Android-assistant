package com.kairo.assistant.actions

import android.content.Context
import com.kairo.assistant.nlu.models.ParsedCommand

interface ActionExecutor {
    fun execute(command: ParsedCommand, context: Context): ActionResult
}

data class ActionResult(val success: Boolean, val message: String)
