package com.ece420.lab1;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.graphics.Typeface;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Main activity for the IMU-based repetition counter.
 *
 * The user straps the phone to their forearm, presses Start, performs the exercise,
 * then presses Stop. The buffered IMU session is then processed through the full
 * signal pipeline (LPF → jerk magnitude → autocorrelation → peak detection) and
 * the resulting repetition count is displayed on screen.
 *
 * Two signal sources are supported and can be toggled with the Mode button:
 *   - Accel Jerk: uses the linear accelerometer (default)
 *   - Gyro Jerk:  uses the gyroscope
 */
public class RepCounterActivity extends Activity {

    private static final String TAG            = "RepCounterActivity";
    private static final int    REQUEST_SAVE_CSV = 1001;

    private static final int MODE_ACCEL = 0;
    private static final int MODE_GYRO  = 1;

    private ImuCollector imuCollector;
    private boolean      recording  = false;
    private int          signalMode = MODE_ACCEL;

    private ImuCollector  dataCollector;
    private boolean       dataRecording = false;

    private LiveClassifier     liveClassifier;
    private int                lastDetectedLabel = -1;
    private float              sessionCalories   = 0f;
    private TextView           textExerciseType;
    private TextView           textConfidence;
    private TextView           textLiveCalories;

    private Button   buttonStart;
    private Button   buttonNext;
    private Button   buttonStop;
    private Button   buttonMode;
    private TextView textRepCount;
    private TextView textStatus;
    private TextView textMode;

    private Button   buttonRecord;
    private Button   buttonRecordStop;
    private TextView textRecordStatus;

    // Stats
    private TextView        textStatDuration;
    private TextView        textStatRepTime;
    private TextView        textStatMaxAccel;
    private TextView        textStatCalories;
    private EditText        editWeight;
    private EditText        editLoad;
    private SharedPreferences prefs;
    private long            startTimeMs;
    private long            stopTimeMs;

    private static final String PREF_FILE   = "lab1_prefs";
    private static final String PREF_WEIGHT = "body_weight";
    private static final String PREF_LOAD   = "load_weight";
    private static final float  MET_BP      = 4.0f;
    private static final float  MET_SP      = 5.0f;
    private static final float  MET_TP      = 3.5f;

    // Range-of-motion estimates per exercise (metres, one-way arc)
    private static final float ROM_BP      = 0.35f;   // bicep curl  (~35 cm)
    private static final float ROM_SP      = 0.55f;   // shoulder press (~55 cm)
    private static final float ROM_TP      = 0.40f;   // triceps extension (~40 cm)
    private static final float ROM_DEFAULT = 0.40f;

    // Session history
    private LinearLayout layoutSetHistory;
    private Button       buttonClearSession;
    private int          setNumber = 0;

    // -------------------------------------------------------------------------
    // Activity lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_rep_counter);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        imuCollector  = new ImuCollector(this);
        dataCollector = new ImuCollector(this);
        liveClassifier = new LiveClassifier(this, new LiveClassifier.Callback() {
            @Override
            public void onResult(ExerciseClassifier.Result result) {
                onLiveResult(result);
            }
        });

        prefs            = getSharedPreferences(PREF_FILE, MODE_PRIVATE);

        textRepCount      = (TextView) findViewById(R.id.textRepCount);
        textStatus        = (TextView) findViewById(R.id.textStatus);
        textMode          = (TextView) findViewById(R.id.textMode);
        buttonStart       = (Button)   findViewById(R.id.buttonStart);
        buttonStop        = (Button)   findViewById(R.id.buttonStop);
        buttonMode        = (Button)   findViewById(R.id.buttonMode);
        buttonRecord      = (Button)   findViewById(R.id.buttonRecord);
        buttonRecordStop  = (Button)   findViewById(R.id.buttonRecordStop);
        textRecordStatus  = (TextView) findViewById(R.id.textRecordStatus);
        textExerciseType  = (TextView) findViewById(R.id.textExerciseType);
        textConfidence    = (TextView) findViewById(R.id.textConfidence);
        textLiveCalories  = (TextView) findViewById(R.id.textLiveCalories);
        buttonNext        = (Button)   findViewById(R.id.buttonNext);
        textStatDuration  = (TextView) findViewById(R.id.textStatDuration);
        textStatRepTime   = (TextView) findViewById(R.id.textStatRepTime);
        textStatMaxAccel  = (TextView) findViewById(R.id.textStatMaxAccel);
        textStatCalories  = (TextView) findViewById(R.id.textStatCalories);
        editWeight         = (EditText)      findViewById(R.id.editWeight);
        editLoad           = (EditText)      findViewById(R.id.editLoad);
        layoutSetHistory   = (LinearLayout)  findViewById(R.id.layoutSetHistory);
        buttonClearSession = (Button)        findViewById(R.id.buttonClearSession);

        buttonClearSession.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                layoutSetHistory.removeAllViews();
                setNumber = 0;
            }
        });

        float savedWeight = prefs.getFloat(PREF_WEIGHT, 70f);
        editWeight.setText(savedWeight == (int) savedWeight
                ? String.valueOf((int) savedWeight) : String.valueOf(savedWeight));

        float savedLoad = prefs.getFloat(PREF_LOAD, 0f);
        editLoad.setText(savedLoad == (int) savedLoad
                ? String.valueOf((int) savedLoad) : String.valueOf(savedLoad));

        buttonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!recording) {
                    imuCollector.start();
                    recording        = true;
                    startTimeMs      = System.currentTimeMillis();
                    sessionCalories  = 0f;
                    textRepCount.setText("--");
                    textStatus.setText("Recording…");
                    textStatDuration.setText("—");
                    textStatRepTime.setText("—");
                    textStatMaxAccel.setText("—");
                    textStatCalories.setText("—");
                    textLiveCalories.setText("0 kcal");
                    Log.d(TAG, "Recording started.");
                }
            }
        });

        buttonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (recording) {
                    imuCollector.stop();
                    recording   = false;
                    stopTimeMs  = System.currentTimeMillis();
                    int samples = imuCollector.getAccelSampleCount();
                    textStatus.setText("Processing " + samples + " samples…");
                    Log.d(TAG, "Recording stopped. Accel samples: " + samples
                            + "  Gyro samples: " + imuCollector.getGyroSampleCount());
                    processAndDisplay();
                }
            }
        });

        buttonNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (recording) {
                    imuCollector.stop();
                    recording  = false;
                    stopTimeMs = System.currentTimeMillis();
                    textStatus.setText("Processing set…");
                    processAndDisplay(true);
                }
            }
        });

        buttonMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!recording) {
                    toggleMode();
                }
            }
        });

        buttonRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!dataRecording) {
                    dataCollector.start();
                    dataRecording = true;
                    textRecordStatus.setText("Recording…");
                    Log.d(TAG, "IMU data recording started.");
                }
            }
        });

        buttonRecordStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (dataRecording) {
                    dataCollector.stop();
                    dataRecording = false;
                    int n = dataCollector.getAccelSampleCount();
                    Log.d(TAG, "IMU data recording stopped. Accel samples: " + n);
                    promptSaveCsv(n);
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (recording)     imuCollector.resume();
        if (dataRecording) dataCollector.resume();
        liveClassifier.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (recording)     imuCollector.pause();
        if (dataRecording) dataCollector.pause();
        liveClassifier.stop();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SAVE_CSV && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                writeCsvToUri(uri);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Live classification callback
    // -------------------------------------------------------------------------

    private void onLiveResult(ExerciseClassifier.Result result) {
        if (result == null) {
            textExerciseType.setText("—");
            textExerciseType.setTextColor(0xFF9E9E9E);
            textConfidence.setText("");
            textLiveCalories.setText("0 kcal");
            return;
        }

        lastDetectedLabel = result.label;
        textExerciseType.setText(result.name);
        int color = labelColor(result.label);
        textExerciseType.setTextColor(color);
        textConfidence.setText(Math.round(result.confidence * 100) + "% confidence");

        // Body-weight calorie contribution: MET × kg × hours per classification window
        // STEP=150 samples at 50 Hz → 3 s per window
        float met        = result.label == ExerciseClassifier.LABEL_SP ? MET_SP : MET_BP;
        float windowHrs  = (150f / 50f) / 3600f;
        float romM       = getRomM(result.label);
        float loadKg     = readLoad();
        // Load-based contribution per window: estimate ~1 rep per window at current cadence
        float calLoad    = (loadKg * 9.81f * romM * 2f * 1f) / (0.25f * 4184f);
        sessionCalories += met * readWeight() * windowHrs + calLoad;
        textLiveCalories.setText(String.format("%.2f kcal", sessionCalories));
    }

    // -------------------------------------------------------------------------
    // Processing
    // -------------------------------------------------------------------------

    /**
     * Run the signal processing pipeline on a background thread so the UI
     * remains responsive, then post the result back to the main thread.
     */
    private void processAndDisplay() {
        processAndDisplay(false);
    }

    private void processAndDisplay(final boolean restartRecording) {
        final int mode = signalMode;

        // Snapshot all channel data on the main thread before handing off to the
        // background thread. This prevents the background thread from reading a
        // list that imuCollector.start() may clear when the next set begins.
        final List<Float> snapAx = new ArrayList<>(imuCollector.getAccelX());
        final List<Float> snapAy = new ArrayList<>(imuCollector.getAccelY());
        final List<Float> snapAz = new ArrayList<>(imuCollector.getAccelZ());
        final List<Float> snapGx = new ArrayList<>(imuCollector.getGyroX());
        final List<Float> snapGy = new ArrayList<>(imuCollector.getGyroY());
        final List<Float> snapGz = new ArrayList<>(imuCollector.getGyroZ());
        final int   snapSampleCount = snapAx.size();
        final float bodyKg          = readWeight();
        final float loadKg          = readLoad();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final int repCount;

                if (mode == MODE_GYRO) {
                    repCount = SignalProcessor.countRepsGyro(snapGx, snapGy, snapGz);
                } else {
                    repCount = SignalProcessor.countRepsAccel(snapAx, snapAy, snapAz);
                }

                Log.d(TAG, "Detected reps: " + repCount + "  (mode=" + mode + ")");

                final float peakAccel   = computePeakAccelFromSnapshot(snapAx, snapAy, snapAz);
                final float durationSec = (stopTimeMs - startTimeMs) / 1000f;
                final float avgRepSec   = SignalProcessor.avgPeakIntervalSec(snapAx, snapAy, snapAz);

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        textRepCount.setText(String.valueOf(repCount));
                        updateStats(repCount, durationSec, avgRepSec, peakAccel, bodyKg, loadKg);
                        addSetToHistory(repCount, durationSec, avgRepSec, peakAccel, bodyKg, loadKg);

                        if (restartRecording) {
                            // Immediately start recording the next set
                            imuCollector.start();
                            recording        = true;
                            startTimeMs      = System.currentTimeMillis();
                            sessionCalories  = 0f;
                            textRepCount.setText("--");
                            textStatDuration.setText("—");
                            textStatRepTime.setText("—");
                            textStatMaxAccel.setText("—");
                            textStatCalories.setText("—");
                            textStatus.setText("Set " + (setNumber + 1) + " — Recording…");
                        } else {
                            textStatus.setText("Done  |  samples: " + snapSampleCount);
                        }
                    }
                });
            }
        }).start();
    }

    // -------------------------------------------------------------------------
    // IMU data recording helpers
    // -------------------------------------------------------------------------

    private void promptSaveCsv(final int sampleCount) {
        new AlertDialog.Builder(this)
                .setTitle("Save IMU Data")
                .setMessage("Captured " + sampleCount + " samples.\nSave as CSV?")
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("text/csv");
                        intent.putExtra(Intent.EXTRA_TITLE,
                                "imu_session_" + System.currentTimeMillis() + ".csv");
                        startActivityForResult(intent, REQUEST_SAVE_CSV);
                    }
                })
                .setNegativeButton("Discard", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        textRecordStatus.setText("Idle");
                    }
                })
                .show();
    }

    private void writeCsvToUri(final Uri uri) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Long>  ts  = dataCollector.getAccelTimestamps();
                final List<Float> ax  = dataCollector.getAccelX();
                final List<Float> ay  = dataCollector.getAccelY();
                final List<Float> az  = dataCollector.getAccelZ();
                final List<Long>  gts = dataCollector.getGyroTimestamps();
                final List<Float> gx  = dataCollector.getGyroX();
                final List<Float> gy  = dataCollector.getGyroY();
                final List<Float> gz  = dataCollector.getGyroZ();

                final int rows = Math.min(ax.size(), gx.size());
                final long t0  = ts.isEmpty() ? 0L : ts.get(0);

                try (OutputStream os = getContentResolver().openOutputStream(uri);
                     OutputStreamWriter w = new OutputStreamWriter(os)) {

                    w.write("timestamp_ms,ax,ay,az,gx,gy,gz\n");
                    for (int i = 0; i < rows; i++) {
                        long tMs = (ts.get(i) - t0) / 1_000_000L;
                        w.write(tMs + ","
                                + ax.get(i) + "," + ay.get(i) + "," + az.get(i) + ","
                                + gx.get(i) + "," + gy.get(i) + "," + gz.get(i) + "\n");
                    }

                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            textRecordStatus.setText("Saved  |  " + rows + " rows");
                        }
                    });

                } catch (IOException e) {
                    Log.e(TAG, "CSV write failed: " + e.getMessage());
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            textRecordStatus.setText("Save failed");
                        }
                    });
                }
            }
        }).start();
    }

    // -------------------------------------------------------------------------
    // Stats helpers
    // -------------------------------------------------------------------------

    private void updateStats(int repCount, float durationSec, float avgRepSec,
                             float peakAccel, float bodyKg, float loadKg) {
        prefs.edit().putFloat(PREF_WEIGHT, bodyKg).putFloat(PREF_LOAD, loadKg).apply();

        float calories = calcCalories(repCount, durationSec, bodyKg, loadKg);

        textStatDuration.setText(formatDuration(durationSec));
        // Prefer peak-to-peak interval; fall back to total/reps if not enough peaks
        textStatRepTime.setText(avgRepSec > 0
                ? String.format("%.2fs", avgRepSec)
                : repCount > 0 ? String.format("%.2fs", durationSec / repCount) : "—");
        textStatMaxAccel.setText(String.format("%.1f m/s²", peakAccel));
        textStatCalories.setText(String.format("%.2f kcal", calories));
    }

    private float calcCalories(int repCount, float durationSec, float bodyKg, float loadKg) {
        float met     = labelMet(lastDetectedLabel);
        float romM    = getRomM(lastDetectedLabel);
        // Mechanical work on the load: W = load × g × ROM × 2 (up+down) × reps
        // Muscle mechanical efficiency ≈ 25%, so metabolic energy = W / 0.25
        // 1 kcal = 4184 J
        float calLoad = (loadKg * 9.81f * romM * 2f * repCount) / (0.25f * 4184f);
        float calBody = met * bodyKg * (durationSec / 3600f);
        return calLoad + calBody;
    }

    private float getRomM(int label) {
        if (label == ExerciseClassifier.LABEL_BP) return ROM_BP;
        if (label == ExerciseClassifier.LABEL_SP) return ROM_SP;
        if (label == ExerciseClassifier.LABEL_TP) return ROM_TP;
        return ROM_DEFAULT;
    }

    private float labelMet(int label) {
        if (label == ExerciseClassifier.LABEL_SP) return MET_SP;
        if (label == ExerciseClassifier.LABEL_TP) return MET_TP;
        return MET_BP;
    }

    private int labelColor(int label) {
        if (label == ExerciseClassifier.LABEL_BP) return 0xFF1565C0;  // blue
        if (label == ExerciseClassifier.LABEL_SP) return 0xFF2E7D32;  // green
        if (label == ExerciseClassifier.LABEL_TP) return 0xFFE65100;  // deep orange
        return 0xFF757575;
    }

    private float readWeight() {
        try {
            float w = Float.parseFloat(editWeight.getText().toString().trim());
            return w > 0 ? w : 70f;
        } catch (NumberFormatException e) {
            return 70f;
        }
    }

    private float readLoad() {
        try {
            float w = Float.parseFloat(editLoad.getText().toString().trim());
            return w >= 0 ? w : 0f;
        } catch (NumberFormatException e) {
            return 0f;
        }
    }

    private float computePeakAccelFromSnapshot(List<Float> ax, List<Float> ay, List<Float> az) {
        float peak = 0;
        for (int i = 0; i < ax.size(); i++) {
            float mag = (float) Math.sqrt(
                    ax.get(i) * ax.get(i) +
                    ay.get(i) * ay.get(i) +
                    az.get(i) * az.get(i));
            if (mag > peak) peak = mag;
        }
        return peak;
    }

    private void addSetToHistory(int repCount, float durationSec, float avgRepSec,
                                 float peakAccel, float bodyKg, float loadKg) {
        setNumber++;

        String exerciseName;
        int    labelColor;
        exerciseName = lastDetectedLabel >= 0 && lastDetectedLabel < ExerciseClassifier.N_CLASSES
                ? ExerciseClassifier.LABEL_NAMES[lastDetectedLabel] : "Unknown";
        labelColor   = labelColor(lastDetectedLabel);

        float calories = calcCalories(repCount, durationSec, bodyKg, loadKg);

        // Divider before every row except the first
        if (setNumber > 1) {
            View div = new View(this);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1);
            dp.setMargins(0, 6, 0, 6);
            div.setLayoutParams(dp);
            div.setBackgroundColor(0xFFE0E0E0);
            layoutSetHistory.addView(div);
        }

        // Row container
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);

        // Header: "Set 1  ·  Biceps Press"
        TextView header = new TextView(this);
        header.setText("Set " + setNumber + "  ·  " + exerciseName);
        header.setTextSize(14);
        header.setTextColor(labelColor);
        header.setTypeface(null, Typeface.BOLD);

        // Detail line — use peak-to-peak interval when available
        String repTime = avgRepSec > 0
                ? String.format("%.1fs/rep", avgRepSec)
                : (repCount > 0 ? String.format("%.1fs/rep", durationSec / repCount) : "—");
        String loadStr = loadKg > 0 ? String.format("  ·  %.0fkg load", loadKg) : "";
        TextView detail = new TextView(this);
        detail.setText(repCount + " reps  ·  "
                + formatDuration(durationSec) + "  ·  "
                + repTime + "  ·  "
                + String.format("%.1f m/s²", peakAccel) + "  ·  "
                + String.format("%.2f kcal", calories)
                + loadStr);
        detail.setTextSize(12);
        detail.setTextColor(0xFF757575);

        row.addView(header);
        row.addView(detail);
        layoutSetHistory.addView(row);
    }

    private static String formatDuration(float sec) {
        int m = (int) sec / 60;
        int s = (int) sec % 60;
        return m > 0 ? String.format("%dm %ds", m, s) : String.format("%ds", s);
    }

    // -------------------------------------------------------------------------
    // Mode toggle
    // -------------------------------------------------------------------------

    private void toggleMode() {
        if (signalMode == MODE_ACCEL) {
            signalMode = MODE_GYRO;
            textMode.setText("Signal: Gyro Jerk");
            buttonMode.setText("Switch to Accel Jerk");
        } else {
            signalMode = MODE_ACCEL;
            textMode.setText("Signal: Accel Jerk");
            buttonMode.setText("Switch to Gyro Jerk");
        }
    }
}
