#!/usr/bin/env python3
"""
train_model_svm.py

Train a Linear SVM (calibrated) on TF-IDF features, evaluate, and produce:
 - bar chart: average confidence per true class on test set
 - bar chart: accuracy per class on test set

Requirements:
    pip install scikit-learn joblib python-docx PyPDF2 matplotlib
    (also pdf2image/poppler only needed if you use PDF->image in loader; here we only read text)
"""

import os
import random
import joblib
import pandas as pd
import numpy as np
from docx import Document
from PyPDF2 import PdfReader
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.svm import LinearSVC
from sklearn.calibration import CalibratedClassifierCV
from sklearn.model_selection import train_test_split, cross_val_score
from sklearn.feature_selection import SelectKBest, chi2
from sklearn.metrics import classification_report, accuracy_score, confusion_matrix
import matplotlib.pyplot as plt

# ---------------- CONFIG ----------------
BASE_DIR = r"D:\dataset"                    # adapt to your dataset root
CSV_PATH = r"D:\Dataset Label\labels.csv"   # CSV with filename (relative to BASE_DIR) and label columns
MODEL_DIR = r"C:\Users\EFM\IdeaProjects\Legal_Project\models"  # where to store model & plots
RANDOM_STATE = 42
# ----------------------------------------

os.makedirs(MODEL_DIR, exist_ok=True)
os.makedirs(os.path.join(MODEL_DIR, "plots"), exist_ok=True)

def read_docx(path):
    try:
        doc = Document(path)
        return "\n".join([p.text for p in doc.paragraphs])
    except Exception:
        return ""

def read_pdf(path):
    try:
        reader = PdfReader(path)
        text = ""
        for page in reader.pages:
            text += page.extract_text() or ""
        return text
    except Exception:
        return ""

def read_file(path):
    path = path.strip()
    if path.lower().endswith(".txt"):
        try:
            with open(path, "r", encoding="utf-8", errors="ignore") as f:
                return f.read()
        except Exception:
            return ""
    elif path.lower().endswith(".docx"):
        return read_docx(path)
    elif path.lower().endswith(".pdf"):
        return read_pdf(path)
    else:
        return ""

# small random degradation to reduce template overfitting (optional)
def degrade_text(t, rate=0.07):
    words = t.split()
    keep = [w for w in words if random.random() > rate]
    return " ".join(keep)

def load_dataset():
    df = pd.read_csv(CSV_PATH)
    texts = []
    labels = []
    missing = 0
    for _, row in df.iterrows():
        filename = str(row["filename"]).strip().replace("/", "\\")
        label = str(row["label"]).strip()
        full_path = os.path.join(BASE_DIR, filename)
        if os.path.exists(full_path):
            text = read_file(full_path)
            if text and len(text.strip()) > 30:
                texts.append(text)
                labels.append(label)
            else:
                # skip extremely short / empty extraction
                pass
        else:
            missing += 1
    if missing:
        print(f"⚠️ {missing} files referenced in CSV were not found under {BASE_DIR}")
    print(f"✅ Loaded {len(texts)} documents.")
    return texts, labels

def train_and_evaluate():
    texts, labels = load_dataset()
    if len(texts) == 0:
        raise RuntimeError("No documents loaded. Check CSV_PATH and BASE_DIR.")

    # optional degradation
    texts = [degrade_text(t) for t in texts]

    # split
    X_train, X_temp, y_train, y_temp = train_test_split(
        texts, labels, test_size=0.30, stratify=labels, random_state=RANDOM_STATE
    )
    X_valid, X_test, y_valid, y_test = train_test_split(
        X_temp, y_temp, test_size=0.5, stratify=y_temp, random_state=RANDOM_STATE
    )

    # TF-IDF
    vectorizer = TfidfVectorizer(
        sublinear_tf=True, max_df=0.6, min_df=5, ngram_range=(1, 2),
        stop_words="english", max_features=10000
    )
    X_train_vec = vectorizer.fit_transform(X_train)
    X_valid_vec = vectorizer.transform(X_valid)
    X_test_vec = vectorizer.transform(X_test)

    print("TF-IDF shape:", X_train_vec.shape)

    # Feature selection
    selector = SelectKBest(chi2, k=min(3000, X_train_vec.shape[1]))
    X_train_sel = selector.fit_transform(X_train_vec, y_train)
    X_valid_sel = selector.transform(X_valid_vec)
    X_test_sel = selector.transform(X_test_vec)

    print("Selected feature shape:", X_train_sel.shape)

    # Train LinearSVC + calibration to get probabilities
    print("Training LinearSVC (calibrated)...")
    base = LinearSVC(C=1.0, max_iter=20000, dual=True)
    clf = CalibratedClassifierCV(base, cv=5, method="sigmoid")  # calibrated probabilities
    clf.fit(X_train_sel, y_train)

    # Validation & Test evaluation
    y_valid_pred = clf.predict(X_valid_sel)
    print("\nValidation report:")
    print(classification_report(y_valid, y_valid_pred))
    print("Validation accuracy:", accuracy_score(y_valid, y_valid_pred))

    # Test
    y_test_pred = clf.predict(X_test_sel)
    print("\nTest report:")
    print(classification_report(y_test, y_test_pred))
    print("Test accuracy:", accuracy_score(y_test, y_test_pred))

    # Save artifacts
    joblib.dump(vectorizer, os.path.join(MODEL_DIR, "tfidf_vectorizer.pkl"))
    joblib.dump(selector, os.path.join(MODEL_DIR, "feature_selector.pkl"))
    joblib.dump(clf, os.path.join(MODEL_DIR, "svm_calibrated_model.pkl"))
    print(f"\nSaved model & artifacts to {MODEL_DIR}")

    # ---------- Per-class metrics for plotting ----------
    labels_unique = sorted(list(set(labels)))
    # compute per-class accuracy (based on true y_test)
    per_class_counts = {lab: 0 for lab in labels_unique}
    per_class_correct = {lab: 0 for lab in labels_unique}
    per_class_confidences = {lab: [] for lab in labels_unique}  # collect max proba per sample

    # get probabilities on test set
    probas = clf.predict_proba(X_test_sel)  # shape (n_samples, n_classes)
    class_index_map = {c: i for i, c in enumerate(clf.classes_)}  # mapping class -> column index

    for true_label, pred_label, probs_row in zip(y_test, y_test_pred, probas):
        per_class_counts[true_label] += 1
        if pred_label == true_label:
            per_class_correct[true_label] += 1
        # record confidence for the class predicted (or the true class — choose true)
        # We want average confidence **for the true class** (how confident model is about true label)
        if true_label in class_index_map:
            idx = class_index_map[true_label]
            per_class_confidences[true_label].append(probs_row[idx])
        else:
            per_class_confidences[true_label].append(0.0)

    # compute statistics arrays for plotting
    classes_plot = []
    avg_conf = []
    accuracy = []
    support = []
    for lab in labels_unique:
        classes_plot.append(lab)
        support.append(per_class_counts[lab])
        if per_class_confidences[lab]:
            avg_conf.append(float(np.mean(per_class_confidences[lab])))
        else:
            avg_conf.append(0.0)
        if per_class_counts[lab] > 0:
            accuracy.append(per_class_correct[lab] / per_class_counts[lab])
        else:
            accuracy.append(0.0)

    # ---------- PLOTTING ----------
    # 1) Average confidence per true class
    plt.figure(figsize=(10, 6))
    x_pos = np.arange(len(classes_plot))
    plt.bar(x_pos, avg_conf)
    plt.xticks(x_pos, classes_plot, rotation=45, ha="right")
    plt.ylabel("Average confidence (true-class)")
    plt.title("Average predicted probability for the TRUE class (Test set)")
    plt.tight_layout()
    conf_plot_path = os.path.join(MODEL_DIR, "plots", "avg_confidence_per_class.png")
    plt.savefig(conf_plot_path)
    print(f"Saved average confidence plot to: {conf_plot_path}")
    plt.show()

    # 2) Accuracy per class
    plt.figure(figsize=(10, 6))
    plt.bar(x_pos, accuracy)
    plt.xticks(x_pos, classes_plot, rotation=45, ha="right")
    plt.ylabel("Accuracy")
    plt.ylim(0, 1.0)
    plt.title("Per-class accuracy (Test set)")
    plt.tight_layout()
    acc_plot_path = os.path.join(MODEL_DIR, "plots", "accuracy_per_class.png")
    plt.savefig(acc_plot_path)
    print(f"Saved accuracy plot to: {acc_plot_path}")
    plt.show()

    # Also save a CSV summary
    summary_df = pd.DataFrame({
        "class": classes_plot,
        "support": support,
        "avg_confidence": avg_conf,
        "accuracy": accuracy
    })
    summary_df.to_csv(os.path.join(MODEL_DIR, "plots", "per_class_summary.csv"), index=False)
    print("Saved per-class summary CSV.")

if __name__ == "__main__":
    train_and_evaluate()
