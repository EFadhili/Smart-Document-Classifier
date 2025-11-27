import os
import pandas as pd
import random
from docx import Document
from PyPDF2 import PdfReader
import joblib
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split, cross_val_score
from sklearn.feature_selection import SelectKBest, chi2
from sklearn.metrics import classification_report, accuracy_score


# Base folder where all the subfolders are
BASE_DIR = r"D:\dataset"
MODEL_DIR = "models"

# Read CSV with just filename and label
labels_df = pd.read_csv(r"D:\Dataset Label\labels.csv")

def read_file(path):
    if path.lower().endswith(".txt"):
        with open(path, "r", encoding="utf-8", errors="ignore") as f:
            return f.read()
    elif path.lower().endswith(".docx"):
        try:
            doc = Document(path)
            return "\n".join([p.text for p in doc.paragraphs])
        except Exception:
            return ""
    elif path.lower().endswith(".pdf"):
        try:
            reader = PdfReader(path)
            text = ""
            for page in reader.pages:
                text += page.extract_text() or ""
            return text
        except Exception as e:
            print(f"⚠️ Error reading {path}: {e}")
            return ""
    else:
        return ""

texts, labels = [], []

print("🔍 Reading documents...")
for i, row in labels_df.iterrows():
    filename = row["filename"].strip().replace("/", "\\")
    label = row["label"].strip()


    # Construct full path based on label
    full_path = os.path.join(BASE_DIR, filename)

    if os.path.exists(full_path):
        text = read_file(full_path)
        if len(text.strip()) > 30:  # skip empty docs
            texts.append(text)
            labels.append(label)
    else:
        print(f"⚠️ File not found: {full_path}")

print(f"✅ Loaded {len(texts)} valid documents out of {len(labels_df)} total.")

# --- STEP 2: TEXT RANDOMIZATION ---
def degrade_text(t, rate=0.07):
    words = t.split()
    keep = [w for w in words if random.random() > rate]
    return " ".join(keep)

texts = [degrade_text(t) for t in texts]

# ---SPLIT DATA
print("📊 Splitting into train/validation/test sets...")
X_train, X_temp, y_train, y_temp = train_test_split(
    texts, labels, test_size=0.3, stratify=labels, random_state=42
)
X_valid, X_test, y_valid, y_test = train_test_split(
    X_temp, y_temp, test_size=0.5, stratify=y_temp, random_state=42
)

# --- Step 2: TF-IDF Feature Extraction ---
print("🔠 Extracting TF-IDF features...")
vectorizer = TfidfVectorizer(
    sublinear_tf=True,
    max_df=0.6,
    min_df=5,
    ngram_range=(1, 2),
    stop_words="english",
    max_features=10000
)

X_train_vec = vectorizer.fit_transform(X_train)
X_valid_vec = vectorizer.transform(X_valid)
X_test_vec = vectorizer.transform(X_test)

print(f"✅ TF-IDF matrix shape: {X_train_vec.shape}\n")

# --- STEP 4: FEATURE SELECTION ---
print("🔎 Selecting top features...")
selector = SelectKBest(chi2, k=min(10000, X_train_vec.shape[1]))
X_train_vec = selector.fit_transform(X_train_vec, y_train)
X_valid_vec = selector.transform(X_valid_vec)
X_test_vec = selector.transform(X_test_vec)
print(f"✅ Reduced feature shape: {X_train_vec.shape}\n")

# --- Step 3: Random Forest Training ---
print("\n🌲 Training Random Forest model...")
clf = RandomForestClassifier(
    n_estimators=150,
    max_depth=20,
    max_features="sqrt",
    min_samples_split=8,
    min_samples_leaf=3,
    bootstrap=True,
    oob_score=True,
    n_jobs=-1,
    random_state=42
)

clf.fit(X_train_vec, y_train)

# --- Step 4: Evaluation ---
y_pred = clf.predict(X_valid_vec)
acc = accuracy_score(y_valid, y_pred)

print("\n📈 Model Evaluation Results:")
print(f"Accuracy: {acc:.4f}")
print("\nDetailed Report:")
print(classification_report(y_valid, y_pred))

# --- STEP 7: CROSS-VALIDATION ---
print("🔁 Performing 5-fold cross-validation...")
scores = cross_val_score(clf, X_train_vec, y_train, cv=5)
print(f"Cross-validation accuracy: {scores.mean():.4f} ± {scores.std():.4f}\n")


# --- STEP 8: TEST PERFORMANCE ---
y_test_pred = clf.predict(X_test_vec)
test_acc = accuracy_score(y_test, y_test_pred)
print("🧪 Final Test Results:")
print(f"Test Accuracy: {test_acc:.4f}")
print(classification_report(y_test, y_test_pred))

# --- Step 5: Save the Model and Vectorizer ---
os.makedirs("models", exist_ok=True)
joblib.dump(vectorizer, "models/tfidf_vectorizer.pkl")
joblib.dump(selector, os.path.join(MODEL_DIR, "feature_selector.pkl"))
joblib.dump(clf, "models/random_forest_model.pkl")

print("\n💾 Model and vectorizer saved to /models/")


probs = clf.predict_proba(X_test_vec)
print("Average confidence:", probs.max(axis=1).mean())