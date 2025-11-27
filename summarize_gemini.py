import sys
import json
import os
import requests

def main():
    try:
        payload = json.loads(sys.stdin.read())
        text = payload.get("text", "").strip()
        model = payload.get("model", "gemini-2.5-flash")  # Remove "models/" prefix
        max_tokens = payload.get("max_tokens", 16384)

        if not text:
            print(json.dumps({"error": "No text provided"}))
            return

        # Get user OAuth token passed by Java
        token = os.getenv("GOOGLE_OAUTH_ACCESS_TOKEN")
        if not token:
            print(json.dumps({"error": "Missing OAuth access token"}))
            return

        # Vertex AI REST API endpoint (replace with your actual project ID and location)
        project_id = "legal-ocr-project-474912"  # REPLACE WITH YOUR ACTUAL PROJECT ID
        location = "us-central1"  # Or your preferred region
        endpoint = f"https://{location}-aiplatform.googleapis.com/v1/projects/{project_id}/locations/{location}/publishers/google/models/{model}:generateContent"

        headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json"
        }

        body = {
            "contents": [
                {
                    "parts": [
                        {"text": "Provide a comprehensive summary of this legal document. Include: "
                                 "1. Parties involved\n2. Purpose and intent\n3. Key obligations and rights\n"
                                 "4. Important dates and deadlines\n5. Financial terms if any\n"
                                 "6. Termination conditions\n7. Overall significance\n\n"
                                 "Ensure the summary is complete and doesn't cut off mid-sentence:\n\n" + text}
                    ]
                }
            ],
            "generationConfig": {
                "maxOutputTokens": max_tokens,
                "temperature": 0.2
            }
        }

        response = requests.post(endpoint, headers=headers, json=body, timeout=120)

        if response.status_code != 200:
            error_msg = f"Vertex AI API error: {response.status_code} - {response.text}"
            print(json.dumps({"error": error_msg}))
            return

        data = response.json()

        # Check for safety filters
        if "promptFeedback" in data and "blockReason" in data["promptFeedback"]:
            block_reason = data["promptFeedback"]["blockReason"]
            print(json.dumps({"error": f"Content blocked for safety: {block_reason}"}))
            return

        summary = ""
        if "candidates" in data and len(data["candidates"]) > 0:
            candidate = data["candidates"][0]

            # Check finish reason
            if "finishReason" in candidate and candidate["finishReason"] != "STOP":
                print(json.dumps({"warning": f"Generation finished early: {candidate['finishReason']}"}))

            if "content" in candidate and "parts" in candidate["content"]:
                if len(candidate["content"]["parts"]) > 0:
                    summary = candidate["content"]["parts"][0].get("text", "")

        if not summary:
            print(json.dumps({"error": "No summary generated - empty response from model"}))
            return

        print(json.dumps({"summary": summary}, ensure_ascii=False))

    except requests.exceptions.Timeout:
        print(json.dumps({"error": "Request timeout - model took too long to respond"}))
    except Exception as e:
        print(json.dumps({"error": f"Gemini summarization error: {str(e)}"}))

if __name__ == "__main__":
    main()