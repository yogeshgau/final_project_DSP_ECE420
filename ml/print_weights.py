import joblib, numpy as np, os, sys

model_path = os.path.join(os.path.dirname(__file__), 'models', 'logistic_regression.pkl')
pipeline   = joblib.load(model_path)
scaler     = pipeline.named_steps['scaler']
clf        = pipeline.named_steps['clf']

mean  = scaler.mean_
scale = scaler.scale_
coef  = clf.coef_[0]
bias  = clf.intercept_[0]

sys.stdout.reconfigure(encoding='utf-8')
print("BIAS", bias)
print("MEAN", list(mean))
print("SCALE", list(scale))
print("COEF", list(coef))
