import sys
import json
import re

def preprocess_text(text):
    # Lowercasing
    text = text.lower()
    # Remove punctuation & digits
    text = re.sub(r"[^a-z\s]", "", text)
    # Tokenize
    tokens = text.split()
    # Remove stopwords (demo list)
    stopwords = {"the", "and", "a", "is", "in", "of", "to"}
    tokens = [t for t in tokens if t not in stopwords]
    return tokens

if __name__ == "__main__":
    if len(sys.argv) > 1:
        # Command-line argument mode
        text = " ".join(sys.argv[1:])
        print(json.dumps(preprocess_text(text)))
    else:
        # Stdin mode (used by Java bridge)
        payload = json.loads(sys.stdin.read())
        if payload.get("mode") == "preprocess":
            text = payload.get("text", "")
            processed = preprocess_text(text)
            print(json.dumps(processed))
