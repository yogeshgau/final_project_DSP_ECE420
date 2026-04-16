package com.ece420.lab1;

import java.util.List;

/**
 * Signal processing pipeline for IMU-based repetition counting.
 *
 * Pipeline:
 *   1. Low-pass filter (IIR) applied to each axis independently.
 *   2. Jerk magnitude computed as the L2 norm of the discrete derivative.
 *   3. Autocorrelation on the jerk magnitude to estimate the dominant repetition period.
 *   4. Peak detection with minimum separation equal to half the estimated period.
 */
public class SignalProcessor {

    // IIR low-pass filter coefficient: y[n] = ALPHA*x[n] + (1-ALPHA)*y[n-1]
    // Approximate cutoff ~ ALPHA * fs / (2*pi). At 50 Hz, ALPHA=0.2 gives ~1.6 Hz.
    private static final float LPF_ALPHA = 0.2f;

    // Repetition period search bounds (in samples).
    // Assumes sensor is running at approximately 50 Hz (SENSOR_DELAY_GAME).
    private static final int MIN_PERIOD_SAMPLES = 20;   // ~0.4 s minimum rep duration
    private static final int MAX_PERIOD_SAMPLES = 250;  // ~5.0 s maximum rep duration

    // Peak amplitude threshold expressed as a fraction of the signal standard deviation
    // above the mean. Filters out low-energy noise bumps.
    private static final float PEAK_THRESHOLD_STD_FACTOR = 0.4f;

    // -------------------------------------------------------------------------
    // Public entry points
    // -------------------------------------------------------------------------

    /**
     * Count repetitions from raw accelerometer samples using accel-jerk magnitude.
     *
     * @param ax  list of accelerometer x-axis values (m/s^2)
     * @param ay  list of accelerometer y-axis values (m/s^2)
     * @param az  list of accelerometer z-axis values (m/s^2)
     * @return    estimated repetition count, or 0 if the signal is too short
     */
    public static int countRepsAccel(List<Float> ax, List<Float> ay, List<Float> az) {
        int n = ax.size();
        if (n < MIN_PERIOD_SAMPLES * 2) return 0;

        float[] fa = applyLPF(toArray(ax), LPF_ALPHA);
        float[] fb = applyLPF(toArray(ay), LPF_ALPHA);
        float[] fc = applyLPF(toArray(az), LPF_ALPHA);

        float[] jerk = jerkMagnitude(fa, fb, fc);
        return detectReps(jerk);
    }

    /**
     * Count repetitions from raw gyroscope samples using gyro-jerk magnitude.
     *
     * @param gx  list of gyroscope x-axis values (rad/s)
     * @param gy  list of gyroscope y-axis values (rad/s)
     * @param gz  list of gyroscope z-axis values (rad/s)
     * @return    estimated repetition count, or 0 if the signal is too short
     */
    public static int countRepsGyro(List<Float> gx, List<Float> gy, List<Float> gz) {
        int n = gx.size();
        if (n < MIN_PERIOD_SAMPLES * 2) return 0;

        float[] fa = applyLPF(toArray(gx), LPF_ALPHA);
        float[] fb = applyLPF(toArray(gy), LPF_ALPHA);
        float[] fc = applyLPF(toArray(gz), LPF_ALPHA);

        float[] jerk = jerkMagnitude(fa, fb, fc);
        return detectReps(jerk);
    }

    // -------------------------------------------------------------------------
    // Internal pipeline steps
    // -------------------------------------------------------------------------

    /**
     * Apply a first-order IIR low-pass filter to a signal.
     *
     *   y[n] = alpha * x[n] + (1 - alpha) * y[n-1]
     */
    private static float[] applyLPF(float[] x, float alpha) {
        float[] y = new float[x.length];
        y[0] = x[0];
        float oneMinusAlpha = 1.0f - alpha;
        for (int i = 1; i < x.length; i++) {
            y[i] = alpha * x[i] + oneMinusAlpha * y[i - 1];
        }
        return y;
    }

    /**
     * Compute the jerk magnitude signal from three filtered axis signals.
     *
     *   ||j_tot|| = sqrt( (da_x)^2 + (da_y)^2 + (da_z)^2 )
     *
     * where da_x[n] = a_x[n+1] - a_x[n] (discrete derivative).
     * The output has length (n - 1).
     */
    private static float[] jerkMagnitude(float[] fa, float[] fb, float[] fc) {
        int n = fa.length - 1;
        float[] jerk = new float[n];
        for (int i = 0; i < n; i++) {
            float da = fa[i + 1] - fa[i];
            float db = fb[i + 1] - fb[i];
            float dc = fc[i + 1] - fc[i];
            jerk[i] = (float) Math.sqrt(da * da + db * db + dc * dc);
        }
        return jerk;
    }

    /**
     * Estimate the dominant period via autocorrelation and count repetitions
     * by finding prominent peaks separated by at least half the estimated period.
     *
     * @param jerk  jerk magnitude signal
     * @return      repetition count
     */
    private static int detectReps(float[] jerk) {
        int period = estimatePeriod(jerk);
        if (period <= 0) return 0;
        return countPeaks(jerk, period);
    }

    /**
     * Estimate the dominant repetition period (in samples) via autocorrelation.
     *
     *   R_xx(tau) = sum_t  x(t) * x(t + tau)
     *
     * The signal is zero-meaned before computing the autocorrelation to remove
     * the DC offset introduced by the always-positive jerk magnitude.
     * The lag with the highest autocorrelation value in the valid search range
     * is returned as the estimated period L.
     *
     * @param signal  jerk magnitude signal (non-negative)
     * @return        estimated period in samples
     */
    private static int estimatePeriod(float[] signal) {
        // Zero-mean the signal
        float mean = 0;
        for (float v : signal) mean += v;
        mean /= signal.length;

        float[] s = new float[signal.length];
        for (int i = 0; i < signal.length; i++) {
            s[i] = signal[i] - mean;
        }

        int maxLag = Math.min(MAX_PERIOD_SAMPLES, signal.length / 2);
        if (maxLag < MIN_PERIOD_SAMPLES) return -1;

        float bestCorr = Float.NEGATIVE_INFINITY;
        int bestLag = MIN_PERIOD_SAMPLES;

        for (int lag = MIN_PERIOD_SAMPLES; lag <= maxLag; lag++) {
            float corr = 0;
            int terms = signal.length - lag;
            for (int t = 0; t < terms; t++) {
                corr += s[t] * s[t + lag];
            }
            corr /= terms;   // normalize by number of terms
            if (corr > bestCorr) {
                bestCorr = corr;
                bestLag = lag;
            }
        }

        return bestLag;
    }

    /**
     * Count the number of prominent peaks in the jerk signal.
     *
     * A sample is counted as a peak when:
     *   - It is a local maximum within a window of width (minSep) on each side.
     *   - Its value exceeds mean + PEAK_THRESHOLD_STD_FACTOR * std.
     *   - No previous peak was found within the last (minSep) samples.
     *
     * @param signal  jerk magnitude signal
     * @param period  estimated repetition period (samples); minSep = period / 2
     * @return        number of detected repetitions
     */
    private static int countPeaks(float[] signal, int period) {
        // Compute mean and standard deviation for threshold
        float mean = 0;
        for (float v : signal) mean += v;
        mean /= signal.length;

        float variance = 0;
        for (float v : signal) {
            float diff = v - mean;
            variance += diff * diff;
        }
        float std = (float) Math.sqrt(variance / signal.length);

        float threshold = mean + PEAK_THRESHOLD_STD_FACTOR * std;
        int minSep = Math.max(1, period / 2);

        int count = 0;
        int lastPeakIdx = -2 * minSep;

        for (int i = minSep; i < signal.length - minSep; i++) {
            if (signal[i] < threshold) continue;
            if (i - lastPeakIdx < minSep) continue;

            // Check local maximum within ±minSep window
            boolean isLocalMax = true;
            for (int j = i - minSep; j <= i + minSep; j++) {
                if (j != i && signal[j] >= signal[i]) {
                    isLocalMax = false;
                    break;
                }
            }

            if (isLocalMax) {
                count++;
                lastPeakIdx = i;
            }
        }

        return count;
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private static float[] toArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }
}
