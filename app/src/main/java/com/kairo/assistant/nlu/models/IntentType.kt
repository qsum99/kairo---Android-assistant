package com.kairo.assistant.nlu.models

/**
 * All supported intent types for the Kairo voice assistant.
 */
enum class IntentType {
    CALL,
    SEND_SMS,
    OPEN_APP,
    SET_ALARM,
    TOGGLE_BLUETOOTH,
    OPEN_SETTINGS,
    GOOGLE_SEARCH,
    BING_SEARCH,
    TOGGLE_TORCH,
    LOCK_DEVICE,
    CONVERSATION,
    UNKNOWN
}
