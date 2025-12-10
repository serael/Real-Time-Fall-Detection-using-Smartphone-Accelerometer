package com.falldetection;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

/**
 * 센서 샘플링 레이트 테스트 액티비티
 * 세 가지 방법을 비교하여 실제 샘플링 레이트 측정
 */
public class SensorTestActivity extends AppCompatActivity {
    private TextView rawRateText;
    private TextView fixedRateText;
    private TextView interpolatedRateText;
    private TextView comparisonText;

    private SensorManager sensorManager;
    private Sensor accelerometer;

    // 방법 1: SENSOR_DELAY_FASTEST (기존)
    private int rawSampleCount = 0;
    private long rawStartTime = 0;

    // 방법 2: 타임스탬프 방식
    private FixedRateSensorCollector fixedCollector;
    private int fixedSampleCount = 0;
    private long fixedStartTime = 0;

    // 방법 3: 선형 보간
    private InterpolatedSensorCollector interpolatedCollector;
    private int interpolatedSampleCount = 0;
    private long interpolatedStartTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 간단한 레이아웃 (프로그래밍 방식)
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(40, 40, 40, 40);

        TextView title = new TextView(this);
        title.setText("센서 샘플링 레이트 비교");
        title.setTextSize(24);
        title.setPadding(0, 0, 0, 40);
        layout.addView(title);

        rawRateText = createTextView("방법 1 (FASTEST): 측정 중...");
        layout.addView(rawRateText);

        fixedRateText = createTextView("방법 2 (타임스탬프): 측정 중...");
        layout.addView(fixedRateText);

        interpolatedRateText = createTextView("방법 3 (선형 보간): 측정 중...");
        layout.addView(interpolatedRateText);

        comparisonText = createTextView("");
        comparisonText.setPadding(0, 40, 0, 0);
        layout.addView(comparisonText);

        setContentView(layout);

        // 센서 초기화
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        startTests();
    }

    private TextView createTextView(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setPadding(0, 10, 0, 10);
        return tv;
    }

    private void startTests() {
        // 방법 1: SENSOR_DELAY_FASTEST
        sensorManager.registerListener(rawListener, accelerometer,
            SensorManager.SENSOR_DELAY_FASTEST);

        // 방법 2: 타임스탬프 방식
        fixedCollector = new FixedRateSensorCollector(this);
        fixedCollector.start(new FixedRateSensorCollector.SampleCallback() {
            @Override
            public void onSampleCollected(float accX, float accY, float accZ, long timestamp) {
                updateFixedRate();
            }
        });

        // 방법 3: 선형 보간 방식
        interpolatedCollector = new InterpolatedSensorCollector(this);
        interpolatedCollector.start(new FixedRateSensorCollector.SampleCallback() {
            @Override
            public void onSampleCollected(float accX, float accY, float accZ, long timestamp) {
                updateInterpolatedRate();
            }
        });
    }

    private final SensorEventListener rawListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            updateRawRate();
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

    private void updateRawRate() {
        rawSampleCount++;

        if (rawStartTime == 0) {
            rawStartTime = System.currentTimeMillis();
        }

        long elapsed = System.currentTimeMillis() - rawStartTime;
        if (elapsed >= 1000) {
            final float rate = rawSampleCount / (elapsed / 1000.0f);
            final float diff = Math.abs(rate - 200.0f);

            runOnUiThread(() -> {
                rawRateText.setText(String.format(Locale.US,
                    "방법 1 (FASTEST): %.1f Hz (차이: %.1f Hz)", rate, diff));
                rawRateText.setTextColor(getColorForDifference(diff));
                updateComparison();
            });

            rawSampleCount = 0;
            rawStartTime = System.currentTimeMillis();
        }
    }

    private void updateFixedRate() {
        fixedSampleCount++;

        if (fixedStartTime == 0) {
            fixedStartTime = System.currentTimeMillis();
        }

        long elapsed = System.currentTimeMillis() - fixedStartTime;
        if (elapsed >= 1000) {
            final float rate = fixedSampleCount / (elapsed / 1000.0f);
            final float diff = Math.abs(rate - 200.0f);

            runOnUiThread(() -> {
                fixedRateText.setText(String.format(Locale.US,
                    "방법 2 (타임스탬프): %.1f Hz (차이: %.1f Hz)", rate, diff));
                fixedRateText.setTextColor(getColorForDifference(diff));
                updateComparison();
            });

            fixedSampleCount = 0;
            fixedStartTime = System.currentTimeMillis();
        }
    }

    private void updateInterpolatedRate() {
        interpolatedSampleCount++;

        if (interpolatedStartTime == 0) {
            interpolatedStartTime = System.currentTimeMillis();
        }

        long elapsed = System.currentTimeMillis() - interpolatedStartTime;
        if (elapsed >= 1000) {
            final float rate = interpolatedSampleCount / (elapsed / 1000.0f);
            final float diff = Math.abs(rate - 200.0f);

            runOnUiThread(() -> {
                interpolatedRateText.setText(String.format(Locale.US,
                    "방법 3 (선형 보간): %.1f Hz (차이: %.1f Hz)", rate, diff));
                interpolatedRateText.setTextColor(getColorForDifference(diff));
                updateComparison();
            });

            interpolatedSampleCount = 0;
            interpolatedStartTime = System.currentTimeMillis();
        }
    }

    private int getColorForDifference(float diff) {
        if (diff < 2.0f) {
            return 0xFF00AA00;  // 녹색 - 매우 좋음
        } else if (diff < 5.0f) {
            return 0xFFFFAA00;  // 주황색 - 보통
        } else {
            return 0xFFFF0000;  // 빨강 - 나쁨
        }
    }

    private void updateComparison() {
        runOnUiThread(() -> {
            comparisonText.setText(
                "목표: 200 Hz\n\n" +
                "✅ 녹색: 매우 정확 (오차 < 2 Hz)\n" +
                "⚠️ 주황: 보통 (오차 < 5 Hz)\n" +
                "❌ 빨강: 부정확 (오차 >= 5 Hz)\n\n" +
                "권장: 녹색 방법 사용"
            );
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(rawListener);
        if (fixedCollector != null) {
            fixedCollector.stop();
        }
        if (interpolatedCollector != null) {
            interpolatedCollector.stop();
        }
    }
}
