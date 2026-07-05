import whisper
import numpy as np
import sounddevice as sd
import torch

# Use OpenAI Whisper (pure Python + torch) and select device automatically
device = "cuda" if torch.cuda.is_available() else "cpu"
model = whisper.load_model("tiny", device=device)
samplerate = 16000  # Sample rate for recording
duration = 5  # Duration of recording in seconds

print("Recording...")
audio = sd.rec(int(duration * samplerate), samplerate=samplerate, channels=1)
sd.wait()
print("Recording finished. Transcribing (in-memory)...")

audio = audio.flatten().astype(np.float32)  # Flatten and ensure float32
result = model.transcribe(audio)
print(result.get("text", ""))

#this is the speech recognition part of the code
# recoger=sr.Recognizer()
# with sr.Microphone() as source:
#     print("Say something!")
#     audio = recoger.listen(source)
#     print("Recognizing...")
# try:
#     text = recoger.recognize_google(audio)
#     print("You said: " + text)
# except sr.UnknownValueError:
#     print("Google Speech Recognition could not understand audio")






# model = whisper.load_model("tiny")
# input_audio = sr
# with input_audio as source:
#     audio = sr.Recognizer().record(source)
# result = model.transcribe(audio)
# print(result["text"])