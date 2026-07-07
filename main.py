"""
Kairo -- Main Pipeline (Voice / Text LLM Assistant)
Integrates: Listen (STT) -> LLM Brain (LangGraph + Ollama LLaMA) -> Speak (TTS)

Usage:
    python main.py              # Voice-activated loop
    python main.py --test       # Text-activated interactive mode
    python main.py --model llama3.1:8b  # Force a specific Ollama model
"""

from __future__ import annotations

import sys
from stt import SpeechToText
from tts import TextToSpeech
from llm_brain import LLMBrain

BANNER = r"""
    +-----------------------------------------------+
    |                                               |
    |   K   K  AAAA  III RRRR   OOO                 |
    |   K  K  A    A  I  R   R O   O                |
    |   KKK   AAAAAA  I  RRRR  O   O                |
    |   K  K  A    A  I  R  R  O   O                |
    |   K   K A    A III R   R  OOO                 |
    |                                               |
    |   Local AI Conversational Assistant           |
    |   Using LangGraph & LLaMA SLM                 |
    +-----------------------------------------------+
"""

def select_microphone() -> int | None:
    """
    Interactive microphone selection menu.
    Returns the chosen device index, or None for auto-detect.
    """
    import sounddevice as sd

    try:
        all_devs = sd.query_devices()
    except Exception as e:
        print(f"[MIC] Could not query audio devices: {e}")
        return None

    input_devs = []
    for i, d in enumerate(all_devs):
        if d["max_input_channels"] > 0:
            name = d["name"]
            # Skip loopback or virtual devices
            if any(skip in name.lower() for skip in ["stereo mix", "pc speaker", "loopback"]):
                continue
            input_devs.append((i, name, d["max_input_channels"]))

    if not input_devs:
        print("[MIC] No microphones found!")
        return None

    # Determine system default index
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


def run_voice_loop(stt: SpeechToText, brain: LLMBrain, tts: TextToSpeech) -> None:
    """Continuous loop using voice input and spoken output."""
    tts.speak("Kairo is ready. How can I help you today?")
    print("\n[KAIRO] Voice loop is active. Speak clearly.")
    print("-" * 50)

    # Simple dictionary of typical Whisper hallucinations on quiet audio
    hallucinations = {
        "", "you", "thank you", "thanks", "bye", "the end",
        "thank you for watching", "thanks for watching",
        "subscribe", "like and subscribe", "watching"
    }

    while True:
        try:
            # Listen to mic (default duration of 5 seconds)
            query = stt.listen(duration=5.0)

            if not query:
                continue

            cleaned_query = query.lower().strip().rstrip(".!?,")
            if cleaned_query in hallucinations:
                continue

            print(f"\n[STT] Query: {query}")

            # Send to local LangGraph + LLaMA brain
            print("[BRAIN] Thinking...")
            response = brain.query(query)
            print(f"[BRAIN] Answer: {response}")

            # Speak response out loud
            tts.speak(response)
            print("-" * 50)

        except KeyboardInterrupt:
            print("\n[KAIRO] Shutting down...")
            tts.speak("Goodbye!")
            break
        except Exception as e:
            print(f"[KAIRO] Error in voice loop: {e}")
            break


def run_text_loop(brain: LLMBrain, tts: TextToSpeech) -> None:
    """Interactive console text loop (great for quick testing)."""
    print("\n[TEST MODE] Type your questions below. Type 'exit' or 'quit' to end.")
    print("-" * 50)

    while True:
        try:
            query = input("\nYou: ").strip()
            if not query:
                continue

            if query.lower() in ("exit", "quit", "q"):
                print("[KAIRO] Shutting down...")
                tts.speak("Goodbye!")
                break

            # Send to local LangGraph + LLaMA brain
            print("[BRAIN] Thinking...")
            response = brain.query(query)
            print(f"[BRAIN] Answer: {response}")

            # Speak response out loud (and print to console)
            tts.speak(response)
            print("-" * 50)

        except KeyboardInterrupt:
            print("\n[KAIRO] Done.")
            break
        except Exception as e:
            print(f"[KAIRO] Error in text loop: {e}")
            break


def main():
    print(BANNER)

    test_mode = "--test" in sys.argv

    # Check for custom model argument
    model_name = "llama3.2:1b"
    if "--model" in sys.argv:
        idx = sys.argv.index("--model")
        if idx + 1 < len(sys.argv):
            model_name = sys.argv[idx + 1]
    elif "--model_name" in sys.argv:
        idx = sys.argv.index("--model_name")
        if idx + 1 < len(sys.argv):
            model_name = sys.argv[idx + 1]

    # Initialize modules
    print(f"[KAIRO] Initializing LLM brain with model: {model_name}...")
    brain = LLMBrain(model_name=model_name)
    tts = TextToSpeech(rate=2)

    if test_mode:
        print("[KAIRO] Running in Text-Test Mode.")
        run_text_loop(brain, tts)
    else:
        print("[KAIRO] Running in Voice Mode.")
        mic_idx = select_microphone()
        stt = SpeechToText(model_size="tiny", language="en", mic_device=mic_idx)
        run_voice_loop(stt, brain, tts)


if __name__ == "__main__":
    main()
