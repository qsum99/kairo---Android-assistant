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
        IntentType.TOGGLE_WIFI to WifiExecutor(),
        IntentType.TOGGLE_INTERNET to InternetExecutor(),
        IntentType.TOGGLE_AIRPLANE to AirplaneModeExecutor(),
        IntentType.OPEN_SETTINGS to SettingsExecutor(),
        IntentType.GOOGLE_SEARCH to GoogleSearchExecutor(),
        IntentType.BING_SEARCH to BingSearchExecutor(),
        IntentType.TOGGLE_TORCH to TorchExecutor(),
        IntentType.LOCK_DEVICE to LockDeviceExecutor(),
        IntentType.TOGGLE_HOTSPOT to HotspotExecutor(),
        IntentType.MEDIA_PLAY to MediaExecutor(),
        IntentType.MEDIA_PAUSE to MediaExecutor(),
        IntentType.VOLUME_UP to VolumeExecutor(),
        IntentType.VOLUME_DOWN to VolumeExecutor(),
        IntentType.SET_VOLUME to VolumeExecutor()
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
