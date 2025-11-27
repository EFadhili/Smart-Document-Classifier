import os
import pandas as pd
import numpy as np
from sklearn.metrics import classification_report, confusion_matrix, accuracy_score
import joblib
import matplotlib.pyplot as plt
import seaborn as sns

# ====== CONFIG - edit paths here ======
PRED_CSV = r"C:\Users\EFM\IdeaProjects\Backup\Legal_Project\outputs\svm_predictions.csv"
LABELS_CSV = r"C:\Users\EFM\Downloads\Test1\unseen_labels.csv"
PROBS_CSV = None  # optional: path to CSV with probability columns aligned with PRED_CSV (or None)
# If you saved probabilities in a separate file or as columns, set PROBS_CSV accordingly.
# =====================================

def normalize_filename(fn):
    # keep only basename, lower-case, strip spaces
    return os.path.basename(str(fn)).strip().lower()

def normalize_label(l):
    if pd.isna(l): return ""
    s = str(l).strip()
    # common fixes (extend as needed)
    s = s.replace("  ", " ")
    # unify common typos / variants:
    s = s.replace("power of attoney", "power of attorney")
    s = s.replace("power of attoney", "power of attorney")
    s = s.replace("petitions", "petition")
    s = s.replace("contracts", "contract")
    s = s.replace("rulings", "ruling")
    s = s.replace("affidavits", "affidavit")
    # remove weird chars, unify case
    return s.lower()

print("🔎 Loading CSVs...")
pred_df = pd.read_csv(PRED_CSV)
labels_df = pd.read_csv(LABELS_CSV)

print(f"Prediction rows: {len(pred_df)}, Label rows: {len(labels_df)}")

# Normalize filename columns
if 'filename' not in pred_df.columns:
    raise SystemExit("Predictions CSV must have 'filename' column")
if 'filename' not in labels_df.columns:
    raise SystemExit("Labels CSV must have 'filename' column")

pred_df['__fname'] = pred_df['filename'].apply(normalize_filename)
labels_df['__fname'] = labels_df['filename'].apply(normalize_filename)

# Normalize labels
# predictions may be in column 'predicted' or 'prediction'
pred_label_col = 'predicted' if 'predicted' in pred_df.columns else \
    ('prediction' if 'prediction' in pred_df.columns else None)
if pred_label_col is None:
    raise SystemExit("Predictions CSV must have 'predicted' or 'prediction' column")

pred_df['__pred'] = pred_df[pred_label_col].apply(normalize_label)

true_label_col = 'true_label' if 'true_label' in labels_df.columns else \
    ('label' if 'label' in labels_df.columns else None)
if true_label_col is None:
    raise SystemExit("Labels CSV must have 'true_label' or 'label' column")

labels_df['__true'] = labels_df[true_label_col].apply(normalize_label)

# Show distinct labels found
print("\nDistinct predicted labels (raw -> normalized) sample:")
print(pred_df[[pred_label_col, '__pred']].drop_duplicates().head(20))
print("\nDistinct true labels (raw -> normalized) sample:")
print(labels_df[[true_label_col, '__true']].drop_duplicates().head(20))

# Check duplicates
dups_pred = pred_df['__fname'].duplicated().sum()
dups_lab = labels_df['__fname'].duplicated().sum()
print(f"\nDuplicate filenames: predictions={dups_pred}, labels={dups_lab}")

# Merge on normalized filename
merged = pd.merge(pred_df, labels_df, on='__fname', suffixes=('_pred', '_true'), how='outer', indicator=True)

print("\nMerge indicator counts:")
print(merged['_merge'].value_counts())

# Rows that matched
matched = merged[merged['_merge'] == 'both']
print(f"\nMatched rows: {len(matched)}")

if len(matched) == 0:
    print("⚠️ No matched filenames. Check filename normalization and extensions.")
    # print a sample of mismatches
    print("\nSample preds not matched:")
    print(merged[merged['_merge']=='left_only'][['filename_pred']].head(20))
    print("\nSample truths not matched:")
    print(merged[merged['_merge']=='right_only'][['filename_true']].head(20))
    raise SystemExit("No matches: aborting metric computation.")

# Inspect unmatched examples (helpful)
left_only = merged[merged['_merge']=='left_only']
right_only = merged[merged['_merge']=='right_only']
if not left_only.empty:
    print(f"\nPredictions without ground truth: {len(left_only)} (showing up to 10):")
    print(left_only[['filename_pred']].head(10))
if not right_only.empty:
    print(f"\nGround truth without predictions: {len(right_only)} (showing up to 10):")
    print(right_only[['filename_true']].head(10))

# Build y_true, y_pred
y_true = matched['__true']
y_pred = matched['__pred']

print(f"\nFinal evaluation set size: {len(y_true)}")
if len(y_true) == 0:
    raise SystemExit("No rows to evaluate after merge.")

# Unique label sets
labels_sorted = sorted(list(set(y_true.tolist() + y_pred.tolist())))
print("\nLabels used for evaluation:", labels_sorted)

# Compute metrics
print("\n✅ Classification report:")
print(classification_report(y_true, y_pred, labels=labels_sorted, zero_division=0))

acc = accuracy_score(y_true, y_pred)
cm = confusion_matrix(y_true, y_pred, labels=labels_sorted)
print(f"\nAccuracy: {acc:.4f}")
print("\nConfusion matrix (rows=true, cols=pred):")
print(pd.DataFrame(cm, index=labels_sorted, columns=labels_sorted))

# Plot confusion matrix (small)
try:
    plt.figure(figsize=(8,6))
    sns.heatmap(cm, annot=True, fmt='d', cmap='Blues', xticklabels=labels_sorted, yticklabels=labels_sorted)
    plt.title('Confusion matrix')
    plt.ylabel('True')
    plt.xlabel('Predicted')
    plt.tight_layout()
    plt.show()
except Exception as e:
    print("Could not draw plot:", e)

# Average confidence if probabilities available in predictions
# Option A: probabilities as separate file PROBS_CSV aligned by filename with columns for each class, or
# Option B: predictions file contains 'confidence' column (max probability)
if PROBS_CSV and os.path.exists(PROBS_CSV):
    probs_df = pd.read_csv(PROBS_CSV)
    probs_df['__fname'] = probs_df['filename'].apply(normalize_filename)
    probs_merged = pd.merge(matched, probs_df, on='__fname', how='left')
    # Assuming probs_df has a 'confidence' column or class probability columns; try to detect 'confidence'
    if 'confidence' in probs_merged.columns:
        avg_conf = probs_merged['confidence'].mean()
        print(f"\nAverage confidence (from PROBS_CSV): {avg_conf:.3f}")
elif 'confidence' in pred_df.columns:
    # If predictions CSV included a confidence column
    matched2 = matched.copy()
    if 'confidence_pred' in matched2.columns:
        avg_conf = matched2['confidence_pred'].astype(float).mean()
        print(f"\nAverage confidence (from predictions file): {avg_conf:.3f}")
    else:
        print("\nNo 'confidence' column found in predictions CSV.")

print("\nDone. Use the unmatched filename samples above to fix label/filename issues and re-run.")
