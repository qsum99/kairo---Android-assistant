"""
Kairo — Action Dispatcher Module
Maps parsed Intent objects to executable actions.
On PC, actions are simulated (print + response string).
On Android, these will map to real system APIs.
"""

from __future__ import annotations

from datetime import datetime

from intent_parser import Intent


class ActionDispatcher:
    """
    Maps Intent objects to actions and generates spoken responses.
    All actions are simulated on PC — they print what would happen
    and return a response string for TTS.
    """

    def __init__(self):
        # Map intent names → handler methods
        self._handlers: dict[str, callable] = {
            "CALL": self._handle_call,
            "SMS": self._handle_sms,
            "ALARM": self._handle_alarm,
            "TIMER": self._handle_timer,
            "OPEN_APP": self._handle_open_app,
            "PLAY_MUSIC": self._handle_play_music,
            "WEATHER": self._handle_weather,
            "TIME": self._handle_time,
            "VOLUME": self._handle_volume,
            "BRIGHTNESS": self._handle_brightness,
            "FLASHLIGHT": self._handle_flashlight,
            "UNKNOWN": self._handle_unknown,
        }

    def dispatch(self, intent: Intent) -> str:
        """
        Execute the action for a given intent.

        Args:
            intent: Parsed Intent object from the parser.

        Returns:
            Response string to be spoken by TTS.
        """
        handler = self._handlers.get(intent.name, self._handle_unknown)
        response = handler(intent)
        print(f"[ACTION] {intent.name} -> {response}")
        return response

    # ── Individual Handlers ──────────────────────────────────────────────

    def _handle_call(self, intent: Intent) -> str:
        contact = intent.slots.get("contact_name", "unknown contact")
        # SIMULATED: On Android, this would launch ACTION_CALL intent
        print(f"  [CALL] [SIM] Initiating call to: {contact}")
        return f"Calling {contact}"

    def _handle_sms(self, intent: Intent) -> str:
        contact = intent.slots.get("contact_name", "unknown contact")
        message = intent.slots.get("message", "")
        if message:
            print(f"  [SMS] [SIM] Sending SMS to {contact}: \"{message}\"")
            return f"Sending message to {contact}: {message}"
        else:
            print(f"  [SMS] [SIM] Opening SMS to {contact} (no message body)")
            return f"What would you like to say to {contact}?"

    def _handle_alarm(self, intent: Intent) -> str:
        time_str = intent.slots.get("time", "unknown time")
        # SIMULATED: On Android, this would use AlarmManager
        print(f"  [ALARM] [SIM] Setting alarm for: {time_str}")
        return f"Alarm set for {time_str}"

    def _handle_timer(self, intent: Intent) -> str:
        duration = intent.slots.get("duration", "unknown duration")
        # SIMULATED: On Android, this would use CountDownTimer
        print(f"  [TIMER] [SIM] Starting timer for: {duration}")
        return f"Timer set for {duration}"

    def _handle_open_app(self, intent: Intent) -> str:
        app_name = intent.slots.get("app_name", "unknown app")
        # SIMULATED: On Android, this would use PackageManager + launch intent
        print(f"  [APP] [SIM] Opening app: {app_name}")
        return f"Opening {app_name}"

    def _handle_play_music(self, intent: Intent) -> str:
        query = intent.slots.get("query", "")
        if query:
            print(f"  [MUSIC] [SIM] Playing music: {query}")
            return f"Playing {query}"
        else:
            print("  [MUSIC] [SIM] Playing music")
            return "Playing music"

    def _handle_weather(self, intent: Intent) -> str:
        # SIMULATED: On Android, this could use cached weather data or local sensors
        print("  [WEATHER] [SIM] Fetching weather info")
        return "I'm sorry, weather data is not available offline yet. This feature will work once we add a local weather cache."

    def _handle_time(self, intent: Intent) -> str:
        now = datetime.now()
        time_str = now.strftime("%I:%M %p")
        print(f"  [TIME] [SIM] Current time: {time_str}")
        return f"It's {time_str}"

    def _handle_volume(self, intent: Intent) -> str:
        direction = intent.slots.get("direction", "")
        level = intent.slots.get("level", "")
        if level:
            # SIMULATED: On Android, this would use AudioManager
            print(f"  [VOL] [SIM] Setting volume to {level}%")
            return f"Volume set to {level} percent"
        elif direction:
            print(f"  [VOL] [SIM] Volume {direction}")
            return f"Volume {direction}"
        else:
            return "Volume adjusted"

    def _handle_brightness(self, intent: Intent) -> str:
        direction = intent.slots.get("direction", "")
        level = intent.slots.get("level", "")
        if level:
            print(f"  [BRT] [SIM] Setting brightness to {level}%")
            return f"Brightness set to {level} percent"
        elif direction:
            print(f"  [BRT] [SIM] Brightness {direction}")
            return f"Brightness {direction}"
        else:
            return "Brightness adjusted"

    def _handle_flashlight(self, intent: Intent) -> str:
        state = intent.slots.get("state", "on")
        # SIMULATED: On Android, this would use CameraManager.setTorchMode()
        label = "ON" if state == "on" else "OFF"
        print(f"  [FLASH:{label}] [SIM] Flashlight {state}")
        return f"Flashlight turned {state}"

    def _handle_unknown(self, intent: Intent) -> str:
        print(f"  [??] [SIM] Unknown command: \"{intent.raw_text}\"")
        return "I'm sorry, I didn't understand that command. Could you try again?"


# ── Quick self-test ──────────────────────────────────────────────────────────
if __name__ == "__main__":
    dispatcher = ActionDispatcher()

    test_intents = [
        Intent(name="CALL", confidence=1.0, slots={"contact_name": "mom"}, raw_text="call mom"),
        Intent(name="SMS", confidence=1.0, slots={"contact_name": "john", "message": "hey there"}, raw_text="text john hey there"),
        Intent(name="ALARM", confidence=1.0, slots={"time": "7 am"}, raw_text="set alarm 7 am"),
        Intent(name="TIMER", confidence=1.0, slots={"duration": "5 minutes"}, raw_text="set timer 5 minutes"),
        Intent(name="OPEN_APP", confidence=1.0, slots={"app_name": "YouTube"}, raw_text="open YouTube"),
        Intent(name="PLAY_MUSIC", confidence=1.0, slots={"query": "rock"}, raw_text="play some rock"),
        Intent(name="WEATHER", confidence=1.0, slots={}, raw_text="what's the weather"),
        Intent(name="TIME", confidence=1.0, slots={}, raw_text="what time is it"),
        Intent(name="VOLUME", confidence=1.0, slots={"direction": "up"}, raw_text="volume up"),
        Intent(name="BRIGHTNESS", confidence=1.0, slots={"level": "80"}, raw_text="set brightness to 80"),
        Intent(name="FLASHLIGHT", confidence=1.0, slots={"state": "on"}, raw_text="turn on flashlight"),
        Intent(name="UNKNOWN", confidence=0.0, slots={}, raw_text="tell me a joke"),
    ]

    print("=" * 60)
    print("ACTION DISPATCHER — SELF TEST")
    print("=" * 60)
    for intent in test_intents:
        response = dispatcher.dispatch(intent)
        print(f"    TTS would say: \"{response}\"")
        print()
