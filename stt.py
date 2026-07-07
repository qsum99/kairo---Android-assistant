"""
Kairo -- Speech-to-Text Module
Records audio from microphone and transcribes it offline using OpenAI Whisper.
"""

from __future__ import annotations

import numpy as np
import sounddevice as sd
import torch
import whisper


class SpeechToText:
    """Offline speech-to-text transcriber using sounddevice + Whisper."""

    # Normalization parameters for quiet microphones
    MAX_GAIN = 30.0
    TARGET_PEAK = 0.9

    def __init__(self, model_size: str = "tiny", language: str = "en", mic_device: int | None = None):
        """
        Initialize Whisper model and select the microphone.

        Args:
            model_size: Whisper model size ("tiny", "base", "small", etc.)
            language: Transcription language (e.g., "en")
            mic_device: Force a specific microphone device index
        """
        self.language = language
        self.samplerate = 16000  # Whisper models operate at 16kHz
        self.device = "cuda" if torch.cuda.is_available() else "cpu"

        # Setup mic
        if mic_device is not None:
            self._mic_device = mic_device
            dev_info = sd.query_devices(mic_device)
            print(f"[STT] Forcing microphone: [{mic_device}] {dev_info['name']}")
        else:
            self._mic_device = self._find_default_mic()

        print(f"[STT] Loading Whisper '{model_size}' model on {self.device}...")
        self.model = whisper.load_model(model_size, device=self.device)
        print("[STT] Model loaded successfully [OK]")

    def _find_default_mic(self) -> int | None:
        """Query and set the default input microphone."""
        try:
            default_input_idx = sd.default.device[0]
            default_device = sd.query_devices(default_input_idx)
            print(f"[STT] Using default mic: [{default_input_idx}] {default_device['name']}")
            return default_input_idx
        except Exception as e:
            print(f"[STT] WARNING: Mic detection failed: {e}")
            return None

    def _apply_agc(self, audio: np.ndarray) -> np.ndarray:
        """Boost quiet audio signals so Whisper can transcribe clearly."""
        peak = float(np.max(np.abs(audio)))
        if peak < 0.0001:
            # Silence
            return audio

        # Calculate boost multiplier (cap at MAX_GAIN to avoid blowing up noise floor)
        gain = min(self.TARGET_PEAK / peak, self.MAX_GAIN)
        if gain > 1.5:
            print(f"[STT] Mic level is low (peak={peak:.5f}). Software gain applied: {gain:.1f}x")
        
        boosted = np.clip(audio * gain, -1.0, 1.0)
        print(f"[STT] Boosted audio level: peak={float(np.max(np.abs(boosted))):.3f}")
        return boosted

    def listen(self, duration: float = 5.0) -> str:
        """
        Record audio for a fixed duration and transcribe it.

        Args:
            duration: Recording duration in seconds.

        Returns:
            Transcribed text.
        """
        print(f"[STT] Listening for {duration} seconds... (speak now)")

        try:
            audio = sd.rec(
                int(duration * self.samplerate),
                samplerate=self.samplerate,
                channels=1,
                dtype="float32",
                device=self._mic_device
            )
            sd.wait()  # Block until recording finishes
        except Exception as e:
            print(f"[STT] Recording failed: {e}")
            return ""

        audio = audio.flatten()
        
        # Apply automatic gain control
        audio = self._apply_agc(audio)

        print("[STT] Transcribing audio...")
        try:
            result = self.model.transcribe(
                audio,
                language=self.language,
                fp16=(self.device == "cuda")
            )
            text = result.get("text", "").strip()
            print(f'[STT] Heard: "{text}"')
            return text
        except Exception as e:
            print(f"[STT] Transcription error: {e}")
            return ""

    @staticmethod
    def list_microphones():
        """List all available audio input devices."""
        print("\nAvailable Input Devices:")
        print("=" * 60)
        try:
            devs = sd.query_devices()
            for i, d in enumerate(devs):
                if d["max_input_channels"] > 0:
                    default_marker = " [DEFAULT]" if i == sd.default.device[0] else ""
                    print(f"  [{i}] {d['name']} (channels={d['max_input_channels']}){default_marker}")
        except Exception as e:
            print(f"Could not list devices: {e}")
        print("=" * 60 + "\n")


# -- Quick Self Test -----------------------------------------------------------
if __name__ == "__main__":
    SpeechToText.list_microphones()
    
    # Run test
    stt = SpeechToText(model_size="tiny")
    text = stt.listen(duration=5.0)
    print(f"Result: {text}")
