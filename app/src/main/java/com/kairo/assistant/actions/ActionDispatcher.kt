package com.kairo.assistant.actions

import android.content.Context
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand

class ActionDispatcher {

    private val executors: Map<IntentType, ActionExecutor> = mapOf(
        IntentType.CALL to CallExecutor(),
        IntentType.SEND_SMS to SmsExecutor(),
        IntentType.OPEN_APP to OpenAppExecutor(),
        IntentType.SET_ALARM to AlarmExecutor(),
        IntentType.TOGGLE_BLUETOOTH to BluetoothExecutor(),
        IntentType.OPEN_SETTINGS to SettingsExecutor(),
        IntentType.GOOGLE_SEARCH to GoogleSearchExecutor(),
        IntentType.BING_SEARCH to BingSearchExecutor(),
        IntentType.TOGGLE_TORCH to TorchExecutor(),
        IntentType.LOCK_DEVICE to LockDeviceExecutor()
    )

    fun dispatch(command: ParsedCommand, context: Context): ActionResult {
        if (command.intent == IntentType.UNKNOWN) {
            return ActionResult(false, "I didn't understand that")
        }
        val executor = executors[command.intent]
            ?: return ActionResult(false, "I didn't understand that")
        return executor.execute(command, context)
    }
}
