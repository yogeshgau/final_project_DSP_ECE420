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
public class ExerciseClassifier {

    public static final int    LABEL_BP = 0;
    public static final int    LABEL_SP = 1;
    public static final int    LABEL_TP = 2;
    public static final String[] LABEL_NAMES = {
        "Biceps Press", "Shoulder Press", "Triceps Extension"
    };

    static final int N_FEATURES = 46;
    static final int N_CLASSES  = 3;
    static final int WINDOW     = 100;

        private static final float[] BIAS = {
            0.0204526769f,
            0.2819801578f,
            -0.3024328347f
        };

        private static final float[] SCALER_MEAN = {
            2.2688826570f,
            3.3822142262f,
            -4.4294906889f,
            9.9045549459f,
            14.3340456563f,
            5.7006900907f,
            1.7329396943f,
            5.0355759070f,
            -6.1277894743f,
            11.8179027912f,
            17.9456922999f,
            8.0083960544f,
            1.2119755493f,
            3.1077308558f,
            -6.1694202802f,
            8.7535977876f,
            14.9230180710f,
            4.4346901979f,
            0.0159912489f,
            1.6833598106f,
            -2.9090344749f,
            2.8904996509f,
            5.7995341296f,
            1.7325276410f,
            -0.0157628254f,
            1.2085938748f,
            -2.6949968210f,
            2.6534984324f,
            5.3484952615f,
            1.2521721857f,
            -0.0081115286f,
            1.6739494469f,
            -2.8723614194f,
            2.7144249795f,
            5.5867863976f,
            1.7136378522f,
            10.5722563559f,
            3.8004941074f,
            2.7584471888f,
            1.1256681740f,
            -0.0137830777f,
            0.2222950192f,
            -0.1852188525f,
            0.1837597487f,
            -0.1292520022f,
            -0.3440639592f
        };

        private static final float[] SCALER_SCALE = {
            4.3583906780f,
            1.7617888920f,
            6.6002386886f,
            5.7984178734f,
            8.0993129153f,
            2.4877306978f,
            5.7847859819f,
            2.1186937557f,
            8.1426631882f,
            5.5473418413f,
            6.8125311431f,
            1.4759094921f,
            3.2769788785f,
            1.4869398691f,
            4.3009874638f,
            5.2535608714f,
            6.7472046595f,
            2.0999951022f,
            0.4215160646f,
            0.9934382113f,
            1.3020145678f,
            1.4586414255f,
            2.5910605600f,
            0.9984484218f,
            0.3740867992f,
            0.5242421815f,
            1.2519543593f,
            1.2869453728f,
            2.2257825567f,
            0.5547821550f,
            0.3979199391f,
            0.8538561345f,
            1.2302767522f,
            1.2237176655f,
            2.3288485692f,
            0.8677721186f,
            0.9118912697f,
            1.4240574819f,
            0.7656623147f,
            0.3185189505f,
            0.6185787430f,
            0.6247136775f,
            0.5012589161f,
            0.5803238429f,
            0.7573306312f,
            0.3109745744f
        };

        private static final float[][] COEF = {
            { 0.1667751472f, -0.3039144468f, 0.1481000774f, -0.3336285329f, -0.3595383481f, 0.1482658030f, -0.6134732225f, 0.0042665887f, -0.2957809121f, -0.7958120590f, -0.2944862768f, -0.1677483510f, 0.1854175281f, -0.1341438477f, -0.0981869204f, 0.0145563929f, 0.0739229961f, 0.0457403585f, -0.0312352194f, 0.5247497070f, -0.2018033728f, 0.1919026353f, 0.2094386022f, 0.4444862213f, 0.1097162604f, -0.1213611968f, 0.1099318345f, -0.0408155945f, -0.0854337994f, -0.2411948997f, 0.1039873653f, -0.0323728583f, 0.0914008138f, -0.1605177494f, -0.1326306578f, -0.1607295178f, 0.1295249014f, -0.2425620587f, 0.3068608741f, 0.0549324322f, 0.0507241578f, -0.2287032826f, -0.2678634021f, -0.1655089447f, 0.2597417446f, -0.4381339452f },
            { 0.2304024613f, -0.1179999753f, 0.3094326431f, -0.0334385540f, -0.2760999839f, -0.0087681226f, 0.4589480035f, -0.0073115022f, 0.5612153879f, 0.6795740472f, -0.1174244951f, 0.0851252442f, -0.1804046764f, -0.1248664792f, 0.1364888996f, 0.1585396895f, 0.0364389411f, 0.0168014580f, 0.0112728739f, -0.2633074321f, 0.2036263466f, -0.6071821858f, -0.4441368393f, -0.2687052238f, 0.2512597693f, 0.1119365669f, -0.4412822321f, 0.3216667939f, 0.4341990485f, -0.1866031396f, -0.1890099651f, -0.0521024343f, -0.3293677447f, -0.1038687324f, 0.1194183865f, -0.1454292898f, -0.0156434824f, 0.0634757329f, -0.3899680131f, -0.2950930350f, -0.1557744119f, 0.1950084033f, 0.0429121281f, 0.0880506318f, 0.0822659849f, 0.2905990649f },
            { -0.3971776086f, 0.4219144222f, -0.4575327205f, 0.3670670869f, 0.6356383320f, -0.1394976803f, 0.1545252189f, 0.0030449135f, -0.2654344758f, 0.1162380117f, 0.4119107719f, 0.0826231068f, -0.0050128517f, 0.2590103269f, -0.0383019793f, -0.1730960824f, -0.1103619371f, -0.0625418164f, 0.0199623455f, -0.2614422749f, -0.0018229737f, 0.4152795505f, 0.2346982371f, -0.1757809975f, -0.3609760297f, 0.0094246299f, 0.3313503976f, -0.2808511994f, -0.3487652491f, 0.4277980393f, 0.0850225997f, 0.0844752926f, 0.2379669309f, 0.2643864818f, 0.0132122714f, 0.3061588076f, -0.1138814190f, 0.1790863258f, 0.0831071390f, 0.2401606028f, 0.1050502540f, 0.0336948793f, 0.2249512741f, 0.0774583129f, -0.3420077295f, 0.1475348803f }
        };

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    public static class Result {
        public final int    label;
        public final String name;
        public final float  confidence;
        Result(int label, float confidence) {
            this.label      = label;
            this.name       = LABEL_NAMES[label];
            this.confidence = confidence;
        }
    }

    /**
     * Classify a WINDOW-length slice starting at {@code start}.
     * Returns null if fewer than WINDOW samples are available from start.
     */
    public static Result classify(List<Float> ax, List<Float> ay, List<Float> az,
                                  List<Float> gx, List<Float> gy, List<Float> gz,
                                  int start) {
        if (ax.size() - start < WINDOW || gx.size() - start < WINDOW) return null;

        float[] features = extractFeatures(ax, ay, az, gx, gy, gz, start);

        // Compute logit for each class: bias + coef . z-score(features)
        float[] logits = new float[N_CLASSES];
        for (int k = 0; k < N_CLASSES; k++) {
            logits[k] = BIAS[k];
            for (int i = 0; i < N_FEATURES; i++) {
                logits[k] += COEF[k][i] * (features[i] - SCALER_MEAN[i]) / SCALER_SCALE[i];
            }
        }

        // Numerically stable softmax
        float maxLogit = logits[0];
        for (float v : logits) if (v > maxLogit) maxLogit = v;
        float[] probs = new float[N_CLASSES];
        float   sum   = 0f;
        for (int k = 0; k < N_CLASSES; k++) {
            probs[k] = (float) Math.exp(logits[k] - maxLogit);
            sum += probs[k];
        }
        for (int k = 0; k < N_CLASSES; k++) probs[k] /= sum;

        int label = 0;
        for (int k = 1; k < N_CLASSES; k++) if (probs[k] > probs[label]) label = k;

        return new Result(label, probs[label]);
    }

    // -----------------------------------------------------------------------
    // Feature extraction  (must stay in sync with ml/train.py:window_features)
    // -----------------------------------------------------------------------

    static float[] extractFeatures(List<Float> ax, List<Float> ay, List<Float> az,
                                   List<Float> gx, List<Float> gy, List<Float> gz,
                                   int start) {
        float[] axW = slice(ax, start);
        float[] ayW = slice(ay, start);
        float[] azW = slice(az, start);
        float[] gxW = slice(gx, start);
        float[] gyW = slice(gy, start);
        float[] gzW = slice(gz, start);

        float[] feat = new float[N_FEATURES];
        int idx = 0;

        for (float[] ch : new float[][]{axW, ayW, azW, gxW, gyW, gzW}) {
            feat[idx++] = mean(ch);
            feat[idx++] = std(ch);
            feat[idx++] = min(ch);
            feat[idx++] = max(ch);
            feat[idx++] = max(ch) - min(ch);
            feat[idx++] = rms(ch);
        }

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
    }

    // -----------------------------------------------------------------------
    // Math helpers
    // -----------------------------------------------------------------------

    private static float[] slice(List<Float> src, int start) {
        float[] out = new float[WINDOW];
        for (int i = 0; i < WINDOW; i++) out[i] = src.get(start + i);
        return out;
    }

    private static float mean(float[] a) {
        float s = 0; for (float v : a) s += v; return s / a.length;
    }

    private static float std(float[] a) {
        float m = mean(a), s = 0;
        for (float v : a) s += (v - m) * (v - m);
        return (float) Math.sqrt(s / a.length);
    }

    private static float min(float[] a) {
        float m = a[0]; for (float v : a) if (v < m) m = v; return m;
    }

    private static float max(float[] a) {
        float m = a[0]; for (float v : a) if (v > m) m = v; return m;
    }

    private static float rms(float[] a) {
        float s = 0; for (float v : a) s += v * v;
        return (float) Math.sqrt(s / a.length);
    }

    private static float[] magnitude(float[] x, float[] y, float[] z) {
        float[] m = new float[x.length];
        for (int i = 0; i < x.length; i++)
            m[i] = (float) Math.sqrt(x[i]*x[i] + y[i]*y[i] + z[i]*z[i]);
        return m;
    }

    private static float pearson(float[] a, float[] b) {
        float ma = mean(a), mb = mean(b), num = 0, da2 = 0, db2 = 0;
        for (int i = 0; i < a.length; i++) {
            float da = a[i] - ma, db = b[i] - mb;
            num += da * db; da2 += da * da; db2 += db * db;
        }
        float den = (float) Math.sqrt(da2 * db2);
        return den == 0 ? 0f : num / den;
    }
}
