# Lab1 — Project Context

## What this app does
Android app (Java, API 28+) that uses the phone's IMU to:
1. **Count exercise reps** — straps to forearm, Start/Stop, runs signal pipeline, shows rep count
2. **Record raw IMU data** — Record/Stop, saves CSV for post-processing / ML training

## Key files

| File | Purpose |
|------|---------|
| `app/src/main/java/com/ece420/lab1/RepCounterActivity.java` | Main activity — all UI logic, two independent ImuCollector instances |
| `app/src/main/java/com/ece420/lab1/ImuCollector.java` | Registers accel + gyro at SENSOR_DELAY_GAME (~50 Hz), buffers samples + hardware timestamps |
| `app/src/main/java/com/ece420/lab1/SignalProcessor.java` | LPF → jerk magnitude → autocorrelation → peak detection → rep count |
| `app/src/main/res/layout/activity_rep_counter.xml` | Single portrait layout for the whole UI |
| `app/src/main/AndroidManifest.xml` | Permissions: MANAGE_EXTERNAL_STORAGE, ACTIVITY_RECOGNITION, HIGH_SAMPLING_RATE_SENSORS |

## UI layout (top → bottom)
- "Rep Counter" title
- Large rep count display (`textRepCount`)
- Status line (`textStatus`)
- **Start / Stop** buttons → triggers rep counting via `imuCollector`
- Mode label + **Switch Accel/Gyro Jerk** button
- Divider
- "IMU Data Recording" label
- **Record / Stop** buttons → triggers raw data capture via `dataCollector`
- Record status label (`textRecordStatus`)

## IMU data recording flow
1. Press **Record** → `dataCollector.start()` clears buffers, registers sensors
2. Press **Stop** → `dataCollector.stop()`, AlertDialog "Save / Discard"
3. Save → `ACTION_CREATE_DOCUMENT` (system file picker, no extra permission needed)
4. `writeCsvToUri()` runs on background thread, writes:

```
timestamp_ms,ax,ay,az,gx,gy,gz
0,0.12,-0.05,9.81,0.001,0.002,-0.003
...
```

- `timestamp_ms` is relative to first accel sample (hardware ns → ms)
- Rows = `min(accel_samples, gyro_samples)` — index-aligned
- Default filename: `imu_session_<unix_ms>.csv`

## Signal processing pipeline (SignalProcessor.java)
1. IIR low-pass filter (α = 0.2, ~1.6 Hz cutoff at 50 Hz)
2. Jerk magnitude: `||Δa|| = sqrt(Δax² + Δay² + Δaz²)`
3. Autocorrelation to estimate dominant rep period
4. Peak detection: peaks > mean + 0.4·std, min separation = half period

## Architecture notes
- `imuCollector` and `dataCollector` are two separate `ImuCollector` instances — they can run simultaneously
- Both are paused/resumed in `onPause`/`onResume`
- Rep counting and data recording are fully independent
- No Jetpack — plain `Activity`, `startActivityForResult` / `onActivityResult`
- Screen kept on during any session (`FLAG_KEEP_SCREEN_ON`)

## Sensor details
- Both sensors registered at `SENSOR_DELAY_GAME` ≈ 50 Hz
- Timestamps stored as `event.timestamp` (nanoseconds, hardware clock since boot)
- `ImuCollector` exposes: `getAccelX/Y/Z()`, `getGyroX/Y/Z()`, `getAccelTimestamps()`, `getGyroTimestamps()`

## Build
- `minSdk 28`, `targetSdk 34`
- Gradle 8.1.1
- Only external dep: `com.jjoe64:graphview:4.2.1` (used in legacy pedometer, not main activity)
