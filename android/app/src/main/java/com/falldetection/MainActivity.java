package com.falldetection;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.util.Locale;

/**
 * Main Activity for Fall Detection App
 * Displays real-time fall detection status using smartphone accelerometer
 */
public class MainActivity extends AppCompatActivity implements SensorEventListener {
    private static final String TAG = "MainActivity";
    private static final int FALL_ALERT_VIBRATION_MS = 1000;

    // UI Components
    private TextView statusText;
    private TextView activityText;
    private TextView confidenceText;
    private TextView bufferStatusText;
    private TextView detectionCountText;
    private ProgressBar progressBar;
    private Button startButton;
    private Button stopButton;
    private Button resetButton;

    // Sensor and Fall Detector
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private FallDetector fallDetector;
    private Vibrator vibrator;

    // Detection state
    private boolean isMonitoring = false;
    private int totalPredictions = 0;
    private int fallDetections = 0;

    // Activity counters
    private int[] activityCounts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize UI components
        initializeUI();

        // Initialize sensor
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (accelerometer == null) {
            statusText.setText("Error: No accelerometer found!");
            startButton.setEnabled(false);
            return;
        }

        // Initialize vibrator
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // Initialize Fall Detector
        try {
            fallDetector = new FallDetector(this);
            activityCounts = new int[fallDetector.getLabels().length];
            statusText.setText("Ready to start monitoring");
            Log.d(TAG, "Fall detector initialized successfully");
        } catch (IOException e) {
            Log.e(TAG, "Error initializing fall detector", e);
            statusText.setText("Error: Failed to load model");
            startButton.setEnabled(false);
        }

        // Set up button listeners
        setupButtons();
    }

    private void initializeUI() {
        statusText = findViewById(R.id.statusText);
        activityText = findViewById(R.id.activityText);
        confidenceText = findViewById(R.id.confidenceText);
        bufferStatusText = findViewById(R.id.bufferStatusText);
        detectionCountText = findViewById(R.id.detectionCountText);
        progressBar = findViewById(R.id.progressBar);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        resetButton = findViewById(R.id.resetButton);

        stopButton.setEnabled(false);
    }

    private void setupButtons() {
        startButton.setOnClickListener(v -> startMonitoring());
        stopButton.setOnClickListener(v -> stopMonitoring());
        resetButton.setOnClickListener(v -> resetCounters());
    }

    private void startMonitoring() {
        if (fallDetector == null || accelerometer == null) {
            return;
        }

        // Register sensor listener
        // SENSOR_DELAY_FASTEST = ~200Hz which matches training data
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);

        isMonitoring = true;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        statusText.setText("Monitoring... Collecting data");

        Log.d(TAG, "Monitoring started");
    }

    private void stopMonitoring() {
        sensorManager.unregisterListener(this);
        isMonitoring = false;
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        statusText.setText("Monitoring stopped");

        Log.d(TAG, "Monitoring stopped");
    }

    private void resetCounters() {
        totalPredictions = 0;
        fallDetections = 0;
        activityCounts = new int[fallDetector.getLabels().length];
        fallDetector.clearBuffer();

        updateDetectionCounts();
        activityText.setText("---");
        confidenceText.setText("---%");
        bufferStatusText.setText("Buffer: 0/" + fallDetector.getWindowSize());
        progressBar.setProgress(0);

        Log.d(TAG, "Counters reset");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isMonitoring || fallDetector == null) {
            return;
        }

        // Get accelerometer data
        float accX = event.values[0];
        float accY = event.values[1];
        float accZ = event.values[2];

        // Add to fall detector buffer
        fallDetector.addSensorData(accX, accY, accZ);

        // Update buffer status
        int bufferSize = fallDetector.getBufferSize();
        int windowSize = fallDetector.getWindowSize();

        updateBufferStatus(bufferSize, windowSize);

        // Make prediction when buffer is full
        if (fallDetector.isReady()) {
            FallDetector.PredictionResult result = fallDetector.predict();

            // Update UI with prediction
            updatePredictionUI(result);

            // Update counters
            totalPredictions++;
            activityCounts[result.classIndex]++;

            if (result.isFall()) {
                fallDetections++;
                onFallDetected(result);
            }

            updateDetectionCounts();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    private void updateBufferStatus(int current, int total) {
        runOnUiThread(() -> {
            bufferStatusText.setText(String.format(Locale.US, "Buffer: %d/%d", current, total));
            int progress = (int) ((current / (float) total) * 100);
            progressBar.setProgress(progress);

            if (current < total) {
                statusText.setText("Collecting data...");
            } else {
                statusText.setText("Monitoring active");
            }
        });
    }

    private void updatePredictionUI(FallDetector.PredictionResult result) {
        runOnUiThread(() -> {
            activityText.setText(result.label.toUpperCase());
            confidenceText.setText(String.format(Locale.US, "%.1f%%", result.confidence * 100));

            // Color code based on activity
            if (result.isFall()) {
                activityText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            } else {
                activityText.setTextColor(getResources().getColor(android.R.color.black));
            }
        });
    }

    private void updateDetectionCounts() {
        runOnUiThread(() -> {
            StringBuilder counts = new StringBuilder();
            counts.append(String.format(Locale.US, "Total: %d  |  Falls: %d\n\n", totalPredictions, fallDetections));

            String[] labels = fallDetector.getLabels();
            for (int i = 0; i < labels.length; i++) {
                counts.append(String.format(Locale.US, "%s: %d\n",
                    labels[i].substring(0, 1).toUpperCase() + labels[i].substring(1),
                    activityCounts[i]));
            }

            detectionCountText.setText(counts.toString());
        });
    }

    private void onFallDetected(FallDetector.PredictionResult result) {
        Log.w(TAG, "FALL DETECTED! Confidence: " + (result.confidence * 100) + "%");

        runOnUiThread(() -> {
            statusText.setText("⚠️ FALL DETECTED! ⚠️");
            statusText.setTextColor(getResources().getColor(android.R.color.holo_red_dark));

            // Vibrate to alert
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(FALL_ALERT_VIBRATION_MS);
            }

            // Reset status color after 3 seconds
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (isMonitoring) {
                    statusText.setText("Monitoring active");
                    statusText.setTextColor(getResources().getColor(android.R.color.black));
                }
            }, 3000);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isMonitoring) {
            stopMonitoring();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (fallDetector != null) {
            fallDetector.close();
        }
    }
}
