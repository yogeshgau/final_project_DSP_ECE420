package com.ece420.lab1;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Registers the accelerometer and gyroscope at SENSOR_DELAY_GAME (~50 Hz) and
 * accumulates all samples into in-memory lists for offline batch processing.
 *
 * Usage:
 *   start()  — clears buffers and registers sensor listeners
 *   stop()   — unregisters listeners; buffers remain accessible for processing
 *   pause() / resume() — suspend/restore listeners on Activity lifecycle events
 *       without clearing the accumulated data
 */
public class ImuCollector implements SensorEventListener {

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final Sensor gyroscope;

    private final List<Float> accelX = new ArrayList<>();
    private final List<Float> accelY = new ArrayList<>();
    private final List<Float> accelZ = new ArrayList<>();

    private final List<Float> gyroX = new ArrayList<>();
    private final List<Float> gyroY = new ArrayList<>();
    private final List<Float> gyroZ = new ArrayList<>();

    public ImuCollector(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope     = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    }

    /** Clear all buffers and begin sensor acquisition. */
    public void start() {
        accelX.clear(); accelY.clear(); accelZ.clear();
        gyroX.clear();  gyroY.clear();  gyroZ.clear();
        register();
    }

    /** Stop sensor acquisition; accumulated data remains in the buffers. */
    public void stop() {
        unregister();
    }

    /** Temporarily unregister listeners (e.g. Activity.onPause). */
    public void pause() {
        unregister();
    }

    /** Re-register listeners without clearing buffers (e.g. Activity.onResume). */
    public void resume() {
        register();
    }

    // -------------------------------------------------------------------------
    // SensorEventListener
    // -------------------------------------------------------------------------

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelX.add(event.values[0]);
            accelY.add(event.values[1]);
            accelZ.add(event.values[2]);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            gyroX.add(event.values[0]);
            gyroY.add(event.values[1]);
            gyroZ.add(event.values[2]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    // -------------------------------------------------------------------------
    // Data accessors
    // -------------------------------------------------------------------------

    public List<Float> getAccelX() { return accelX; }
    public List<Float> getAccelY() { return accelY; }
    public List<Float> getAccelZ() { return accelZ; }

    public List<Float> getGyroX() { return gyroX; }
    public List<Float> getGyroY() { return gyroY; }
    public List<Float> getGyroZ() { return gyroZ; }

    /** Number of accelerometer samples collected in the current session. */
    public int getAccelSampleCount() { return accelX.size(); }

    /** Number of gyroscope samples collected in the current session. */
    public int getGyroSampleCount() { return gyroX.size(); }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void register() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        sensorManager.registerListener(this, gyroscope,     SensorManager.SENSOR_DELAY_GAME);
    }

    private void unregister() {
        sensorManager.unregisterListener(this);
    }
}
