"""
Kairo -- Speech-to-Text Module
Uses sounddevice for mic capture + OpenAI Whisper for offline transcription.
"""

from __future__ import annotations

import numpy as np
import sounddevice as sd
import torch
import whisper


class SpeechToText:
    """Offline speech-to-text using sounddevice + Whisper."""

    # Maximum gain multiplier for quiet mics
    MAX_GAIN = 30.0
    # Target peak level after normalization
    TARGET_PEAK = 0.9

    def __init__(self, model_size: str = "tiny", language: str = "en", mic_device: int | None = None):
        """
        Load the Whisper model once.

        Args:
            model_size: Whisper model variant -- "tiny", "base", "small", etc.
            language: Language code for transcription (e.g. "en").
            mic_device: Explicit audio device index, or None for auto-detect.
        """
        self.language = language
        self.device = "cuda" if torch.cuda.is_available() else "cpu"
        self.samplerate = 16000  # Whisper expects 16kHz

        # Find and set the best microphone
        if mic_device is not None:
            self._mic_device = mic_device
            dev_info = sd.query_devices(mic_device)
            print(f"[STT] Using mic: [{mic_device}] {dev_info['name']}")
        else:
            self._mic_device = self._find_microphone()

        print(f"[STT] Loading Whisper '{model_size}' on {self.device}...")
        self.model = whisper.load_model(model_size, device=self.device)
        print("[STT] Model loaded [OK]")

    @staticmethod
    def _find_microphone() -> int | None:
        """Find the best input device. Prefers Realtek mic over others."""
        try:
            # Get default input device
            default = sd.query_devices(kind="input")
            default_idx = sd.default.device[0]
            print(f"[STT] Default mic: [{default_idx}] {default['name']}")

            # List all input devices
            all_devs = sd.query_devices()
            input_devs = []
            for i, d in enumerate(all_devs):
                if d["max_input_channels"] > 0:
                    name = d["name"].lower()
                    # Skip stereo mix, PC speaker, and loopback devices
                    if any(skip in name for skip in ["stereo mix", "pc speaker", "loopback"]):
                        continue
                    input_devs.append((i, d))

            if input_devs:
                # Prefer "Microphone" devices, especially Realtek
                for idx, d in input_devs:
                    name = d["name"].lower()
                    if "microphone" in name and "array" not in name:
                        if idx != default_idx:
                            print(f"[STT] Also found: [{idx}] {d['name']}")

            # Use default
            return default_idx

        except Exception as e:
            print(f"[STT] WARNING: Mic detection failed: {e}")
            return None

    def listen(self, duration: float = 5.0) -> str:
        """
        Record for a fixed duration and transcribe.

        Args:
            duration: Recording length in seconds.

        Returns:
            Transcribed text string.
        """
        print(f"[STT] Listening for {duration}s... (speak now!)")

        try:
            audio = sd.rec(
                int(duration * self.samplerate),
                samplerate=self.samplerate,
                channels=1,
                dtype="float32",
                device=self._mic_device,
            )
            sd.wait()  # Wait until recording is finished
        except Exception as e:
            print(f"[STT] Recording error: {e}")
            return ""

        audio = audio.flatten()

        # Auto-gain: boost quiet audio for better Whisper recognition
        audio = self._auto_gain(audio)

        print("[STT] Transcribing...")
        try:
            result = self.model.transcribe(
                audio,
                language=self.language,
                fp16=(self.device == "cuda"),
            )
            text = result.get("text", "").strip()
            print(f'[STT] Heard: "{text}"')
            return text
        except Exception as e:
            print(f"[STT] Transcription error: {e}")
            return ""

    @staticmethod
    def _auto_gain(audio: np.ndarray) -> np.ndarray:
        """Boost quiet audio so Whisper can hear it."""
        peak = float(np.max(np.abs(audio)))
        if peak < 0.0001:
            print("[STT] Audio is silent.")
            return audio

        # Calculate gain needed, cap at MAX_GAIN
        gain = min(SpeechToText.TARGET_PEAK / peak, SpeechToText.MAX_GAIN)
        if gain > 1.5:
            print(f"[STT] Mic is quiet (peak={peak:.5f}). Boosting {gain:.1f}x")
        boosted = audio * gain
        # Clip to prevent distortion
        boosted = np.clip(boosted, -1.0, 1.0)
        new_peak = float(np.max(np.abs(boosted)))
        print(f"[STT] Audio level: peak={new_peak:.3f} (after gain)")
        return boosted

    def listen_continuous(
        self,
        silence_threshold: float = 0.0004,
        silence_duration: float = 1.5,
        max_duration: float = 10.0,
        chunk_duration: float = 0.3,
    ) -> str:
        """
        Record until silence is detected, then transcribe.

        Args:
            silence_threshold: RMS energy below which audio is silence.
            silence_duration: Seconds of continuous silence to stop recording.
            max_duration: Maximum recording length (safety cap).
            chunk_duration: Length of each audio chunk to analyze.

        Returns:
            Transcribed text string.
        """
        chunk_size = int(chunk_duration * self.samplerate)
        max_chunks = int(max_duration / chunk_duration)
        silence_chunks_needed = int(silence_duration / chunk_duration)

        print("[STT] Listening... (speak now!)")
        chunks: list[np.ndarray] = []
        silent_count = 0
        has_speech = False

        for _ in range(max_chunks):
            try:
                chunk = sd.rec(
                    chunk_size,
                    samplerate=self.samplerate,
                    channels=1,
                    dtype="float32",
                    device=self._mic_device,
                )
                sd.wait()
            except Exception as e:
                print(f"[STT] Recording error: {e}")
                break

            chunk = chunk.flatten()
            chunks.append(chunk)

            rms = float(np.sqrt(np.mean(chunk ** 2)))

            if rms > silence_threshold:
                has_speech = True
                silent_count = 0
            else:
                if has_speech:
                    silent_count += 1

            # Stop if we've heard speech and then silence long enough
            if has_speech and silent_count >= silence_chunks_needed:
                print("[STT] Silence detected, stopping.")
                break

        if not chunks or not has_speech:
            print("[STT] No speech detected.")
            return ""

        audio = np.concatenate(chunks)

        # Auto-gain before transcribing
        audio = self._auto_gain(audio)

        duration = len(audio) / self.samplerate
        print(f"[STT] Recorded {duration:.1f}s. Transcribing...")

        try:
            result = self.model.transcribe(
                audio,
                language=self.language,
                fp16=(self.device == "cuda"),
            )
            text = result.get("text", "").strip()
            print(f'[STT] Heard: "{text}"')
            return text
        except Exception as e:
            print(f"[STT] Transcription error: {e}")
            return ""

    @staticmethod
    def list_devices():
        """Print all audio devices (for debugging)."""
        print("\nAudio devices:")
        devs = sd.query_devices()
        for i, d in enumerate(devs):
            inp = d["max_input_channels"]
            if inp > 0:
                print(f"  [{i}] {d['name']} (channels={inp})")


# -- Quick self-test -----------------------------------------------------------
if __name__ == "__main__":
    print("=== MICROPHONE TEST ===\n")
    SpeechToText.list_devices()

    stt = SpeechToText(model_size="tiny", language="en")

    print("\n--- Say something (5 seconds) ---")
    text = stt.listen(duration=5)
    if text:
        print(f"\nSUCCESS! You said: {text}")
    else:
        print("\nNo speech detected. Try speaking louder or check mic settings.")

    print("\n--- Continuous mode (speak, then pause) ---")
    text = stt.listen_continuous()
    if text:
        print(f"\nSUCCESS! You said: {text}")
    else:
        print("\nNo speech detected.")