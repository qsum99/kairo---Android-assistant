from llama_cpp import Llama
import os

model_path = r"C:\Users\Someshwar Kumbar\Downloads\tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf"

if not os.path.exists(model_path):
    print(f"Error: Model not found at {model_path}")
    exit(1)

print(f"Loading model: {model_path}...")
try:
    llm = Llama(
        model_path=model_path,
        n_ctx=512,
        n_threads=4,
        verbose=False
    )

    prompt = "<|system|>\nYou are Kairo, a helpful AI.</s>\n<|user|>\nHello!</s>\n<|assistant|>\n"
    print(f"Running inference with prompt: 'Hello!'")

    output = llm(
        prompt,
        max_tokens=50,
        stop=["</s>"],
        echo=False
    )

    response = output["choices"][0]["text"]
    print("\n--- Model Response ---")
    print(response.strip())
    print("----------------------")
    print("\nSuccess! The model file is valid and responding.")

except Exception as e:
    print(f"\nFailed to run model: {e}")
