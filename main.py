"""
Kairo — Main Pipeline
Fully local voice assistant: Listen -> Transcribe -> Parse -> Dispatch -> Speak

Usage:
    python main.py              # Normal mode (continuous loop)
    python main.py --test       # Test mode (type commands instead of speaking)
"""

from __future__ import annotations

import sys

from stt import SpeechToText
from intent_parser import IntentParser
from llm_fallback import LLMFallback
from action_dispatcher import ActionDispatcher
from tts import TextToSpeech


BANNER = r"""
    +-----------------------------------------------+
    |                                               |
    |   K   K  AAAA  III RRRR   OOO                 |
    |   K  K  A    A  I  R   R O   O                |
    |   KKK   AAAAAA  I  RRRR  O   O                |
    |   K  K  A    A  I  R  R  O   O                |
    |   K   K A    A III R   R  OOO                 |
    |                                               |
    |   Fully Local Android Voice Assistant         |
    |   Phase 1 -- PC Prototype                     |
    +-----------------------------------------------+
"""


def run_voice_loop(stt: SpeechToText, parser: IntentParser,
                   llm: LLMFallback, dispatcher: ActionDispatcher,
                   tts: TextToSpeech) -> None:
    """Main voice assistant loop using microphone input."""
    tts.speak("Kairo is ready. Say a command.")

    while True:
        try:
            # 1. Listen — record for fixed duration (more reliable)
            text = stt.listen(duration=5)

            if not text:
                continue

            # Skip common Whisper hallucinations on silence
            hallucinations = {
                "", "you", "thank you", "thanks", "bye", "the end",
                "thank you for watching", "thanks for watching",
                "subscribe", "like and subscribe",
            }
            if text.lower().strip().rstrip(".!?,") in hallucinations:
                continue

            # 2. Parse — try rule-based first
            intent = parser.parse(text)

            # 3. LLM Fallback — if rule-based returned UNKNOWN
            if intent.name == "UNKNOWN" and llm.available:
                print("[PIPELINE] Rule parser -> UNKNOWN. Trying LLM fallback...")
                intent = llm.parse_intent(text)

            # 4. Dispatch — execute the action
            response = dispatcher.dispatch(intent)

            # 5. Speak — TTS response
            tts.speak(response)

            print("-" * 50)

        except KeyboardInterrupt:
            print("\n[KAIRO] Shutting down...")
            tts.speak("Goodbye!")
            break


def run_text_loop(parser: IntentParser, llm: LLMFallback,
                  dispatcher: ActionDispatcher, tts: TextToSpeech) -> None:
    """Test mode — type commands instead of speaking."""
    print("\n[TEST MODE] Type commands to test the pipeline (Ctrl+C to quit)")
    print("-" * 50)

    while True:
        try:
            text = input("\nYou: ").strip()
            if not text:
                continue

            if text.lower() in ("quit", "exit", "q"):
                break

            # Parse
            intent = parser.parse(text)

            # LLM fallback
            if intent.name == "UNKNOWN" and llm.available:
                print("[PIPELINE] Rule parser -> UNKNOWN. Trying LLM fallback...")
                intent = llm.parse_intent(text)

            print(f"[PARSE] Intent: {intent.name} | Conf: {intent.confidence:.2f} | Slots: {intent.slots}")

            # Dispatch
            response = dispatcher.dispatch(intent)

            # Speak
            tts.speak(response)

            print("-" * 50)

        except KeyboardInterrupt:
            print("\n[KAIRO] Done.")
            break


def select_microphone() -> int | None:
    """
    Interactive mic selection menu.
    Returns the chosen device index, or None for auto-detect.
    """
    import sounddevice as sd

    all_devs = sd.query_devices()
    input_devs = []
    for i, d in enumerate(all_devs):
        if d["max_input_channels"] > 0:
            name = d["name"]
            # Skip loopback / virtual devices
            if any(skip in name.lower() for skip in ["stereo mix", "pc speaker"]):
                continue
            input_devs.append((i, name, d["max_input_channels"]))

    if not input_devs:
        print("[MIC] No microphones found!")
        return None

    # Get default
    try:
        default_idx = sd.default.device[0]
        default_name = sd.query_devices(default_idx)["name"]
    except Exception:
        default_idx = input_devs[0][0]
        default_name = input_devs[0][1]

    print("\n" + "=" * 50)
    print("  SELECT MICROPHONE")
    print("=" * 50)
    for idx, name, ch in input_devs:
        is_default = " (default)" if idx == default_idx else ""
        print(f"  [{idx}] {name}{is_default}")
    print()
    print(f"  Press ENTER for default: [{default_idx}] {default_name}")
    print("=" * 50)

    try:
        choice = input("  Your choice: ").strip()
        if not choice:
            print(f"  -> Using default mic [{default_idx}]")
            return default_idx

        choice_int = int(choice)
        # Validate
        valid_ids = [idx for idx, _, _ in input_devs]
        if choice_int in valid_ids:
            chosen_name = sd.query_devices(choice_int)["name"]
            print(f"  -> Selected: [{choice_int}] {chosen_name}")
            return choice_int
        else:
            print(f"  Invalid choice. Using default [{default_idx}]")
            return default_idx
    except (ValueError, EOFError):
        print(f"  -> Using default mic [{default_idx}]")
        return default_idx


def main():
    print(BANNER)

    test_mode = "--test" in sys.argv

    # Check for --mic N command line arg
    cli_mic = None
    if "--mic" in sys.argv:
        mic_idx = sys.argv.index("--mic")
        if mic_idx + 1 < len(sys.argv):
            try:
                cli_mic = int(sys.argv[mic_idx + 1])
            except ValueError:
                pass

    # ── Initialize modules ───────────────────────────────────────────────
    print("[KAIRO] Initializing modules...\n")

    if not test_mode:
        if cli_mic is not None:
            mic_device = cli_mic
            print(f"[MIC] Using mic from command line: device {cli_mic}")
        else:
            mic_device = select_microphone()
        stt = SpeechToText(model_size="tiny", language="en", mic_device=mic_device)
    else:
        stt = None

    parser = IntentParser(fuzzy_threshold=70)
    llm = LLMFallback()  # Lazy-loaded, won't crash if model is missing
    dispatcher = ActionDispatcher()
    tts = TextToSpeech(rate=2)

    print("\n[KAIRO] All modules initialized [OK]")
    print("=" * 50)

    # ── Run the loop ─────────────────────────────────────────────────────
    if test_mode:
        run_text_loop(parser, llm, dispatcher, tts)
    else:
        run_voice_loop(stt, parser, llm, dispatcher, tts)


if __name__ == "__main__":
    main()
