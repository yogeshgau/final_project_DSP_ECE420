package com.ece420.lab1;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

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

    private static final String TAG = "RepCounterActivity";

    private static final int MODE_ACCEL = 0;
    private static final int MODE_GYRO  = 1;

    private ImuCollector imuCollector;
    private boolean recording = false;
    private int signalMode = MODE_ACCEL;

    private Button  buttonStart;
    private Button  buttonStop;
    private Button  buttonMode;
    private TextView textRepCount;
    private TextView textStatus;
    private TextView textMode;

    // -------------------------------------------------------------------------
    // Activity lifecycle
    // -------------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_rep_counter);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        imuCollector = new ImuCollector(this);

        textRepCount = (TextView) findViewById(R.id.textRepCount);
        textStatus   = (TextView) findViewById(R.id.textStatus);
        textMode     = (TextView) findViewById(R.id.textMode);
        buttonStart  = (Button)   findViewById(R.id.buttonStart);
        buttonStop   = (Button)   findViewById(R.id.buttonStop);
        buttonMode   = (Button)   findViewById(R.id.buttonMode);

        buttonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!recording) {
                    imuCollector.start();
                    recording = true;
                    textRepCount.setText("--");
                    textStatus.setText("Recording…");
                    Log.d(TAG, "Recording started.");
                }
            }
        });

        buttonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (recording) {
                    imuCollector.stop();
                    recording = false;
                    int samples = imuCollector.getAccelSampleCount();
                    textStatus.setText("Processing " + samples + " samples…");
                    Log.d(TAG, "Recording stopped. Accel samples: " + samples
                            + "  Gyro samples: " + imuCollector.getGyroSampleCount());
                    processAndDisplay();
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (recording) {
            imuCollector.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (recording) {
            imuCollector.pause();
        }
    }

    // -------------------------------------------------------------------------
    // Processing
    // -------------------------------------------------------------------------

    /**
     * Run the signal processing pipeline on a background thread so the UI
     * remains responsive, then post the result back to the main thread.
     */
    private void processAndDisplay() {
        final int mode = signalMode;

        new Thread(new Runnable() {
            @Override
            public void run() {
                final int repCount;

                if (mode == MODE_GYRO) {
                    repCount = SignalProcessor.countRepsGyro(
                            imuCollector.getGyroX(),
                            imuCollector.getGyroY(),
                            imuCollector.getGyroZ());
                } else {
                    repCount = SignalProcessor.countRepsAccel(
                            imuCollector.getAccelX(),
                            imuCollector.getAccelY(),
                            imuCollector.getAccelZ());
                }

                Log.d(TAG, "Detected reps: " + repCount + "  (mode=" + mode + ")");

                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        textRepCount.setText(String.valueOf(repCount));
                        textStatus.setText("Done  |  samples: "
                                + imuCollector.getAccelSampleCount());
                    }
                });
            }
        }).start();
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
