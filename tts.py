"""
Kairo -- Text-to-Speech Module
Offline TTS using Windows SAPI (built-in, no pip packages needed).
Falls back to pyttsx3 if available, or prints-only if nothing works.
"""

from __future__ import annotations

import os
import platform
import subprocess


class TextToSpeech:
    """
    Offline text-to-speech engine.
    Uses Windows SAPI via PowerShell (most reliable on Win10/11 + Python 3.14).
    """

    def __init__(self, rate: int = 2, volume: int = 100):
        """
        Initialize the TTS engine.

        Args:
            rate: Speech rate (-10 to 10, default 2 = slightly fast).
            volume: Volume level (0 to 100).
        """
        self.rate = rate
        self.volume = volume
        self._method = "none"

        if platform.system() == "Windows":
            # Test if SAPI works via PowerShell
            try:
                test_cmd = (
                    'Add-Type -AssemblyName System.Speech; '
                    '$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; '
                    '$synth.Dispose()'
                )
                result = subprocess.run(
                    ["powershell", "-NoProfile", "-Command", test_cmd],
                    capture_output=True, text=True, timeout=10
                )
                if result.returncode == 0:
                    self._method = "sapi"
                    print("[TTS] Using Windows SAPI (PowerShell) [OK]")
                else:
                    print(f"[TTS] SAPI test failed: {result.stderr.strip()}")
            except Exception as e:
                print(f"[TTS] SAPI not available: {e}")

        # Fallback: try pyttsx3
        if self._method == "none":
            try:
                import pyttsx3
                self._engine = pyttsx3.init()
                self._engine.setProperty("rate", 175)
                self._engine.setProperty("volume", volume / 100.0)
                self._method = "pyttsx3"
                print("[TTS] Using pyttsx3 [OK]")
            except Exception as e:
                print(f"[TTS] pyttsx3 not available: {e}")

        if self._method == "none":
            print("[TTS] WARNING: No TTS engine available. Will print responses only.")

    def speak(self, text: str) -> None:
        """
        Speak the given text aloud (blocking).

        Args:
            text: The text to speak.
        """
        if not text:
            return

        print(f'[TTS] Speaking: "{text}"')

        if self._method == "sapi":
            self._speak_sapi(text)
        elif self._method == "pyttsx3":
            self._speak_pyttsx3(text)
        # If "none", just the print above serves as output

    def _speak_sapi(self, text: str) -> None:
        """Speak using Windows SAPI via PowerShell."""
        # Escape single quotes for PowerShell
        safe_text = text.replace("'", "''").replace('"', '`"')
        ps_script = (
            f"Add-Type -AssemblyName System.Speech; "
            f"$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
            f"$synth.Rate = {self.rate}; "
            f"$synth.Volume = {self.volume}; "
            f"$synth.Speak('{safe_text}'); "
            f"$synth.Dispose()"
        )
        try:
            subprocess.run(
                ["powershell", "-NoProfile", "-Command", ps_script],
                capture_output=True, text=True, timeout=30
            )
        except subprocess.TimeoutExpired:
            print("[TTS] Speech timed out")
        except Exception as e:
            print(f"[TTS] SAPI error: {e}")

    def _speak_pyttsx3(self, text: str) -> None:
        """Speak using pyttsx3 (fallback)."""
        try:
            self._engine.say(text)
            self._engine.runAndWait()
        except Exception as e:
            print(f"[TTS] pyttsx3 error: {e}")

    def list_voices(self) -> None:
        """List available SAPI voices on this system."""
        if platform.system() != "Windows":
            print("[TTS] Voice listing only available on Windows")
            return

        ps_script = (
            "Add-Type -AssemblyName System.Speech; "
            "$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
            "$synth.GetInstalledVoices() | ForEach-Object { $_.VoiceInfo.Name }; "
            "$synth.Dispose()"
        )
        try:
            result = subprocess.run(
                ["powershell", "-NoProfile", "-Command", ps_script],
                capture_output=True, text=True, timeout=10
            )
            if result.stdout.strip():
                print("\nAvailable voices:")
                for i, voice in enumerate(result.stdout.strip().split("\n")):
                    print(f"  [{i}] {voice.strip()}")
            else:
                print("[TTS] No voices found")
        except Exception as e:
            print(f"[TTS] Error listing voices: {e}")


# -- Quick self-test -----------------------------------------------------------
if __name__ == "__main__":
    tts = TextToSpeech(rate=2, volume=100)
    tts.list_voices()

    print("\nSpeaking test phrases...")
    tts.speak("Kairo is online and ready.")
    tts.speak("Calling mom.")
    tts.speak("Alarm set for 7 AM.")
    print("Done.")
