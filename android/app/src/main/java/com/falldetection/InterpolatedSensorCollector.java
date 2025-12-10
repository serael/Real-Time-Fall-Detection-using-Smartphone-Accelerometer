package com.falldetection;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * 선형 보간을 사용한 200Hz 정확한 샘플링
 * 실제 센서 데이터 사이를 보간하여 정확히 5ms 간격 샘플 생성
 */
public class InterpolatedSensorCollector implements SensorEventListener {
    private static final String TAG = "InterpolatedSensor";

    // 목표 샘플링 레이트
    private static final int TARGET_SAMPLE_RATE_HZ = 200;
    private static final long SAMPLE_INTERVAL_NS = 1_000_000_000L / TARGET_SAMPLE_RATE_HZ;  // 5ms

    private SensorManager sensorManager;
    private Sensor accelerometer;

    // 이전/현재 센서 데이터
    private float[] prevSample = null;
    private long prevTimestamp = 0;
    private float[] currentSample = new float[3];
    private long currentTimestamp = 0;

    // 다음 생성할 샘플의 타임스탬프
    private long nextTargetTimestamp = 0;

    private FixedRateSensorCollector.SampleCallback callback;
    private int generatedSampleCount = 0;
    private boolean isStarted = false;

    public InterpolatedSensorCollector(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    /**
     * 센서 리스너 시작
     */
    public void start(FixedRateSensorCollector.SampleCallback callback) {
        this.callback = callback;
        this.prevSample = null;
        this.prevTimestamp = 0;
        this.nextTargetTimestamp = 0;
        this.generatedSampleCount = 0;
        this.isStarted = true;

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        Log.d(TAG, "Interpolated sensor collector started (target: " + TARGET_SAMPLE_RATE_HZ + " Hz)");
    }

    /**
     * 센서 리스너 중지
     */
    public void stop() {
        sensorManager.unregisterListener(this);
        isStarted = false;
        Log.d(TAG, "Generated " + generatedSampleCount + " interpolated samples");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!isStarted || event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }

        // 현재 샘플 업데이트
        currentSample[0] = event.values[0];
        currentSample[1] = event.values[1];
        currentSample[2] = event.values[2];
        currentTimestamp = event.timestamp;

        // 첫 번째 샘플 - 초기화만
        if (prevSample == null) {
            prevSample = new float[3];
            System.arraycopy(currentSample, 0, prevSample, 0, 3);
            prevTimestamp = currentTimestamp;
            nextTargetTimestamp = currentTimestamp + SAMPLE_INTERVAL_NS;
            return;
        }

        // 이전 샘플과 현재 샘플 사이의 모든 목표 타임스탬프에 대해 보간
        while (nextTargetTimestamp <= currentTimestamp) {
            // 선형 보간
            float[] interpolated = interpolate(
                prevSample, prevTimestamp,
                currentSample, currentTimestamp,
                nextTargetTimestamp
            );

            // 콜백 호출
            if (callback != null) {
                callback.onSampleCollected(
                    interpolated[0],
                    interpolated[1],
                    interpolated[2],
                    nextTargetTimestamp
                );
            }

            generatedSampleCount++;
            nextTargetTimestamp += SAMPLE_INTERVAL_NS;
        }

        // 현재 샘플을 이전 샘플로 저장
        System.arraycopy(currentSample, 0, prevSample, 0, 3);
        prevTimestamp = currentTimestamp;
    }

    /**
     * 선형 보간
     * @param sample1 이전 샘플
     * @param time1 이전 타임스탬프
     * @param sample2 현재 샘플
     * @param time2 현재 타임스탬프
     * @param targetTime 목표 타임스탬프
     * @return 보간된 샘플
     */
    private float[] interpolate(float[] sample1, long time1,
                                float[] sample2, long time2,
                                long targetTime) {
        float[] result = new float[3];

        // 보간 비율 계산
        float ratio = (float) (targetTime - time1) / (time2 - time1);

        // 각 축에 대해 선형 보간
        for (int i = 0; i < 3; i++) {
            result[i] = sample1[i] + (sample2[i] - sample1[i]) * ratio;
        }

        return result;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Not used
    }

    /**
     * 생성된 샘플 수 반환
     */
    public int getGeneratedSampleCount() {
        return generatedSampleCount;
    }
}
