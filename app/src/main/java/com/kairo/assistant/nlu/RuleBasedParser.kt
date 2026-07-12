package com.kairo.assistant.nlu

import com.kairo.assistant.data.AppResolver
import com.kairo.assistant.data.ContactResolver
import com.kairo.assistant.nlu.models.IntentType
import com.kairo.assistant.nlu.models.ParsedCommand
import com.kairo.assistant.nlu.rules.AlarmIntentMatcher
import com.kairo.assistant.nlu.rules.CallIntentMatcher
import com.kairo.assistant.nlu.rules.OpenAppIntentMatcher
import com.kairo.assistant.nlu.rules.SmsIntentMatcher
import com.kairo.assistant.nlu.rules.BluetoothIntentMatcher
import com.kairo.assistant.nlu.rules.SettingsIntentMatcher
import com.kairo.assistant.nlu.rules.GoogleSearchIntentMatcher
import com.kairo.assistant.nlu.rules.BingSearchIntentMatcher
import com.kairo.assistant.nlu.rules.TorchIntentMatcher
import com.kairo.assistant.nlu.rules.LockDeviceIntentMatcher
import com.kairo.assistant.nlu.rules.WifiIntentMatcher
import com.kairo.assistant.nlu.rules.InternetIntentMatcher
import com.kairo.assistant.nlu.rules.GreetingIntentMatcher
import com.kairo.assistant.nlu.rules.ExitIntentMatcher
import com.kairo.assistant.nlu.rules.AirplaneModeIntentMatcher

/**
 * Iterates through a prioritised list of rule-based matchers and returns the
 * first successful match, or an UNKNOWN command if nothing matches.
 */
class RuleBasedParser(
    contactResolver: ContactResolver,
    appResolver: AppResolver
) {

    private val matchers: List<IntentMatcher> = listOf(
        GreetingIntentMatcher(),
        ExitIntentMatcher(),
        GoogleSearchIntentMatcher(),
        BingSearchIntentMatcher(),
        TorchIntentMatcher(),
        LockDeviceIntentMatcher(),
        SettingsIntentMatcher(),
        CallIntentMatcher(contactResolver),
        SmsIntentMatcher(contactResolver),
        OpenAppIntentMatcher(appResolver),
        AlarmIntentMatcher(),
        BluetoothIntentMatcher(),
        WifiIntentMatcher(),
        InternetIntentMatcher(),
        AirplaneModeIntentMatcher()
    )

    /**
     * Attempts to match the transcript against each rule-based matcher in priority order.
     *
     * @param transcript The raw voice transcript to parse.
     * @return The first matching [ParsedCommand], or an UNKNOWN command.
     */
    fun tryMatch(transcript: String): ParsedCommand {
        for (matcher in matchers) {
            val result = matcher.tryMatch(transcript)
            if (result != null) return result
        }

        return ParsedCommand(
            intent = IntentType.UNKNOWN,
            target = null,
            extra = transcript,
            confidence = 0.0f
        )
    }
}
