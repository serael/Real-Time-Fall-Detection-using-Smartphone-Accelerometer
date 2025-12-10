# 안드로이드 오탐 해결 가이드

## 테스트 결과
✅ **H5 모델과 TFLite 모델은 100% 동일합니다**
- 366개 테스트 샘플에서 모두 동일한 예측
- 확률 차이: 최대 0.0000017 (무시 가능)
- 테스트 데이터에서 오탐: 0건

## 안드로이드 오탐 원인
모델은 동일하므로, 실시간 데이터의 특성 차이가 원인입니다:
1. 센서 샘플링 레이트 불일치 (SENSOR_DELAY_FASTEST ≠ 정확히 200Hz)
2. 훈련되지 않은 움직임 패턴
3. 센서 노이즈

---

## 즉시 적용 가능한 해결책

### 방법 1: 신뢰도 임계값 추가 (권장)

**FallDetector.java 수정:**

```java
// 클래스 상단에 추가
private static final float FALL_CONFIDENCE_THRESHOLD = 0.85f;  // 85% 이상

// PredictionResult 클래스에 메서드 추가
public boolean isFall() {
    return label.equalsIgnoreCase("falls") && confidence >= FALL_CONFIDENCE_THRESHOLD;
}

public boolean isFallHighConfidence() {
    return label.equalsIgnoreCase("falls") && confidence >= 0.90f;  // 90% 이상
}
```

**MainActivity.java 수정:**

```java
if (result.isFall()) {
    // 기존: confidence 체크 없음
    // 수정: 이미 isFall()에서 임계값 체크됨
    fallDetections++;
    onFallDetected(result);
}
```

### 방법 2: 디바운싱 (연속 감지)

**FallDetector.java에 추가:**

```java
// 클래스 변수
private int consecutiveFallCount = 0;
private static final int REQUIRED_FALL_DETECTIONS = 2;  // 연속 2번 감지

// predict() 메서드에 추가
public PredictionResult predict() {
    // ... 기존 코드 ...

    String predictedLabel = labels[predictedClass];

    // 디바운싱 로직
    if (predictedLabel.equalsIgnoreCase("falls") && maxProb >= 0.80f) {
        consecutiveFallCount++;
    } else {
        consecutiveFallCount = 0;  // 리셋
    }

    // ... 기존 코드 ...
}

// PredictionResult에 추가
public boolean isFallConfirmed() {
    return label.equalsIgnoreCase("falls") &&
           confidence >= 0.80f &&
           consecutiveFallCount >= REQUIRED_FALL_DETECTIONS;
}
```

### 방법 3: 움직임 크기 필터

Falls는 큰 가속도 변화를 동반합니다:

```java
// FallDetector.java에 추가
private float calculateAccelerationMagnitude(List<float[]> data) {
    float maxMagnitude = 0;
    for (float[] sample : data) {
        float magnitude = (float) Math.sqrt(
            sample[0] * sample[0] +
            sample[1] * sample[1] +
            sample[2] * sample[2]
        );
        maxMagnitude = Math.max(maxMagnitude, magnitude);
    }
    return maxMagnitude;
}

// predict()에서 사용
public PredictionResult predict() {
    // ... 기존 코드 ...

    // Falls 예측 시 움직임 크기 확인
    if (predictedLabel.equalsIgnoreCase("falls")) {
        float magnitude = calculateAccelerationMagnitude(dataList);
        if (magnitude < 15.0f) {  // 낙상은 보통 15 m/s² 이상
            // 낙상이 아닌 것으로 판단
            predictedClass = /* 두 번째로 높은 확률의 클래스 */;
            predictedLabel = labels[predictedClass];
            maxProb = /* 두 번째 확률 */;
        }
    }

    // ... 기존 코드 ...
}
```

---

## 디버깅 방법

### 1. 로깅 추가

**DebugLogger.java 사용:**
```java
// MainActivity.java
private DebugLogger debugLogger;

@Override
protected void onCreate(Bundle savedInstanceState) {
    // ...
    debugLogger = new DebugLogger(this, true);
}

// onSensorChanged에서
if (fallDetector.isReady()) {
    FallDetector.PredictionResult result = fallDetector.predict();
    debugLogger.logPrediction(accX, accY, accZ, result);

    if (result.isFall()) {
        debugLogger.logFallDetection(result);
    }
}
```

### 2. 로그 분석

앱 실행 후 로그 파일 추출:
```bash
# 로그 파일 위치
/Android/data/com.falldetection/files/fall_detection_logs/predictions_*.csv

# PC로 복사 후 분석
python analyze_android_logs.py predictions_20241210_123456.csv
```

---

## 권장 설정

### 최소 오탐 설정:
```java
FALL_CONFIDENCE_THRESHOLD = 0.90f;  // 90% 이상
REQUIRED_FALL_DETECTIONS = 3;       // 연속 3번
MIN_ACCELERATION_MAGNITUDE = 18.0f; // 18 m/s² 이상
```

### 균형 설정 (권장):
```java
FALL_CONFIDENCE_THRESHOLD = 0.85f;  // 85% 이상
REQUIRED_FALL_DETECTIONS = 2;       // 연속 2번
MIN_ACCELERATION_MAGNITUDE = 15.0f; // 15 m/s² 이상
```

### 최대 민감도:
```java
FALL_CONFIDENCE_THRESHOLD = 0.75f;  // 75% 이상
REQUIRED_FALL_DETECTIONS = 1;       // 1번만
MIN_ACCELERATION_MAGNITUDE = 12.0f; // 12 m/s² 이상
```

---

## 테스트 시나리오

1. **정상 활동 테스트:**
   - 걷기, 뛰기, 서있기 → Falls 감지 없어야 함

2. **유사 활동 테스트:**
   - 계단 오르내리기
   - 의자에 앉기
   - 침대에 눕기
   → Falls 감지 없어야 함 (여기서 오탐 발생 가능)

3. **실제 낙상 시뮬레이션:**
   - 매트리스에 넘어지기
   → Falls 감지되어야 함

---

## 문의사항

로그 파일이나 특정 오탐 상황을 공유하시면 더 자세한 분석이 가능합니다!
