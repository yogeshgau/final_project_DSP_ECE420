package com.ece420.lab1;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

/**
 * Continuously classifies exercise type from live IMU data.
 *
 * Maintains a rolling window of the last WINDOW accelerometer and gyroscope
 * samples. Every STEP new accelerometer samples, it copies the window,
 * extracts features, runs the logistic regression classifier on a background
 * thread, and posts the result back to the main thread via the callback.
 */
public class LiveClassifier implements SensorEventListener {

    public interface Callback {
        void onResult(ExerciseClassifier.Result result);
    }

    private static final int WINDOW = ExerciseClassifier.WINDOW;
    private static final int STEP   = 25;   // classify every ~0.5 s at 50 Hz

    private final SensorManager sensorManager;
    private final Sensor        accelerometer;
    private final Sensor        gyroscope;
    private final Callback      callback;
    private final Handler       mainHandler = new Handler(Looper.getMainLooper());

    private final List<Float> ax = new ArrayList<>(), ay = new ArrayList<>(), az = new ArrayList<>();
    private final List<Float> gx = new ArrayList<>(), gy = new ArrayList<>(), gz = new ArrayList<>();

    private int accelSinceLastClassify = 0;

    public LiveClassifier(Context context, Callback callback) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope     = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        this.callback = callback;
    }

    public void start() {
        ax.clear(); ay.clear(); az.clear();
        gx.clear(); gy.clear(); gz.clear();
        accelSinceLastClassify = 0;
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, gyroscope,     SensorManager.SENSOR_DELAY_GAME);
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }

    // -------------------------------------------------------------------------
    // SensorEventListener
    // -------------------------------------------------------------------------

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            push(ax, ay, az, event.values[0], event.values[1], event.values[2]);
            accelSinceLastClassify++;
            if (accelSinceLastClassify >= STEP && ax.size() >= WINDOW && gx.size() >= WINDOW) {
                accelSinceLastClassify = 0;
                dispatchClassify();
            }
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            push(gx, gy, gz, event.values[0], event.values[1], event.values[2]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void push(List<Float> x, List<Float> y, List<Float> z,
                      float vx, float vy, float vz) {
        x.add(vx); y.add(vy); z.add(vz);
        if (x.size() > WINDOW) { x.remove(0); y.remove(0); z.remove(0); }
    }

    private void dispatchClassify() {
        final List<Float> axC = new ArrayList<>(ax), ayC = new ArrayList<>(ay), azC = new ArrayList<>(az);
        final List<Float> gxC = new ArrayList<>(gx), gyC = new ArrayList<>(gy), gzC = new ArrayList<>(gz);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final ExerciseClassifier.Result result =
                        ExerciseClassifier.classify(axC, ayC, azC, gxC, gyC, gzC, 0);
                mainHandler.post(new Runnable() {
                    @Override public void run() { callback.onResult(result); }
                });
            }
        }).start();
    }
}
