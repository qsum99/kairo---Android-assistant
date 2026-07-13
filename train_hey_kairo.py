"""
Custom Wake Word Trainer: Robust Binary Classifier (Sigmoid)
------------------------------------------------------------
Target:
  - Positive Class (1.0): "Kairo" and "Hey Kairo"
  - Negative Class (0.0): Silence, static noise, negative words, and 100 English sentences

Pipeline:
  1. Generate TTS base clips for both "kairo" and "hey kairo" (Class 1).
  2. Generate negative sentences and words (Class 0).
  3. Load user-recorded voice clips for "kairo" and "hey kairo" from Colab.
  4. Perform data augmentation on all classes.
  5. Train a binary PyTorch classifier using BCELoss and Sigmoid (prevents out-of-distribution false triggers).
  6. Export the model to ONNX with shape [batch_size, 1].

Usage:
    python train_hey_kairo.py
"""

import os
import glob
import subprocess
import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import TensorDataset, DataLoader
from gtts import gTTS

# ── 1. CONFIG ──────────────────────────────────────────────────────────────
WAKE_WORD_KAIRO = "kairo"
WAKE_WORD_HEY_KAIRO = "hey kairo"
MODEL_NAME = "kairo.onnx"

WINDOW_FRAMES = 16
EMBED_DIM = 96
CLIP_SAMPLES = 40160  # Exactly 2.51 seconds at 16kHz
EMBED_BATCH_SIZE = 32

# Folders for the classes
KAIRO_DIR = "data/kairo_wav"
HEY_KAIRO_DIR = "data/hey_kairo_wav"
NEG_DIR = "data/neg_wav"

KAIRO_MP3_DIR = "data/kairo_mp3"
HEY_KAIRO_MP3_DIR = "data/hey_kairo_mp3"
NEG_MP3_DIR = "data/neg_mp3"

for d in [KAIRO_DIR, HEY_KAIRO_DIR, NEG_DIR, KAIRO_MP3_DIR, HEY_KAIRO_MP3_DIR, NEG_MP3_DIR]:
    os.makedirs(d, exist_ok=True)

# Phonetic near-misses and background words
NEG_WORDS = [
    "cairo", "hey karo", "hey cairo", "kairos", "hey rho",
    "hello world", "hey google", "hey siri", "alexa",
    "play music", "stop", "assistant", "computer", "hey there", "hey kyro"
]

# 100 diverse everyday speech sentences for general conversation rejection
NEG_SENTENCES = [
    "what is the weather today", "how is the traffic to work", "set an alarm for seven am",
    "play some classical music", "turn off the kitchen lights", "remind me to buy milk tomorrow",
    "tell me a funny joke", "what is the capital of France", "how far is the moon from earth",
    "open the front door please", "send a message to john", "call my mom on her cell",
    "what is on my calendar today", "add bananas to my shopping list", "how do you make a chocolate cake",
    "what is the definition of science", "play the latest news podcast", "mute the volume of the TV",
    "increase the brightness of the screen", "find a good Italian restaurant nearby", "how many ounces in a cup",
    "translate hello to Spanish", "what is the stock price of Apple", "tell me a bedtime story",
    "is it going to rain this afternoon", "how do I get to the nearest gas station", "what is the score of the game",
    "remind me to water the plants", "turn on the air conditioner", "play some jazz radio",
    "what is the height of mount everest", "how many days until Christmas", "search the web for artificial intelligence",
    "how do you change a flat tire", "what is the speed of sound", "set a countdown for five minutes",
    "what is the time in London right now", "read my unread email notifications", "lock all the doors in the house",
    "show me the directions to the library", "who wrote the play Hamlet", "how do plants make food",
    "what is the deep ocean temperature", "recommend a mystery book to read", "play some ambient white noise",
    "turn on the smart plug in the bedroom", "remind me of my dentist appointment", "what is the population of Tokyo",
    "how many miles in a kilometer", "what is the square root of eighty one", "play the movie sound track",
    "stop the timer on the stove", "is there a post office near here", "what are the primary colors",
    "how do you say thank you in French", "what is the nearest pharmacy open now", "remind me to pay the bills",
    "turn off the heater in the hallway", "what is the distance to Chicago", "how does a refrigerator work",
    "what is the name of this song", "play some relaxing nature sounds", "set a timer for thirty seconds",
    "what is the spelling of beautiful", "how do you grow tomatoes from seeds", "what is the largest mammal",
    "how many bones are in the human body", "play some upbeat pop hits", "turn on the outdoor patio lights",
    "what is the current time zone here", "how long is a marathon race", "what is the color of the sky",
    "how do search engines find pages", "what is the speed limit on this highway", "set a reminder for next Monday",
    "what are the ingredients of sushi", "how do batteries store energy", "play a podcast about history",
    "turn down the volume a little bit", "what is the best way to clean windows", "remind me to lock the gates",
    "what is the currency used in Japan", "how does a microscope magnify objects", "what is the temperature of water boiling",
    "play some classical piano music", "turn off the charger socket", "what is the schedule for tomorrow",
    "how many players are on a soccer team", "what is the weight of an elephant", "show me a picture of a red rose",
    "how do you reset this device", "what is the distance of the marathon", "tell me the news updates today",
    "is the grocery store open on Sunday", "what is the meaning of life", "how do you play chess"
]

ACCENTS = [
    ("en", "com"), ("en", "co.uk"), ("en", "ca"), ("en", "co.in"), ("en", "com.au"), ("en", "ie"),
    ("es", "com"), ("it", "it"), ("de", "de"), ("nl", "nl")
]


# ── 2. TTS GENERATION ────────────────────────────────────────────────────
def generate_tts_clips():
    print("Downloading diverse 'Kairo' base clips...")
    count = 0
    for lang, tld in ACCENTS:
        for slow in [False, True]:
            out_path = f"{KAIRO_MP3_DIR}/pos_{count}.mp3"
            count += 1
            if os.path.exists(out_path):
                continue
            try:
                tts = gTTS(text=WAKE_WORD_KAIRO, lang=lang, tld=tld, slow=slow)
                tts.save(out_path)
            except Exception as e:
                print(f"  skip kairo tts ({lang}-{tld}): {e}")

    print("Downloading diverse 'Hey Kairo' base clips...")
    count = 0
    for lang, tld in ACCENTS:
        for slow in [False, True]:
            out_path = f"{HEY_KAIRO_MP3_DIR}/pos_{count}.mp3"
            count += 1
            if os.path.exists(out_path):
                continue
            try:
                tts = gTTS(text=WAKE_WORD_HEY_KAIRO, lang=lang, tld=tld, slow=slow)
                tts.save(out_path)
            except Exception as e:
                print(f"  skip hey kairo tts ({lang}-{tld}): {e}")

    print("Downloading diverse negative base words...")
    count = 0
    for word in NEG_WORDS:
        for lang, tld in ACCENTS[:6]:
            for slow in [False, True]:
                out_path = f"{NEG_MP3_DIR}/neg_{count}.mp3"
                count += 1
                if os.path.exists(out_path):
                    continue
                try:
                    tts = gTTS(text=word, lang=lang, tld=tld, slow=slow)
                    tts.save(out_path)
                except Exception as e:
                    print(f"  skip neg ({word}, {lang}-{tld}): {e}")

    print("Downloading 100 negative speech sentences...")
    for i, sentence in enumerate(NEG_SENTENCES):
        out_path = f"{NEG_MP3_DIR}/neg_sent_{i}.mp3"
        if os.path.exists(out_path):
            continue
        try:
            lang, tld = ACCENTS[i % len(ACCENTS)]
            tts = gTTS(text=sentence, lang=lang, tld=tld, slow=False)
            tts.save(out_path)
        except Exception as e:
            print(f"  skip neg_sent_{i}: {e}")


# ── 3. CONVERT TO 16kHz MONO WAV ────────────────────────────────────────
def convert_all_to_wav(mp3_dir, wav_dir):
    mp3_files = glob.glob(f"{mp3_dir}/*.mp3")
    print(f"Converting {len(mp3_files)} clips from {mp3_dir} -> {wav_dir} (16kHz mono)...")
    for mp3_path in mp3_files:
        base = os.path.splitext(os.path.basename(mp3_path))[0]
        wav_path = f"{wav_dir}/{base}.wav"
        if os.path.exists(wav_path):
            continue
        try:
            subprocess.run(
                ["ffmpeg", "-y", "-i", mp3_path, "-ar", "16000", "-ac", "1", wav_path],
                check=True, capture_output=True,
            )
        except subprocess.CalledProcessError as e:
            print(f"  ffmpeg failed on {mp3_path}: {e.stderr.decode(errors='ignore')[:200]}")
        except FileNotFoundError:
            raise RuntimeError("ffmpeg not found. Install it first.")


# ── 4. RAW AUDIO AUGMENTATION ────────────────────────────────────────────
def augment_audio(audio, speed, noise, volume):
    if speed != 1.0:
        n_samples = int(len(audio) / speed)
        audio = np.interp(np.linspace(0, len(audio) - 1, n_samples), np.arange(len(audio)), audio)
    audio = audio * volume
    if noise > 0:
        audio = audio + np.random.normal(0, noise * 32767, audio.shape)
    audio = np.clip(audio, -32768, 32767)
    if len(audio) < CLIP_SAMPLES:
        pad_left = (CLIP_SAMPLES - len(audio)) // 2
        pad_right = CLIP_SAMPLES - len(audio) - pad_left
        audio = np.concatenate([np.zeros(pad_left), audio, np.zeros(pad_right)])
    else:
        audio = audio[:CLIP_SAMPLES]
    return audio.astype(np.int16)


# ── 5. FEATURE EXTRACTION WITH AUGMENTATION ──────────────────────────────
def load_and_augment_dataset(wav_dir, feature_extractor, is_positive):
    import scipy.io.wavfile as wavfile
    wav_files = sorted(glob.glob(f"{wav_dir}/*.wav"))
    
    if not wav_files:
        print(f"  warning: No WAV files found in {wav_dir}")
        return np.empty((0, WINDOW_FRAMES, EMBED_DIM), dtype=np.float32)

    all_embeddings = []
    batch_audio = []

    # Augmentation configurations
    if is_positive:
        # Generate 15 augmented versions per positive clip
        speeds = [0.85, 0.90, 0.95, 1.00, 1.05, 1.10, 1.15]
        noises = [0.0, 0.005, 0.01]
        volumes = [0.6, 1.0, 1.4]
        configs = []
        for s in speeds:
            for n in noises:
                for v in volumes:
                    configs.append((s, n, v))
        np.random.shuffle(configs)
        selected_configs = configs[:15]
    else:
        # Generate 3 augmented versions for negatives
        selected_configs = [
            (1.0, 0.0, 1.0),
            (0.95, 0.005, 0.8),
            (1.05, 0.005, 1.2)
        ]

    def flush_batch():
        if not batch_audio:
            return
        batch = np.stack(batch_audio, axis=0).astype(np.int16)
        emb_batch = feature_extractor.embed_clips(batch, batch_size=batch.shape[0])
        for emb in emb_batch:
            n_frames = emb.shape[0]
            if n_frames < WINDOW_FRAMES:
                pad = np.zeros((WINDOW_FRAMES - n_frames, EMBED_DIM), dtype=np.float32)
                emb = np.vstack([emb, pad])
            elif n_frames > WINDOW_FRAMES:
                start = (n_frames - WINDOW_FRAMES) // 2
                emb = emb[start:start + WINDOW_FRAMES]
            all_embeddings.append(emb.astype(np.float32))
        batch_audio.clear()

    print(f"Loading and augmenting {wav_dir}...")
    for wav_path in wav_files:
        sr, base_audio = wavfile.read(wav_path)
        if sr != 16000:
            continue
        if base_audio.dtype != np.int16:
            base_audio = (base_audio * 32767).astype(np.int16)

        for speed, noise, volume in selected_configs:
            aug_wav = augment_audio(base_audio, speed, noise, volume)
            batch_audio.append(aug_wav)
            if len(batch_audio) >= EMBED_BATCH_SIZE:
                flush_batch()
                
    flush_batch()
    
    if not all_embeddings:
        return np.empty((0, WINDOW_FRAMES, EMBED_DIM), dtype=np.float32)

    return np.stack(all_embeddings, axis=0)


# ── 6. BINARY MODEL DEFINITION ───────────────────────────────────────────
class WakeWordClassifier(nn.Module):
    def __init__(self):
        super().__init__()
        self.fc = nn.Sequential(
            nn.Flatten(),
            nn.Linear(WINDOW_FRAMES * EMBED_DIM, 32),
            nn.ReLU(),
            nn.Dropout(0.3),
            nn.Linear(32, 16),
            nn.ReLU(),
            nn.Linear(16, 1),
            nn.Sigmoid(),  # Sigmoid maps to a clean 0-1 probability
        )

    def forward(self, x):
        return self.fc(x)


# ── 7. TRAINING ───────────────────────────────────────────────────────────
def train_model(X, y, epochs=150, lr=1e-3, val_split=0.15):
    n = X.shape[0]
    idx = np.random.permutation(n)
    n_val = int(n * val_split)
    val_idx, train_idx = idx[:n_val], idx[n_val:]

    X_train = torch.tensor(X[train_idx])
    y_train = torch.tensor(y[train_idx]).unsqueeze(1)
    X_val = torch.tensor(X[val_idx])
    y_val = torch.tensor(y[val_idx]).unsqueeze(1)

    train_ds = TensorDataset(X_train, y_train)
    train_loader = DataLoader(train_ds, batch_size=32, shuffle=True)

    model = WakeWordClassifier()
    criterion = nn.BCELoss()
    # Slight L2 penalty to ensure bias weights do not blow out
    optimizer = torch.optim.Adam(model.parameters(), lr=lr, weight_decay=1e-4)

    print("Training Binary model...")
    for epoch in range(epochs):
        model.train()
        total_loss = 0.0
        for xb, yb in train_loader:
            optimizer.zero_grad()
            preds = model(xb)
            loss = criterion(preds, yb)
            loss.backward()
            optimizer.step()
            total_loss += loss.item() * xb.size(0)
        train_loss = total_loss / len(train_ds)

        if epoch % 10 == 0 or epoch == epochs - 1:
            model.eval()
            with torch.no_grad():
                val_preds = model(X_val)
                val_loss = criterion(val_preds, y_val).item()
                val_acc = ((val_preds > 0.5).float() == y_val).float().mean().item()
            print(f"Epoch {epoch:3d}/{epochs} | train_loss={train_loss:.4f} "
                  f"| val_loss={val_loss:.4f} | val_acc={val_acc:.3f}")

    return model


# ── 8. EXPORT ─────────────────────────────────────────────────────────────
def export_model(model):
    print(f"Exporting binary model to {MODEL_NAME}...")
    model.eval()
    dummy_input = torch.randn(1, WINDOW_FRAMES, EMBED_DIM)
    torch.onnx.export(
        model,
        dummy_input,
        MODEL_NAME,
        export_params=True,
        opset_version=12,
        do_constant_folding=True,
        input_names=["embeddings"],
        output_names=["probability"],
        dynamic_axes={"embeddings": {0: "batch_size"}, "probability": {0: "batch_size"}},
        dynamo=False,
    )
    print(f"Saved {MODEL_NAME}!")


# ── 9. MAIN ────────────────────────────────────────────────────────────────
def main():
    from openwakeword.utils import AudioFeatures

    generate_tts_clips()
    convert_all_to_wav(KAIRO_MP3_DIR, KAIRO_DIR)
    convert_all_to_wav(HEY_KAIRO_MP3_DIR, HEY_KAIRO_DIR)
    convert_all_to_wav(NEG_MP3_DIR, NEG_DIR)

    print("Loading openWakeWord ONNX models...")
    feature_extractor = AudioFeatures()

    # Load and augment negatives
    X_neg_base = load_and_augment_dataset(NEG_DIR, feature_extractor, is_positive=False)
    
    # Load and augment positives (both "kairo" and "hey kairo")
    X_kairo = load_and_augment_dataset(KAIRO_DIR, feature_extractor, is_positive=True)
    X_hey_kairo = load_and_augment_dataset(HEY_KAIRO_DIR, feature_extractor, is_positive=True)

    # Generate 150 clips of pure silence and ambient room noise for negatives
    print("Generating 150 clips of pure silence and ambient static noise...")
    bg_audio = []
    for _ in range(75):
        bg_audio.append(np.zeros(CLIP_SAMPLES, dtype=np.int16))
    for _ in range(75):
        std = np.random.uniform(5, 80)
        noise = np.random.normal(0, std, CLIP_SAMPLES).astype(np.int16)
        bg_audio.append(noise)

    bg_batch = np.stack(bg_audio, axis=0)
    bg_embeddings = feature_extractor.embed_clips(bg_batch, batch_size=32)

    processed_bg = []
    for emb in bg_embeddings:
        n_frames = emb.shape[0]
        if n_frames < WINDOW_FRAMES:
            pad = np.zeros((WINDOW_FRAMES - n_frames, EMBED_DIM), dtype=np.float32)
            emb = np.vstack([emb, pad])
        elif n_frames > WINDOW_FRAMES:
            start = (n_frames - WINDOW_FRAMES) // 2
            emb = emb[start:start + WINDOW_FRAMES]
        processed_bg.append(emb.astype(np.float32))

    X_bg = np.stack(processed_bg, axis=0)
    X_neg = np.vstack([X_neg_base, X_bg])

    print(f"\nTraining samples - Negatives (0.0): {X_neg.shape[0]}, Positives (1.0): {X_kairo.shape[0] + X_hey_kairo.shape[0]}")

    # Compile binary dataset
    X = np.vstack([X_neg, X_kairo, X_hey_kairo]).astype(np.float32)
    y = np.hstack([
        np.zeros(X_neg.shape[0]),
        np.ones(X_kairo.shape[0] + X_hey_kairo.shape[0])
    ]).astype(np.float32)

    model = train_model(X, y)
    export_model(model)

    print("\nDone! Merge the model using onnx.load / onnx.save in Colab.")


if __name__ == "__main__":
    main()