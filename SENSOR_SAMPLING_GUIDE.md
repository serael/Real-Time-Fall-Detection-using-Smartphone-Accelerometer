# 200Hz 정확한 센서 샘플링 가이드

## 문제: SENSOR_DELAY_FASTEST의 불규칙한 샘플링

```java
// 기존 방식 - 문제가 있음
sensorManager.registerListener(this, accelerometer,
    SensorManager.SENSOR_DELAY_FASTEST);
// → 100~300Hz로 가변적
// → 기기마다 다름
// → 훈련 데이터(200Hz)와 불일치
```

---

## 해결책 1: 타임스탬프 기반 샘플링 (간단)

**원리:** 센서 이벤트를 빠르게 받되, 5ms 간격으로만 선택

### 장점
✅ 구현이 간단
✅ 실제 센서 데이터만 사용 (보간 없음)
✅ CPU 사용량 낮음

### 단점
❌ 정확히 5ms 간격 보장 안 됨 (±1ms 오차 가능)
❌ 센서 이벤트가 느리면 샘플 누락 가능

### 사용법

**MainActivity.java 수정:**

```java
public class MainActivity extends AppCompatActivity {
    private FixedRateSensorCollector sensorCollector;
    private FallDetector fallDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ...

        // 기존 sensorManager 코드 제거
        // sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        // accelerometer = ...

        // 새로운 센서 수집기 사용
        sensorCollector = new FixedRateSensorCollector(this);

        try {
            fallDetector = new FallDetector(this);
        } catch (IOException e) {
            // ...
        }
    }

    private void startMonitoring() {
        sensorCollector.start(new FixedRateSensorCollector.SampleCallback() {
            @Override
            public void onSampleCollected(float accX, float accY, float accZ, long timestamp) {
                // FallDetector에 데이터 전달 (기존 로직과 동일)
                fallDetector.addSensorData(accX, accY, accZ);

                int bufferSize = fallDetector.getBufferSize();
                int windowSize = fallDetector.getWindowSize();
                updateBufferStatus(bufferSize, windowSize);

                if (fallDetector.isReady()) {
                    FallDetector.PredictionResult result = fallDetector.predict();
                    updatePredictionUI(result);

                    totalPredictions++;
                    activityCounts[result.classIndex]++;

                    if (result.isFall()) {
                        fallDetections++;
                        onFallDetected(result);
                    }

                    updateDetectionCounts();
                }
            }
        });

        isMonitoring = true;
        // ...
    }

    private void stopMonitoring() {
        sensorCollector.stop();
        isMonitoring = false;
        // ...
    }
}
```

---

## 해결책 2: 선형 보간 방식 (정확)

**원리:** 실제 센서 데이터 사이를 보간하여 정확히 5ms 간격 샘플 생성

### 장점
✅ 정확히 5ms 간격 보장
✅ 샘플 누락 없음
✅ 훈련 데이터와 완벽히 일치

### 단점
❌ 보간으로 인한 약간의 데이터 왜곡
❌ CPU 사용량 약간 높음

### 사용법

**MainActivity.java 수정:**

```java
public class MainActivity extends AppCompatActivity {
    private InterpolatedSensorCollector sensorCollector;
    private FallDetector fallDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ...

        sensorCollector = new InterpolatedSensorCollector(this);

        try {
            fallDetector = new FallDetector(this);
        } catch (IOException e) {
            // ...
        }
    }

    private void startMonitoring() {
        sensorCollector.start(new FixedRateSensorCollector.SampleCallback() {
            @Override
            public void onSampleCollected(float accX, float accY, float accZ, long timestamp) {
                // 동일한 콜백 인터페이스 사용
                fallDetector.addSensorData(accX, accY, accZ);

                // ... 나머지 코드 동일 ...
            }
        });

        isMonitoring = true;
        // ...
    }

    private void stopMonitoring() {
        sensorCollector.stop();
        isMonitoring = false;
        // ...
    }
}
```

---

## 비교표

| 특성 | SENSOR_DELAY_FASTEST | 타임스탬프 방식 | 선형 보간 방식 |
|------|---------------------|----------------|---------------|
| 샘플링 레이트 | 가변 (100~300Hz) | ~200Hz (±1ms) | 정확히 200Hz |
| 정확도 | ❌ 낮음 | ⚠️ 보통 | ✅ 높음 |
| CPU 사용량 | 낮음 | 낮음 | 보통 |
| 구현 복잡도 | 매우 간단 | 간단 | 보통 |
| 훈련 데이터 일치 | ❌ | ⚠️ | ✅ |
| **권장 여부** | ❌ | ✅ (시작용) | ✅✅ (최종) |

---

## 권장 단계별 적용

### 1단계: 타임스탬프 방식으로 시작
```java
sensorCollector = new FixedRateSensorCollector(this);
```
- 간단하게 적용
- 효과 확인

### 2단계: 필요시 선형 보간 방식 적용
```java
sensorCollector = new InterpolatedSensorCollector(this);
```
- 더 정확한 200Hz 필요 시
- 오탐이 여전히 발생할 때

---

## 테스트 방법

### 실제 샘플링 레이트 확인

**로그 추가:**
```java
private int sampleCount = 0;
private long startTime = 0;

@Override
public void onSampleCollected(float accX, float accY, float accZ, long timestamp) {
    sampleCount++;

    if (startTime == 0) {
        startTime = System.currentTimeMillis();
    }

    // 1초마다 샘플링 레이트 출력
    long elapsed = System.currentTimeMillis() - startTime;
    if (elapsed >= 1000) {
        float actualRate = sampleCount / (elapsed / 1000.0f);
        Log.d(TAG, "Actual sampling rate: " + String.format("%.1f Hz", actualRate));

        // 리셋
        sampleCount = 0;
        startTime = System.currentTimeMillis();
    }

    // ... 기존 코드
}
```

**예상 출력:**
```
Actual sampling rate: 199.5 Hz  ✅ (타임스탬프 방식)
Actual sampling rate: 200.0 Hz  ✅ (선형 보간 방식)
Actual sampling rate: 245.3 Hz  ❌ (SENSOR_DELAY_FASTEST)
```

---

## 예상 효과

### 오탐 감소
- 샘플링 레이트 일치로 패턴 인식 정확도 향상
- 훈련 데이터와 동일한 조건에서 추론

### 정확도 향상
- 윈도우 크기(400 샘플 = 정확히 2초)
- 슬라이딩 윈도우 간격 일정

---

## 주의사항

### 1. 센서 지연 시간
- 일부 구형 기기는 센서 이벤트가 느릴 수 있음
- 이 경우 보간 방식 사용 권장

### 2. 배터리 영향
- SENSOR_DELAY_FASTEST는 배터리를 많이 소모
- 하지만 데이터 정확도를 위해 필요

### 3. 성능 테스트
- 실제 기기에서 반드시 테스트
- CPU 사용률 모니터링

---

## 문제 해결

### Q: 샘플링 레이트가 여전히 불안정
**A:** InterpolatedSensorCollector 사용

### Q: CPU 사용률이 너무 높음
**A:**
1. FixedRateSensorCollector 사용
2. SENSOR_DELAY_GAME (50Hz)으로 낮추고 업샘플링

### Q: 여전히 오탐 발생
**A:**
1. 샘플링 레이트 로그 확인
2. ANDROID_FIX.md의 신뢰도 임계값 적용
3. 디바운싱 추가

---

## 완전한 예제

**MainActivity.java (완전한 코드):**

```java
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    // 선형 보간 방식 사용 (더 정확)
    private InterpolatedSensorCollector sensorCollector;
    private FallDetector fallDetector;

    // 샘플링 레이트 모니터링
    private int sampleCount = 0;
    private long startTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeUI();

        // 센서 수집기 초기화
        sensorCollector = new InterpolatedSensorCollector(this);

        // Fall Detector 초기화
        try {
            fallDetector = new FallDetector(this);
            statusText.setText("Ready to start monitoring");
        } catch (IOException e) {
            Log.e(TAG, "Error initializing fall detector", e);
            statusText.setText("Error: Failed to load model");
            startButton.setEnabled(false);
        }

        setupButtons();
    }

    private void startMonitoring() {
        if (fallDetector == null) {
            return;
        }

        sampleCount = 0;
        startTime = 0;

        sensorCollector.start(new FixedRateSensorCollector.SampleCallback() {
            @Override
            public void onSampleCollected(float accX, float accY, float accZ, long timestamp) {
                // 샘플링 레이트 모니터링
                monitorSamplingRate();

                // FallDetector에 데이터 추가
                fallDetector.addSensorData(accX, accY, accZ);

                int bufferSize = fallDetector.getBufferSize();
                int windowSize = fallDetector.getWindowSize();
                updateBufferStatus(bufferSize, windowSize);

                // 버퍼가 가득 차면 예측
                if (fallDetector.isReady()) {
                    FallDetector.PredictionResult result = fallDetector.predict();
                    updatePredictionUI(result);

                    totalPredictions++;
                    activityCounts[result.classIndex]++;

                    if (result.isFall() && result.confidence >= 0.85f) {  // 신뢰도 임계값
                        fallDetections++;
                        onFallDetected(result);
                    }

                    updateDetectionCounts();
                }
            }
        });

        isMonitoring = true;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        statusText.setText("Monitoring... Collecting data");

        Log.d(TAG, "Monitoring started with 200Hz sampling");
    }

    private void stopMonitoring() {
        sensorCollector.stop();
        isMonitoring = false;
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        statusText.setText("Monitoring stopped");

        Log.d(TAG, "Monitoring stopped");
    }

    private void monitorSamplingRate() {
        sampleCount++;

        if (startTime == 0) {
            startTime = System.currentTimeMillis();
        }

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= 5000) {  // 5초마다
            float actualRate = sampleCount / (elapsed / 1000.0f);
            Log.i(TAG, "Actual sampling rate: " + String.format("%.1f Hz", actualRate));

            sampleCount = 0;
            startTime = System.currentTimeMillis();
        }
    }

    // ... 나머지 메서드들 ...
}
```

---

## 결론

**권장 방법:**
1. 🥇 **InterpolatedSensorCollector** - 가장 정확
2. 🥈 **FixedRateSensorCollector** - 간단하고 효과적
3. 🥉 **SENSOR_DELAY_FASTEST** - 사용하지 말 것

**적용 후 예상 결과:**
- ✅ 샘플링 레이트: 200Hz ± 0.5Hz
- ✅ 훈련 데이터와 일치
- ✅ 오탐 감소
- ✅ 정확도 향상
