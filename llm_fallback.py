"""
Kairo — LLM Fallback Module
Uses SmolLM2 (quantized GGUF) via llama-cpp-python for intent classification
when the rule-based parser cannot handle a command.
"""

from __future__ import annotations

import json
import os
from pathlib import Path

from intent_parser import Intent


# ── Default model path ───────────────────────────────────────────────────────
DEFAULT_MODEL_DIR = Path(__file__).parent / "models"
DEFAULT_MODEL_NAME = "smollm2-360m.Q4_K_M.gguf"

# Supported intents (same list the rule-based parser uses)
SUPPORTED_INTENTS = [
    "CALL", "SMS", "ALARM", "TIMER", "OPEN_APP", "PLAY_MUSIC",
    "WEATHER", "TIME", "VOLUME", "BRIGHTNESS", "FLASHLIGHT", "UNKNOWN",
]

# System prompt for intent classification
SYSTEM_PROMPT = f"""\
You are an intent classifier for a voice assistant. Given a user command, respond with ONLY a JSON object.

Available intents: {', '.join(SUPPORTED_INTENTS)}

Response format (nothing else):
{{"intent": "INTENT_NAME", "slots": {{"key": "value"}}}}

Slot keys by intent:
- CALL: contact_name
- SMS: contact_name, message
- ALARM: time
- TIMER: duration
- OPEN_APP: app_name
- PLAY_MUSIC: query
- VOLUME: direction (up/down/mute) or level (number)
- BRIGHTNESS: direction (up/down) or level (number)
- FLASHLIGHT: state (on/off)
- WEATHER, TIME: no slots
- UNKNOWN: no slots
"""


class LLMFallback:
    """
    Offline LLM fallback for intent parsing.
    Uses a small quantized language model via llama-cpp-python.
    Gracefully degrades if the model file is not available.
    """

    def __init__(
        self,
        model_path: str | Path | None = None,
        n_ctx: int = 512,
        n_threads: int = 4,
        verbose: bool = False,
    ):
        """
        Initialize the LLM fallback.

        Args:
            model_path: Path to the GGUF model file. If None, uses default location.
            n_ctx: Context window size (tokens).
            n_threads: CPU threads for inference.
            verbose: Whether to print llama.cpp logs.
        """
        if model_path is None:
            model_path = DEFAULT_MODEL_DIR / DEFAULT_MODEL_NAME
        self.model_path = Path(model_path)
        self.n_ctx = n_ctx
        self.n_threads = n_threads
        self.verbose = verbose

        self._llm = None  # Lazy-loaded
        self._available: bool | None = None  # None = not checked yet

    @property
    def available(self) -> bool:
        """Check if the model file exists."""
        if self._available is None:
            self._available = self.model_path.exists()
            if not self._available:
                print(f"[LLM] Model not found at: {self.model_path}")
                print("[LLM] LLM fallback disabled. Rule-based parser will be used.")
                print(f"[LLM] To enable, place a GGUF model at: {self.model_path}")
        return self._available

    def _load_model(self):
        """Lazy-load the LLM model."""
        if self._llm is not None:
            return

        if not self.available:
            return

        try:
            from llama_cpp import Llama

            print(f"[LLM] Loading model: {self.model_path.name}...")
            self._llm = Llama(
                model_path=str(self.model_path),
                n_ctx=self.n_ctx,
                n_threads=self.n_threads,
                verbose=self.verbose,
            )
            print("[LLM] Model loaded [OK]")
        except ImportError:
            print("[LLM] llama-cpp-python not installed. LLM fallback disabled.")
            self._available = False
        except Exception as e:
            print(f"[LLM] Failed to load model: {e}")
            self._available = False

    def parse_intent(self, text: str) -> Intent:
        """
        Use the LLM to classify a command into an intent.

        Args:
            text: Raw transcription that the rule-based parser couldn't handle.

        Returns:
            Intent object. Falls back to UNKNOWN if LLM is unavailable or fails.
        """
        self._load_model()

        if self._llm is None:
            return Intent(
                name="UNKNOWN",
                confidence=0.0,
                slots={"raw_query": text},
                raw_text=text,
            )

        prompt = (
            f"{SYSTEM_PROMPT}\n\n"
            f"User command: \"{text}\"\n"
            f"JSON response:"
        )

        try:
            response = self._llm(
                prompt,
                max_tokens=128,
                temperature=0.1,
                stop=["\n\n", "User command:"],
            )
            raw_output = response["choices"][0]["text"].strip()
            return self._parse_response(raw_output, text)
        except Exception as e:
            print(f"[LLM] Inference error: {e}")
            return Intent(
                name="UNKNOWN",
                confidence=0.0,
                slots={"raw_query": text},
                raw_text=text,
            )

    def _parse_response(self, raw_output: str, original_text: str) -> Intent:
        """Parse the LLM's JSON response into an Intent."""
        try:
            # Try to extract JSON from the response
            # The LLM might wrap it in markdown or extra text
            json_start = raw_output.find("{")
            json_end = raw_output.rfind("}") + 1
            if json_start == -1 or json_end == 0:
                raise ValueError("No JSON found in response")

            data = json.loads(raw_output[json_start:json_end])
            intent_name = data.get("intent", "UNKNOWN").upper()

            # Validate intent name
            if intent_name not in SUPPORTED_INTENTS:
                intent_name = "UNKNOWN"

            slots = data.get("slots", {})
            if not isinstance(slots, dict):
                slots = {}

            return Intent(
                name=intent_name,
                confidence=0.7,  # LLM gets medium-high confidence
                slots=slots,
                raw_text=original_text,
            )
        except (json.JSONDecodeError, ValueError) as e:
            print(f"[LLM] Could not parse response: {raw_output!r} — {e}")
            return Intent(
                name="UNKNOWN",
                confidence=0.0,
                slots={"raw_query": original_text},
                raw_text=original_text,
            )


# ── Quick self-test ──────────────────────────────────────────────────────────
if __name__ == "__main__":
    llm = LLMFallback()

    if llm.available:
        test_commands = [
            "remind me to buy groceries",
            "can you call my brother",
            "please turn off the lights",
            "what's the capital of France",
        ]
        print("=" * 60)
        print("LLM FALLBACK — SELF TEST")
        print("=" * 60)
        for cmd in test_commands:
            intent = llm.parse_intent(cmd)
            print(f"  \"{cmd}\"")
            print(f"    -> {intent.name} (conf={intent.confidence:.2f}) slots={intent.slots}")
            print()
    else:
        print("\n[LLM] Self-test skipped — no model file found.")
        print(f"[LLM] Expected at: {DEFAULT_MODEL_DIR / DEFAULT_MODEL_NAME}")
        print("[LLM] Download a GGUF model and place it there to enable LLM fallback.")
