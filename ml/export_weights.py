"""
Reads weights.json (produced by train.py) and writes ExerciseClassifier.java
into the Android source tree with all parameters hardcoded.

Run this after train.py:
    py ml/train.py
    py ml/export_weights.py
"""

import os
import json

WEIGHTS_PATH = os.path.join(os.path.dirname(__file__), 'weights.json')
OUT_PATH     = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main',
                             'java', 'com', 'ece420', 'lab1', 'ExerciseClassifier.java')

with open(WEIGHTS_PATH) as f:
    w = json.load(f)

n_classes  = w['n_classes']
n_features = len(w['mean'])
bias       = w['bias']          # list[n_classes]
mean       = w['mean']          # list[n_features]
scale      = w['scale']         # list[n_features]
coef       = w['coef']          # list[n_classes][n_features]

print(f"n_classes={n_classes}  n_features={n_features}")

def fmt_1d(name, arr, indent=8):
    pad = " " * indent
    vals = ",\n".join(f"{pad}    {v:.10f}f" for v in arr)
    return f"{pad}private static final float[] {name} = {{\n{vals}\n{pad}}};"

def fmt_2d(name, matrix, indent=8):
    pad = " " * indent
    rows = []
    for row in matrix:
        row_vals = ", ".join(f"{v:.10f}f" for v in row)
        rows.append(f"{pad}    {{ {row_vals} }}")
    inner = ",\n".join(rows)
    return f"{pad}private static final float[][] {name} = {{\n{inner}\n{pad}}};"

java = f"""\
package com.ece420.lab1;

import java.util.List;

/**
 * Multinomial logistic regression classifier for exercise type recognition.
 *
 * Classes: 0 = Biceps Press  |  1 = Shoulder Press  |  2 = Triceps Extension
 *
 * Feature vector (46 dims) matches ml/train.py exactly:
 *   per channel [ax,ay,az,gx,gy,gz]: mean,std,min,max,range,rms  -> 36
 *   accel magnitude mean, std                                     ->  2
 *   gyro  magnitude mean, std                                     ->  2
 *   Pearson r: ax-ay, ax-az, ay-az, gx-gy, gx-gz, gy-gz          ->  6
 */
public class ExerciseClassifier {{

    public static final int    LABEL_BP = 0;
    public static final int    LABEL_SP = 1;
    public static final int    LABEL_TP = 2;
    public static final String[] LABEL_NAMES = {{
        "Biceps Press", "Shoulder Press", "Triceps Extension"
    }};

    static final int N_FEATURES = {n_features};
    static final int N_CLASSES  = {n_classes};
    static final int WINDOW     = 100;

{fmt_1d("BIAS",         bias)}

{fmt_1d("SCALER_MEAN",  mean)}

{fmt_1d("SCALER_SCALE", scale)}

{fmt_2d("COEF",         coef)}

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public static class Result {{
        public final int    label;
        public final String name;
        public final float  confidence;
        Result(int label, float confidence) {{
            this.label      = label;
            this.name       = LABEL_NAMES[label];
            this.confidence = confidence;
        }}
    }}

    /**
     * Classify a WINDOW-length slice starting at {{@code start}}.
     * Returns null if fewer than WINDOW samples are available from start.
     */
    public static Result classify(List<Float> ax, List<Float> ay, List<Float> az,
                                  List<Float> gx, List<Float> gy, List<Float> gz,
                                  int start) {{
        if (ax.size() - start < WINDOW || gx.size() - start < WINDOW) return null;

        float[] features = extractFeatures(ax, ay, az, gx, gy, gz, start);

        // Compute logit for each class: bias + coef . z-score(features)
        float[] logits = new float[N_CLASSES];
        for (int k = 0; k < N_CLASSES; k++) {{
            logits[k] = BIAS[k];
            for (int i = 0; i < N_FEATURES; i++) {{
                logits[k] += COEF[k][i] * (features[i] - SCALER_MEAN[i]) / SCALER_SCALE[i];
            }}
        }}

        // Numerically stable softmax
        float maxLogit = logits[0];
        for (float v : logits) if (v > maxLogit) maxLogit = v;
        float[] probs = new float[N_CLASSES];
        float   sum   = 0f;
        for (int k = 0; k < N_CLASSES; k++) {{
            probs[k] = (float) Math.exp(logits[k] - maxLogit);
            sum += probs[k];
        }}
        for (int k = 0; k < N_CLASSES; k++) probs[k] /= sum;

        int label = 0;
        for (int k = 1; k < N_CLASSES; k++) if (probs[k] > probs[label]) label = k;

        return new Result(label, probs[label]);
    }}

    // -----------------------------------------------------------------------
    // Feature extraction  (must stay in sync with ml/train.py:window_features)
    // -----------------------------------------------------------------------

    static float[] extractFeatures(List<Float> ax, List<Float> ay, List<Float> az,
                                   List<Float> gx, List<Float> gy, List<Float> gz,
                                   int start) {{
        float[] axW = slice(ax, start);
        float[] ayW = slice(ay, start);
        float[] azW = slice(az, start);
        float[] gxW = slice(gx, start);
        float[] gyW = slice(gy, start);
        float[] gzW = slice(gz, start);

        float[] feat = new float[N_FEATURES];
        int idx = 0;

        for (float[] ch : new float[][]{{axW, ayW, azW, gxW, gyW, gzW}}) {{
            feat[idx++] = mean(ch);
            feat[idx++] = std(ch);
            feat[idx++] = min(ch);
            feat[idx++] = max(ch);
            feat[idx++] = max(ch) - min(ch);
            feat[idx++] = rms(ch);
        }}

        float[] accMag = magnitude(axW, ayW, azW);
        feat[idx++] = mean(accMag);
        feat[idx++] = std(accMag);

        float[] gyrMag = magnitude(gxW, gyW, gzW);
        feat[idx++] = mean(gyrMag);
        feat[idx++] = std(gyrMag);

        feat[idx++] = pearson(axW, ayW);
        feat[idx++] = pearson(axW, azW);
        feat[idx++] = pearson(ayW, azW);
        feat[idx++] = pearson(gxW, gyW);
        feat[idx++] = pearson(gxW, gzW);
        feat[idx++] = pearson(gyW, gzW);

        return feat;
    }}

    // -----------------------------------------------------------------------
    // Math helpers
    // -----------------------------------------------------------------------

    private static float[] slice(List<Float> src, int start) {{
        float[] out = new float[WINDOW];
        for (int i = 0; i < WINDOW; i++) out[i] = src.get(start + i);
        return out;
    }}

    private static float mean(float[] a) {{
        float s = 0; for (float v : a) s += v; return s / a.length;
    }}

    private static float std(float[] a) {{
        float m = mean(a), s = 0;
        for (float v : a) s += (v - m) * (v - m);
        return (float) Math.sqrt(s / a.length);
    }}

    private static float min(float[] a) {{
        float m = a[0]; for (float v : a) if (v < m) m = v; return m;
    }}

    private static float max(float[] a) {{
        float m = a[0]; for (float v : a) if (v > m) m = v; return m;
    }}

    private static float rms(float[] a) {{
        float s = 0; for (float v : a) s += v * v;
        return (float) Math.sqrt(s / a.length);
    }}

    private static float[] magnitude(float[] x, float[] y, float[] z) {{
        float[] m = new float[x.length];
        for (int i = 0; i < x.length; i++)
            m[i] = (float) Math.sqrt(x[i]*x[i] + y[i]*y[i] + z[i]*z[i]);
        return m;
    }}

    private static float pearson(float[] a, float[] b) {{
        float ma = mean(a), mb = mean(b), num = 0, da2 = 0, db2 = 0;
        for (int i = 0; i < a.length; i++) {{
            float da = a[i] - ma, db = b[i] - mb;
            num += da * db; da2 += da * da; db2 += db * db;
        }}
        float den = (float) Math.sqrt(da2 * db2);
        return den == 0 ? 0f : num / den;
    }}
}}
"""

os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
with open(OUT_PATH, 'w') as f:
    f.write(java)

print(f"Wrote -> {OUT_PATH}")
