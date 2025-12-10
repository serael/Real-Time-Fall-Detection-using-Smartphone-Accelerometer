package com.falldetection;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 200Hz 고정 샘플링 레이트를 위한 센서 데이터 수집기
 * 타임스탬프 기반으로 정확히 5ms 간격으로 샘플링
 */
public class FixedRateSensorCollector implements SensorEventListener {
    private static final String TAG = "FixedRateSensor";

    // 목표 샘플링 레이트
    private static final int TARGET_SAMPLE_RATE_HZ = 200;
    private static final long SAMPLE_INTERVAL_NS = 1_000_000_000L / TARGET_SAMPLE_RATE_HZ;  // 5ms in nanoseconds
    private static final long SAMPLE_INTERVAL_MS = 1000L / TARGET_SAMPLE_RATE_HZ;  // 5ms

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private long lastSampleTimestamp = 0;
    private int rawSampleCount = 0;
    private int acceptedSampleCount = 0;

    // 리샘플링을 위한 임시 버퍼
    private float[] lastAcceptedSample = null;
    private float[] currentRawSample = new float[3];

    // 콜백 인터페이스
    public interface SampleCallback {
        void onSampleCollected(float accX, float accY, float accZ, long timestamp);
    }

    private SampleCallback callback;

    public FixedRateSensorCollector(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    /**
     * 센서 리스너 시작
     */
    public void start(SampleCallback callback) {
        this.callback = callback;
        this.lastSampleTimestamp = 0;
        this.rawSampleCount = 0;
        this.acceptedSampleCount = 0;
        this.lastAcceptedSample = null;

        // SENSOR_DELAY_FASTEST로 최대한 빠르게 수집
        // 그 중에서 5ms 간격으로만 선택
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
    }

    /**
     * 센서 리스너 중지
     */
    public void stop() {
        sensorManager.unregisterListener(this);
        printStatistics();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }

        long currentTimestamp = event.timestamp;  // nanoseconds
        rawSampleCount++;

        // 현재 샘플 저장
        currentRawSample[0] = event.values[0];
        currentRawSample[1] = event.values[1];
        currentRawSample[2] = event.values[2];

        // 첫 샘플이거나 5ms 이상 경과한 경우
        if (lastSampleTimestamp == 0 ||
            (currentTimestamp - lastSampleTimestamp) >= SAMPLE_INTERVAL_NS) {

            // 샘플 수집
            if (callback != null) {
                callback.onSampleCollected(
                    currentRawSample[0],
                    currentRawSample[1],
                    currentRawSample[2],
                    currentTimestamp
                );
            }

            lastSampleTimestamp = currentTimestamp;
            acceptedSampleCount++;

            // 마지막 수집 샘플 저장 (보간용)
            if (lastAcceptedSample == null) {
                lastAcceptedSample = new float[3];
            }
            lastAcceptedSample[0] = currentRawSample[0];
            lastAcceptedSample[1] = currentRawSample[1];
            lastAcceptedSample[2] = currentRawSample[2];
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    /**
     * 실제 샘플링 레이트 계산
     */
    public int getActualSampleRate() {
        if (acceptedSampleCount < 2 || lastSampleTimestamp == 0) {
            return 0;
        }

        // 평균 샘플링 레이트 계산 (매우 근사치)
        long totalTimeNs = lastSampleTimestamp;
        return (int) (acceptedSampleCount * 1_000_000_000L / totalTimeNs);
    }

    /**
     * 통계 출력
     */
    private void printStatistics() {
        if (rawSampleCount > 0) {
            float acceptanceRate = (acceptedSampleCount * 100.0f) / rawSampleCount;
            android.util.Log.d(TAG, "=== Sampling Statistics ===");
            android.util.Log.d(TAG, "Raw samples: " + rawSampleCount);
            android.util.Log.d(TAG, "Accepted samples: " + acceptedSampleCount);
            android.util.Log.d(TAG, "Acceptance rate: " + String.format("%.1f%%", acceptanceRate));
            android.util.Log.d(TAG, "Target rate: " + TARGET_SAMPLE_RATE_HZ + " Hz");
        }
    }
}
