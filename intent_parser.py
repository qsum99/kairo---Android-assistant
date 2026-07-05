"""
Kairo — Intent Parser Module
Two-tier intent recognition:
  1. Regex patterns (fast, precise)
  2. Fuzzy matching via rapidfuzz (handles variations)
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from rapidfuzz import fuzz, process


# ── Data Structures ──────────────────────────────────────────────────────────

@dataclass
class Intent:
    """Parsed user intent with extracted slots."""
    name: str               # e.g. "CALL", "ALARM", "UNKNOWN"
    confidence: float       # 0.0 – 1.0
    slots: dict = field(default_factory=dict)   # e.g. {"contact_name": "mom"}
    raw_text: str = ""      # original transcription


# ── Regex Rules ──────────────────────────────────────────────────────────────

# Each rule: (intent_name, compiled_regex, slot_extractor_function)
# Slot extractors receive match object, return dict

def _extract_call(m: re.Match) -> dict:
    return {"contact_name": m.group("name").strip()}

def _extract_sms(m: re.Match) -> dict:
    slots = {"contact_name": m.group("name").strip()}
    msg = m.group("message")
    if msg:
        slots["message"] = msg.strip()
    return slots

def _extract_alarm(m: re.Match) -> dict:
    return {"time": m.group("time").strip()}

def _extract_timer(m: re.Match) -> dict:
    return {"duration": m.group("dur").strip()}

def _extract_app(m: re.Match) -> dict:
    return {"app_name": m.group("app").strip()}

def _extract_music(m: re.Match) -> dict:
    query = m.group("query")
    return {"query": query.strip()} if query else {}

def _extract_volume(m: re.Match) -> dict:
    direction = m.group("dir") if "dir" in m.groupdict() else None
    level = m.group("level") if "level" in m.groupdict() else None
    slots = {}
    if direction:
        slots["direction"] = direction.strip().lower()
    if level:
        slots["level"] = level.strip()
    return slots

def _extract_brightness(m: re.Match) -> dict:
    direction = m.group("dir") if "dir" in m.groupdict() else None
    level = m.group("level") if "level" in m.groupdict() else None
    slots = {}
    if direction:
        slots["direction"] = direction.strip().lower()
    if level:
        slots["level"] = level.strip()
    return slots

def _extract_flashlight(m: re.Match) -> dict:
    state = m.group("state").strip().lower()
    return {"state": "on" if state in ("on", "enable") else "off"}


def _extract_flashlight_multi(m: re.Match) -> dict:
    """Handle multiple named groups for flashlight state."""
    state = m.group("state") or m.group("state2") or m.group("state3")
    if state:
        return {"state": state.strip().lower()}
    # enable/disable branch
    raw = m.group(0).lower()
    return {"state": "on" if "enable" in raw else "off"}


REGEX_RULES: list[tuple[str, re.Pattern, callable]] = [
    # CALL — "call mom", "phone dad", "dial john"
    (
        "CALL",
        re.compile(
            r"(?:call|phone|dial|ring)\s+(?P<name>.+)",
            re.IGNORECASE,
        ),
        _extract_call,
    ),
    # SMS — "text john hello there", "send message to mom hey"
    (
        "SMS",
        re.compile(
            r"(?:text|message|sms|send\s+(?:a\s+)?(?:message|text|sms)\s+(?:to\s+)?)"
            r"\s*(?P<name>\w+(?:\s+\w+)?)\s*(?P<message>.*)?",
            re.IGNORECASE,
        ),
        _extract_sms,
    ),
    # ALARM — "set alarm 7am", "wake me at 6:30", "alarm for 8 pm"
    (
        "ALARM",
        re.compile(
            r"(?:set\s+(?:an?\s+)?alarm(?:\s+(?:for|at|to))?\s+|wake\s+me\s+(?:up\s+)?(?:at\s+)?)"
            r"(?P<time>.+)",
            re.IGNORECASE,
        ),
        _extract_alarm,
    ),
    # TIMER — "set timer 5 minutes", "timer for 30 seconds"
    (
        "TIMER",
        re.compile(
            r"(?:set\s+(?:a\s+)?timer(?:\s+(?:for|of))?\s+|timer\s+(?:for\s+)?)"
            r"(?P<dur>.+)",
            re.IGNORECASE,
        ),
        _extract_timer,
    ),
    # OPEN_APP — "open YouTube", "launch camera", "start settings"
    (
        "OPEN_APP",
        re.compile(
            r"(?:open|launch|start|run)\s+(?P<app>.+)",
            re.IGNORECASE,
        ),
        _extract_app,
    ),
    # PLAY_MUSIC — "play music", "play some rock"
    (
        "PLAY_MUSIC",
        re.compile(
            r"(?:play)\s+(?:some\s+)?(?:music|song|songs)?(?:\s+(?P<query>.+))?",
            re.IGNORECASE,
        ),
        _extract_music,
    ),
    # VOLUME — "volume up", "turn volume down", "set volume to 50"
    (
        "VOLUME",
        re.compile(
            r"(?:(?:turn\s+)?volume\s+(?P<dir>up|down|mute)|set\s+volume\s+(?:to\s+)?(?P<level>\d+))",
            re.IGNORECASE,
        ),
        _extract_volume,
    ),
    # BRIGHTNESS — "brightness up", "dim the screen", "set brightness to 80"
    (
        "BRIGHTNESS",
        re.compile(
            r"(?:(?:turn\s+)?brightness\s+(?P<dir>up|down)|dim\s+(?:the\s+)?screen"
            r"|set\s+brightness\s+(?:to\s+)?(?P<level>\d+))",
            re.IGNORECASE,
        ),
        _extract_brightness,
    ),
    # FLASHLIGHT — "turn on flashlight", "flashlight off"
    (
        "FLASHLIGHT",
        re.compile(
            r"(?:(?:turn\s+)?(?P<state>on|off)\s+(?:the\s+)?(?:flashlight|torch)"
            r"|(?:flashlight|torch)\s+(?P<state2>on|off)"
            r"|(?:turn\s+)?(?:the\s+)?(?:flashlight|torch)\s+(?P<state3>on|off)"
            r"|(?:enable|disable)\s+(?:the\s+)?(?:flashlight|torch))",
            re.IGNORECASE,
        ),
        _extract_flashlight_multi,
    ),
    # WEATHER — "what's the weather", "how's the weather today"
    (
        "WEATHER",
        re.compile(
            r"(?:what(?:'s| is)\s+the\s+weather|how(?:'s| is)\s+the\s+weather|weather\s+(?:today|now|forecast))",
            re.IGNORECASE,
        ),
        lambda m: {},
    ),
    # TIME — "what time is it", "what's the time"
    (
        "TIME",
        re.compile(
            r"(?:what(?:'s| is)\s+the\s+time|what\s+time\s+is\s+it|tell\s+me\s+the\s+time|current\s+time)",
            re.IGNORECASE,
        ),
        lambda m: {},
    ),
]




# ── Fuzzy-Match Keyword Map ─────────────────────────────────────────────────

# Maps trigger phrases to intent names for fuzzy matching
FUZZY_TRIGGERS: dict[str, str] = {
    "call": "CALL",
    "phone": "CALL",
    "dial": "CALL",
    "ring": "CALL",
    "text": "SMS",
    "message": "SMS",
    "send message": "SMS",
    "send text": "SMS",
    "set alarm": "ALARM",
    "wake me": "ALARM",
    "alarm": "ALARM",
    "set timer": "TIMER",
    "timer": "TIMER",
    "countdown": "TIMER",
    "open": "OPEN_APP",
    "launch": "OPEN_APP",
    "start": "OPEN_APP",
    "play music": "PLAY_MUSIC",
    "play song": "PLAY_MUSIC",
    "play": "PLAY_MUSIC",
    "weather": "WEATHER",
    "forecast": "WEATHER",
    "what time": "TIME",
    "current time": "TIME",
    "volume up": "VOLUME",
    "volume down": "VOLUME",
    "mute": "VOLUME",
    "brightness": "BRIGHTNESS",
    "dim screen": "BRIGHTNESS",
    "flashlight": "FLASHLIGHT",
    "torch": "FLASHLIGHT",
}


# ── Intent Parser ────────────────────────────────────────────────────────────

class IntentParser:
    """
    Two-tier intent parser:
      1. Regex patterns (fast, structured extraction)
      2. Fuzzy keyword matching (handles typos & variations)
    """

    def __init__(self, fuzzy_threshold: int = 70):
        """
        Args:
            fuzzy_threshold: Minimum rapidfuzz score (0–100) to accept a fuzzy match.
        """
        self.fuzzy_threshold = fuzzy_threshold
        self._fuzzy_choices = list(FUZZY_TRIGGERS.keys())

    def parse(self, text: str) -> Intent:
        """
        Parse raw text into a structured Intent.

        Tries regex rules first (high confidence), then fuzzy matching
        (medium confidence), and falls back to UNKNOWN.

        Args:
            text: Raw transcription from STT.

        Returns:
            Intent object with name, confidence, slots, and raw text.
        """
        text = text.strip()
        if not text:
            return Intent(name="UNKNOWN", confidence=0.0, raw_text=text)

        # ── Tier 1: Regex ────────────────────────────────────────────────
        for intent_name, pattern, extractor in REGEX_RULES:
            match = pattern.search(text)
            if match:
                try:
                    slots = extractor(match)
                except Exception:
                    slots = {}
                return Intent(
                    name=intent_name,
                    confidence=1.0,
                    slots=slots,
                    raw_text=text,
                )

        # ── Tier 2: Fuzzy match ──────────────────────────────────────────
        result = process.extractOne(
            text.lower(),
            self._fuzzy_choices,
            scorer=fuzz.partial_ratio,
        )
        if result:
            matched_phrase, score, _ = result
            if score >= self.fuzzy_threshold:
                intent_name = FUZZY_TRIGGERS[matched_phrase]
                return Intent(
                    name=intent_name,
                    confidence=score / 100.0,
                    slots={"raw_query": text},
                    raw_text=text,
                )

        # ── Fallback ─────────────────────────────────────────────────────
        return Intent(name="UNKNOWN", confidence=0.0, raw_text=text)


# ── Quick self-test ──────────────────────────────────────────────────────────
if __name__ == "__main__":
    parser = IntentParser()

    test_commands = [
        "call mom",
        "phone dad",
        "text john hey how are you",
        "send a message to sarah",
        "set alarm 7 am",
        "wake me up at 6:30",
        "set timer 5 minutes",
        "open YouTube",
        "launch camera",
        "play some rock music",
        "what's the weather",
        "what time is it",
        "volume up",
        "set volume to 50",
        "brightness down",
        "turn on flashlight",
        "flashlight off",
        "tell me a joke",          # → UNKNOWN (or fuzzy)
        "ring my friend Alex",     # → CALL via regex
    ]

    print("=" * 60)
    print("INTENT PARSER — SELF TEST")
    print("=" * 60)
    for cmd in test_commands:
        intent = parser.parse(cmd)
        print(f"  \"{cmd}\"")
        print(f"    -> {intent.name} (conf={intent.confidence:.2f}) slots={intent.slots}")
        print()
