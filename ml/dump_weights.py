import joblib, numpy as np, os, json

model_path = os.path.join(os.path.dirname(__file__), 'models', 'logistic_regression.pkl')
pipeline   = joblib.load(model_path)
scaler     = pipeline.named_steps['scaler']
clf        = pipeline.named_steps['clf']

out = {
    "bias":  float(clf.intercept_[0]),
    "mean":  scaler.mean_.tolist(),
    "scale": scaler.scale_.tolist(),
    "coef":  clf.coef_[0].tolist(),
}

out_path = os.path.join(os.path.dirname(__file__), 'weights.json')
with open(out_path, 'w') as f:
    json.dump(out, f, indent=2)

print(f"Wrote {out_path}")
print(f"n_features={len(out['coef'])}  bias={out['bias']:.6f}")
