"""
Exercise classifier: Biceps Press (bp) | Shoulder Press (sp) | Triceps Extension (tp)
Data: IMU CSV files from Android app (timestamp_ms, ax, ay, az, gx, gy, gz)

Feature extraction uses sliding windows over each session.
Cross-validation is grouped by session so no window from a test session
ever appears in training (prevents leakage with small datasets).
"""

import os
import glob
import json
import numpy as np
import pandas as pd
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import GroupKFold, cross_val_predict
from sklearn.preprocessing import StandardScaler
from sklearn.pipeline import Pipeline
from sklearn.metrics import classification_report, confusion_matrix, accuracy_score
import joblib

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------

DATA_DIR   = os.path.join(os.path.dirname(__file__), '..', 'data')
MODELS_DIR = os.path.join(os.path.dirname(__file__), 'models')
WINDOW     = 100   # samples  (~2 s at 50 Hz)
STEP       = 50    # samples  (50 % overlap)
CHANNELS   = ['ax', 'ay', 'az', 'gx', 'gy', 'gz']
LABELS     = {0: 'bp', 1: 'sp', 2: 'tp'}
CLASS_NAMES = ['bp', 'sp', 'tp']

# folder name on disk for each label
FOLDERS    = {0: 'bp', 1: 'sp', 2: 'Triceps'}

os.makedirs(MODELS_DIR, exist_ok=True)

# ---------------------------------------------------------------------------
# Feature extraction
# ---------------------------------------------------------------------------

def window_features(seg):
    """Return a 1-D feature vector for a single window."""
    feats = []
    for ch in CHANNELS:
        s = seg[ch].values.astype(np.float32)
        feats.extend([
            s.mean(),
            s.std(),
            s.min(),
            s.max(),
            s.max() - s.min(),
            np.sqrt((s ** 2).mean()),
        ])

    acc_mag = np.sqrt(seg['ax']**2 + seg['ay']**2 + seg['az']**2).values
    gyr_mag = np.sqrt(seg['gx']**2 + seg['gy']**2 + seg['gz']**2).values
    for mag in (acc_mag, gyr_mag):
        feats.extend([mag.mean(), mag.std()])

    for a, b in [('ax','ay'), ('ax','az'), ('ay','az'),
                 ('gx','gy'), ('gx','gz'), ('gy','gz')]:
        feats.append(np.corrcoef(seg[a], seg[b])[0, 1])

    return feats

FEATURE_NAMES = (
    [f"{ch}_{s}" for ch in CHANNELS for s in ('mean','std','min','max','range','rms')]
    + ['acc_mag_mean','acc_mag_std','gyr_mag_mean','gyr_mag_std']
    + ['r_ax_ay','r_ax_az','r_ay_az','r_gx_gy','r_gx_gz','r_gy_gz']
)

# ---------------------------------------------------------------------------
# Data loading
# ---------------------------------------------------------------------------

def load_sessions():
    X, y, groups = [], [], []
    session_id = 0
    for label, folder in FOLDERS.items():
        pattern = os.path.join(DATA_DIR, folder, '*.csv')
        files   = sorted(glob.glob(pattern))
        if not files:
            print(f"  WARNING: no files found for label={label} in {folder}/")
            continue
        for fpath in files:
            df = pd.read_csv(fpath)
            n  = len(df)
            win_count = 0
            for start in range(0, n - WINDOW + 1, STEP):
                seg = df.iloc[start : start + WINDOW]
                X.append(window_features(seg))
                y.append(label)
                groups.append(session_id)
                win_count += 1
            print(f"  [{LABELS[label]}] {os.path.basename(fpath):20s}  "
                  f"rows={n:4d}  windows={win_count:3d}  session_id={session_id}")
            session_id += 1

    return np.array(X, dtype=np.float32), np.array(y), np.array(groups)

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

print("=" * 60)
print("Loading data ...")
X, y, groups = load_sessions()
print(f"\nTotal windows : {len(X)}")
print(f"Feature dims  : {X.shape[1]}")
for lbl, name in LABELS.items():
    print(f"  {name.upper()} windows : {(y == lbl).sum()}")
print(f"Sessions      : {groups.max() + 1}")

X = np.nan_to_num(X, nan=0.0)

# ---------------------------------------------------------------------------
# Models
# ---------------------------------------------------------------------------

models = {
    'Logistic Regression': Pipeline([
        ('scaler', StandardScaler()),
        ('clf',    LogisticRegression(C=1.0, max_iter=2000,
                                      solver='lbfgs', random_state=42)),
    ]),
    'Random Forest': RandomForestClassifier(
        n_estimators=200, max_depth=None, random_state=42
    ),
}

cv = GroupKFold(n_splits=len(np.unique(groups)))

print("\n" + "=" * 60)
for name, model in models.items():
    print(f"\n{'-' * 50}")
    print(f"  {name}")
    print(f"{'-' * 50}")

    y_pred = cross_val_predict(model, X, y, groups=groups, cv=cv)

    acc = accuracy_score(y, y_pred)
    print(f"  Overall accuracy : {acc:.4f}  ({acc*100:.1f} %)")
    print()
    print(classification_report(y, y_pred, target_names=CLASS_NAMES))

    cm = confusion_matrix(y, y_pred)
    print("  Confusion matrix (rows=true, cols=pred):")
    print(f"           {'   '.join(CLASS_NAMES)}")
    for i, row_name in enumerate(CLASS_NAMES):
        row_str = "  ".join(f"{cm[i,j]:4d}" for j in range(len(CLASS_NAMES)))
        print(f"    {row_name}  {row_str}")

    model.fit(X, y)
    model_path = os.path.join(MODELS_DIR, name.lower().replace(' ', '_') + '.pkl')
    joblib.dump(model, model_path)
    print(f"\n  Model saved -> {model_path}")

    if name == 'Random Forest':
        importances = model.feature_importances_
        top_idx = np.argsort(importances)[::-1][:10]
        print("\n  Top-10 features (Random Forest):")
        for rank, i in enumerate(top_idx, 1):
            print(f"    {rank:2d}. {FEATURE_NAMES[i]:25s}  importance={importances[i]:.4f}")

    elif name == 'Logistic Regression':
        # Show top features per class (highest |coef|)
        coef = model.named_steps['clf'].coef_   # shape (n_classes, n_features)
        for k, cname in enumerate(CLASS_NAMES):
            top_idx = np.argsort(np.abs(coef[k]))[::-1][:5]
            print(f"\n  Top-5 features for class '{cname}':")
            for rank, i in enumerate(top_idx, 1):
                print(f"    {rank}. {FEATURE_NAMES[i]:25s}  coef={coef[k,i]:+.4f}")

print("\n" + "=" * 60)
print("Done.")

# ---------------------------------------------------------------------------
# Export LR weights for Android  (run export_weights.py for the full Java file)
# ---------------------------------------------------------------------------

lr_pipeline = models['Logistic Regression']
_scaler     = lr_pipeline.named_steps['scaler']
_clf        = lr_pipeline.named_steps['clf']

weights = {
    "n_classes": int(_clf.coef_.shape[0]),
    "bias":  _clf.intercept_.tolist(),          # list of n_classes floats
    "mean":  _scaler.mean_.tolist(),
    "scale": _scaler.scale_.tolist(),
    "coef":  _clf.coef_.tolist(),               # list of n_classes lists, each 46 floats
}

weights_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'weights.json')
with open(weights_path, 'w') as _f:
    json.dump(weights, _f, indent=2)

print(f"\nWeights exported -> {weights_path}")
print(f"  n_classes={weights['n_classes']}  n_features={len(weights['mean'])}")
