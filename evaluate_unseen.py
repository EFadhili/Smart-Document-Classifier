import os
import io
import joblib
import pandas as pd
import matplotlib.pyplot as plt
from pathlib import Path
from google.cloud import vision
from docx import Document
from pdf2image import convert_from_path
from sklearn.metrics import (
    accuracy_score, precision_score, recall_score,
    f1_score, confusion_matrix, classification_report
)

# ==============================
#          CONFIGURATION
# ==============================
BASE_TEST_DIR = r"C:\Users\EFM\Downloads\Test1"
LABELS_CSV = r"C:/Users/EFM/Downloads/Test1/unseen_labels.csv"

MODEL_PATH = r"C:\Users\EFM\IdeaProjects\Backup\Legal_Project\models\svm_calibrated_model.pkl"
VECTORIZER_PATH = r"C:\Users\EFM\IdeaProjects\Backup\Legal_Project\models\tfidf_vectorizer.pkl"
SELECTOR_PATH = r"C:\Users\EFM\IdeaProjects\Backup\Legal_Project\models\feature_selector.pkl"

OUTPUT_DIR = r"C:\Users\EFM\IdeaProjects\Backup\Legal_Project\outputs"
VISION_JSON = r"C:\Users\EFM\Key\legal-ocr-project-474912-7416611b42c0.json"  # ← update this if needed

PDF_DPI = 200
MIN_TEXT_LEN = 30   # skip files with extracted text shorter than this
# ==============================

os.makedirs(OUTPUT_DIR, exist_ok=True)
os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = VISION_JSON
vision_client = vision.ImageAnnotatorClient()

# -------------------------------
# OCR helpers
# -------------------------------
def ocr_image(path):
    try:
        with io.open(path, 'rb') as img_file:
            content = img_file.read()
        image = vision.Image(content=content)
        response = vision_client.text_detection(image=image)
        return response.full_text_annotation.text or ""
    except Exception as e:
        print(f"❌ OCR error for {path}: {e}")
        return ""

def ocr_pdf(path):
    try:
        pages = convert_from_path(path, dpi=PDF_DPI)
        text = ""
        for p in pages:
            img_bytes = io.BytesIO()
            p.save(img_bytes, format="PNG")
            image = vision.Image(content=img_bytes.getvalue())
            response = vision_client.text_detection(image=image)
            text += (response.full_text_annotation.text or "") + "\n"
        return text
    except Exception as e:
        print(f"❌ PDF OCR error for {path}: {e}")
        return ""

# -------------------------------
# Generic file loader
# -------------------------------
def read_file(path):
    p = path.lower()
    try:
        if p.endswith(".txt"):
            return open(path, "r", encoding="utf8", errors="ignore").read()
        if p.endswith(".docx"):
            doc = Document(path)
            return "\n".join([p.text for p in doc.paragraphs])
        if p.endswith(".pdf"):
            # try text-extraction first (PyPDF2), else OCR fallback
            try:
                from PyPDF2 import PdfReader
                reader = PdfReader(path)
                txt = ""
                for pg in reader.pages:
                    txt += pg.extract_text() or ""
                if txt and len(txt.strip()) > MIN_TEXT_LEN:
                    return txt
            except Exception:
                pass
            return ocr_pdf(path)
        if p.endswith((".png", ".jpg", ".jpeg", ".tiff", ".bmp")):
            return ocr_image(path)
    except Exception as e:
        print(f"⚠️ read_file error {path}: {e}")
    return ""

# -------------------------------
# Filename normalization helpers
# -------------------------------
def normalize_filename(name):
    if not isinstance(name, str):
        return ""
    s = name.strip().lower()
    # remove common leading paths if any
    s = os.path.basename(s)
    # remove common extra quotes
    s = s.strip('"').strip("'")
    # optionally drop extension for matching
    stem = os.path.splitext(s)[0]
    return stem

def detect_columns(df):
    # find likely filename and label columns
    fname_candidates = [c for c in df.columns if c.lower() in ("filename","file","name","fname","file_name","file name")]
    label_candidates = [c for c in df.columns if c.lower() in ("true_label","label","class","ground_truth","gt","y")]
    fname_col = fname_candidates[0] if fname_candidates else None
    label_col = label_candidates[0] if label_candidates else None
    return fname_col, label_col

# -------------------------------
# Load model & vectorizer
# -------------------------------
print("📦 Loading model and artifacts...")
clf = joblib.load(MODEL_PATH)
vectorizer = joblib.load(VECTORIZER_PATH)
selector = joblib.load(SELECTOR_PATH)
print("✅ Loaded model, vectorizer, selector.\n")

# -------------------------------
# Collect test docs
# -------------------------------
print("🔍 Scanning test folder for files...")
texts, filenames, paths = [], [], []
for root, _, files in os.walk(BASE_TEST_DIR):
    for f in files:
        full = os.path.join(root, f)
        txt = read_file(full)
        if txt and len(txt.strip()) >= MIN_TEXT_LEN:
            texts.append(txt)
            filenames.append(f)
            paths.append(full)
        else:
            print(f"⚠️ Skipped (empty/unreadable): {f}")

print(f"📁 Loaded {len(texts)} usable documents.\n")
if len(texts) == 0:
    print("❌ No valid documents found. Check BASE_TEST_DIR and OCR config.")
    exit(1)

# -------------------------------
# Transform & predict
# -------------------------------
print("🔠 Transforming and predicting...")
X = selector.transform(vectorizer.transform(texts))
preds = clf.predict(X)
probs = None
if hasattr(clf, "predict_proba"):
    try:
        probs = clf.predict_proba(X).max(axis=1)
    except Exception:
        probs = None

results_df = pd.DataFrame({
    "filename": filenames,
    "predicted": preds,
    "confidence": probs if probs is not None else [None]*len(preds)
})
results_df["filename_norm"] = results_df["filename"].apply(normalize_filename)
results_df.to_csv(os.path.join(OUTPUT_DIR, "svm_predictions.csv"), index=False)
print(f"💾 Saved predictions → {os.path.join(OUTPUT_DIR, 'svm_predictions.csv')}\n")

# -------------------------------
# If labels CSV present, evaluate
# -------------------------------
if os.path.exists(LABELS_CSV):
    print("📊 Ground-truth file found; loading labels...")
    labels_df = pd.read_csv(LABELS_CSV)
    fname_col, label_col = detect_columns(labels_df)
    if fname_col is None or label_col is None:
        print("❌ Could not auto-detect filename/label columns in labels CSV.")
        print(f"Columns present: {labels_df.columns.tolist()}")
        print("Please ensure the CSV has a filename column (filename/file/name) and a label column (true_label/label/class).")
    else:
        labels_df = labels_df[[fname_col, label_col]].copy()
        labels_df.columns = ["filename_raw", "true_label"]
        labels_df["filename_norm"] = labels_df["filename_raw"].apply(normalize_filename)

        # merge on normalized filename (inner join)
        merged = results_df.merge(labels_df, on="filename_norm", how="inner", suffixes=("_pred","_true"))

        if merged.shape[0] == 0:
            # diagnostics
            print("\n⚠️ No filename matches found between predictions and labels (after normalization).")
            print("Sample predicted filenames (normalized):")
            print(results_df["filename_norm"].drop_duplicates().sample(min(10, len(results_df))).tolist())
            print("\nSample label filenames (normalized):")
            print(labels_df["filename_norm"].drop_duplicates().sample(min(10, len(labels_df))).tolist())
            # show unmatched lists
            preds_set = set(results_df["filename_norm"])
            labels_set = set(labels_df["filename_norm"])
            only_in_preds = sorted(list(preds_set - labels_set))[:20]
            only_in_labels = sorted(list(labels_set - preds_set))[:20]
            if only_in_preds:
                print(f"\nFiles predicted but not labeled ({len(only_in_preds)} sample): {only_in_preds[:10]}")
            if only_in_labels:
                print(f"Files labeled but not predicted ({len(only_in_labels)} sample): {only_in_labels[:10]}")
            print("\nPlease check filenames, extensions, capitalization or CSV formatting.")
        else:
            # compute metrics
            y_true = merged["true_label"]
            y_pred = merged["predicted"]

            print(f"\n✅ Matched {len(merged)} files for evaluation.\n")
            print("Classification report:")
            print(classification_report(y_true, y_pred, zero_division=0))

            acc = accuracy_score(y_true, y_pred)
            prec = precision_score(y_true, y_pred, average="weighted", zero_division=0)
            rec = recall_score(y_true, y_pred, average="weighted", zero_division=0)
            f1 = f1_score(y_true, y_pred, average="weighted", zero_division=0)

            metrics = {"Accuracy": acc, "Precision": prec, "Recall": rec, "F1-score": f1}
            print("Summary metrics:", metrics)

            # save metrics
            pd.DataFrame([metrics]).to_csv(os.path.join(OUTPUT_DIR, "svm_metrics_summary.csv"), index=False)

            # bar chart
            plt.figure(figsize=(7,4))
            plt.bar(metrics.keys(), metrics.values(), color=["#2c7fb8","#7fcdbb","#edf8b1","#f03b20"])
            plt.ylim(0, 1)
            plt.title("SVM Performance Metrics")
            plt.savefig(os.path.join(OUTPUT_DIR, "svm_metrics_bar.png"), bbox_inches="tight")
            plt.close()

            # line chart
            plt.figure(figsize=(7,4))
            plt.plot(list(metrics.keys()), list(metrics.values()), marker="o")
            plt.ylim(0,1)
            plt.title("SVM Metrics Trend")
            plt.savefig(os.path.join(OUTPUT_DIR, "svm_metrics_line.png"), bbox_inches="tight")
            plt.close()

            # confusion matrix (normalized)
            labels_order = sorted(list(set(y_true) | set(y_pred)))
            cm = confusion_matrix(y_true, y_pred, labels=labels_order, normalize='true')
            import seaborn as sns
            plt.figure(figsize=(8,6))
            sns.heatmap(cm, annot=True, fmt=".2f", xticklabels=labels_order, yticklabels=labels_order, cmap="Blues")
            plt.xlabel("Predicted")
            plt.ylabel("True")
            plt.title("Normalized Confusion Matrix")
            plt.savefig(os.path.join(OUTPUT_DIR, "svm_confusion_matrix.png"), bbox_inches="tight")
            plt.close()

            print(f"📈 Saved charts in {OUTPUT_DIR}")

print("\n✅ Script finished.")
