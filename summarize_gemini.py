import sys
import json
import os
import google.generativeai as genai

# ---- STEP 1: Ensure authentication ----
# Uses GOOGLE_APPLICATION_CREDENTIALS (set via environment variable)
# Example: setx GOOGLE_APPLICATION_CREDENTIALS "C:\path\to\LegalOCRKey.json"

def main():
    try:
        # Load input from stdin
        payload = json.loads(sys.stdin.read())
        text = payload.get("text", "").strip()

        if not text:
            print(json.dumps({"error": "No text provided"}))
            return

        # Initialize Gemini model using ADC (service account JSON)
        genai.configure()  # Uses GOOGLE_APPLICATION_CREDENTIALS automatically

        # Pick the best available model from your list (fast + text-capable)
        model = genai.GenerativeModel("models/gemini-2.5-flash")

        # ---- STEP 2: Generate summary ----
        prompt = (
                "Summarize the following legal document clearly and concisely. "
                "Highlight its key intent, parties involved, and purpose:\n\n" + text
        )

        response = model.generate_content(prompt)

        # ---- STEP 3: Extract the text output ----
        summary = response.text if hasattr(response, "text") else str(response)

        # Output as JSON for bridge communication
        print(json.dumps({"summary": summary}, ensure_ascii=False))

    except Exception as e:
        print(json.dumps({"error": f"Gemini summarization error: {e}"}))

if __name__ == "__main__":
    main()
