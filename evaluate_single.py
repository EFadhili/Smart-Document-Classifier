# evaluate_single.py
import sys, json, joblib, os

MODEL_DIR = os.path.join(os.path.dirname(__file__), "models")

# Load the trained components
vectorizer = joblib.load(os.path.join("C:/Users/EFM/IdeaProjects/Legal_Project/models/tfidf_vectorizer.pkl"))
selector = joblib.load(os.path.join("C:/Users/EFM/IdeaProjects/Legal_Project/models/feature_selector.pkl"))
model = joblib.load(os.path.join("C:/Users/EFM/IdeaProjects/Legal_Project/models/random_forest_model.pkl"))

if __name__ == "__main__":
    payload = json.loads(sys.stdin.read())
    text = payload.get("text", "")

    if not text.strip():
        print(json.dumps({"error": "No text provided"}))
        sys.exit(1)

    # Transform text into feature vector
    X_vec = vectorizer.transform([text])
    X_vec = selector.transform(X_vec)

    pred = model.predict(X_vec)[0]
    probs = model.predict_proba(X_vec)[0].max()

    print(json.dumps({
        "prediction": pred,
        "confidence": float(probs)
    }))
