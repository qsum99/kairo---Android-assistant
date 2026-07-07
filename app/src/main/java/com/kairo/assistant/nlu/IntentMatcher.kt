package com.kairo.assistant.nlu

import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

interface IntentMatcher {
    val intentType: IntentType
    fun tryMatch(transcript: String): ParsedCommand?
}
