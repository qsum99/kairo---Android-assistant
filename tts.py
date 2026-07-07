"""
Kairo -- Text-to-Speech Module
Provides offline speech synthesis using Windows SAPI via PowerShell.
Bypasses COM-level crash bugs under Python 3.14.
"""

from __future__ import annotations

import platform
import subprocess


class TextToSpeech:
    """Offline text-to-speech engine using Windows SAPI (Speech Synthesizer)."""

    def __init__(self, rate: int = 2, volume: int = 100):
        """
        Initialize the speech engine.

        Args:
            rate: Speech speed (-10 to 10. Default is 2, slightly faster than default).
            volume: Volume level (0 to 100).
        """
        self.rate = rate
        self.volume = volume
        self._available = False

        if platform.system() == "Windows":
            # Quick test of SAPI synthesis
            try:
                test_cmd = (
                    "Add-Type -AssemblyName System.Speech; "
                    "$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
                    "$synth.Dispose()"
                )
                res = subprocess.run(
                    ["powershell", "-NoProfile", "-Command", test_cmd],
                    capture_output=True, text=True, timeout=5
                )
                if res.returncode == 0:
                    self._available = True
                    print("[TTS] Windows SAPI (PowerShell) initialized [OK]")
                else:
                    print(f"[TTS] SAPI failed: {res.stderr}")
            except Exception as e:
                print(f"[TTS] Initialization error: {e}")

        if not self._available:
            print("[TTS] WARNING: Offline SAPI not available. Running in Print-Only fallback.")

    def speak(self, text: str) -> None:
        """
        Speak text out loud.

        Args:
            text: Text to speak.
        """
        if not text:
            return

        print(f'[TTS] Speaking: "{text}"')

        if not self._available:
            return

        # Prepare strings: Escape single quotes for PowerShell
        safe_text = text.replace("'", "''").replace('"', '`"')
        ps_command = (
            f"Add-Type -AssemblyName System.Speech; "
            f"$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
            f"$synth.Rate = {self.rate}; "
            f"$synth.Volume = {self.volume}; "
            f"$synth.Speak('{safe_text}'); "
            f"$synth.Dispose()"
        )

        try:
            subprocess.run(
                ["powershell", "-NoProfile", "-Command", ps_command],
                capture_output=True, text=True, timeout=15
            )
        except Exception as e:
            print(f"[TTS] Speech synthesis failed: {e}")

    @staticmethod
    def list_voices():
        """List all installed Windows voices."""
        if platform.system() != "Windows":
            print("[TTS] Voices list only available on Windows systems.")
            return

        print("\nAvailable Voices:")
        print("=" * 60)
        ps_command = (
            "Add-Type -AssemblyName System.Speech; "
            "$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
            "$synth.GetInstalledVoices() | ForEach-Object { $_.VoiceInfo.Name }; "
            "$synth.Dispose()"
        )
        try:
            res = subprocess.run(
                ["powershell", "-NoProfile", "-Command", ps_command],
                capture_output=True, text=True, timeout=10
            )
            voices = res.stdout.strip().split("\n")
            for idx, voice in enumerate(voices):
                if voice:
                    print(f"  [{idx}] {voice.strip()}")
        except Exception as e:
            print(f"Could not fetch voices list: {e}")
        print("=" * 60 + "\n")


# -- Quick Self Test -----------------------------------------------------------
if __name__ == "__main__":
    TextToSpeech.list_voices()
    
    tts = TextToSpeech()
    tts.speak("kairo is live")
