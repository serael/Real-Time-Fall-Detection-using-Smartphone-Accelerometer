package com.falldetection;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 디버깅용 로거 - 센서 데이터와 예측 결과를 파일로 저장
 */
public class DebugLogger {
    private static final String TAG = "DebugLogger";
    private FileWriter writer;
    private boolean isEnabled;

    public DebugLogger(Context context, boolean enabled) {
        this.isEnabled = enabled;
        if (enabled) {
            initializeLogFile(context);
        }
    }

    private void initializeLogFile(Context context) {
        try {
            File logDir = new File(context.getExternalFilesDir(null), "fall_detection_logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(new Date());
            File logFile = new File(logDir, "predictions_" + timestamp + ".csv");

            writer = new FileWriter(logFile, true);

            // CSV 헤더
            writer.write("timestamp,accX,accY,accZ,predicted_class,predicted_label,confidence," +
                    "prob_walking,prob_running,prob_standing,prob_pocket,prob_falls\n");
            writer.flush();

            Log.d(TAG, "Log file created: " + logFile.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to create log file", e);
            isEnabled = false;
        }
    }

    public void logPrediction(float accX, float accY, float accZ,
                              FallDetector.PredictionResult result) {
        if (!isEnabled || writer == null) {
            return;
        }

        try {
            long timestamp = System.currentTimeMillis();

            String line = String.format(Locale.US,
                    "%d,%.6f,%.6f,%.6f,%d,%s,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f\n",
                    timestamp,
                    accX, accY, accZ,
                    result.classIndex,
                    result.label,
                    result.confidence,
                    result.probabilities[0],  // walking
                    result.probabilities[1],  // running
                    result.probabilities[2],  // standing
                    result.probabilities[3],  // pocket
                    result.probabilities[4]   // falls
            );

            writer.write(line);
            writer.flush();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write log", e);
        }
    }

    public void logFallDetection(FallDetector.PredictionResult result) {
        if (!isEnabled) {
            return;
        }

        Log.w(TAG, "FALL DETECTED!");
        Log.w(TAG, "  Confidence: " + result.confidence);
        Log.w(TAG, "  Probabilities: ");
        String[] labels = {"walking", "running", "standing", "pocket", "falls"};
        for (int i = 0; i < result.probabilities.length; i++) {
            Log.w(TAG, "    " + labels[i] + ": " + result.probabilities[i]);
        }
    }

    public void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close log file", e);
            }
        }
    }
}
