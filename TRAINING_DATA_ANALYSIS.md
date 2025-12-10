# 훈련 데이터 샘플링 레이트 분석 결과

## 질문
> 모델 학습할때 csv시간값이 200hz로 맞춰서 작성됬는지 아니면 학습할때 데이터를 200hz로 맞춰서 학습하는지 확인

## 답변

### ✅ CSV 파일은 이미 ~200Hz로 수집되었습니다

분석 결과, 모든 훈련 데이터 CSV 파일은 **수집 시점부터 이미 200Hz에 근접한 샘플링 레이트**로 기록되었습니다.

---

## 상세 분석 결과

### 1. 실제 샘플링 레이트 측정

| 파일 | 평균 샘플링 레이트 | 표준편차 | 변동 계수 | 200Hz 기준 만족률 |
|------|------------------|---------|---------|-----------------|
| `falls.csv` | 198.84 Hz | ±0.49 Hz | 0.25% | 100.0% |
| `pocket.csv` | 198.84 Hz | ±0.49 Hz | 0.25% | 100.0% |
| `running.csv` | 198.86 Hz | ±0.51 Hz | 0.26% | 100.0% |
| `standing.csv` | 198.86 Hz | ±0.58 Hz | 0.29% | 100.0% |
| `walking.csv` | 198.84 Hz | ±0.49 Hz | 0.25% | 100.0% |

**결론:** 모든 파일이 198.8~198.9 Hz로 매우 일정하게 수집됨

### 2. 시간 간격 분석

- **평균 간격**: 5.029 ms (목표: 5.000 ms)
- **변동성**: 0.25-0.29% (매우 낮음 → 매우 일정함)
- **200Hz 기준 (5ms ± 1ms) 만족률**: **100%**

**예시 - walking.csv 처음 10개 샘플 간격:**
```
  1: 5.035 ms (198.6 Hz)
  2: 5.005 ms (199.8 Hz)
  3: 5.035 ms (198.6 Hz)
  4: 5.035 ms (198.6 Hz)
  5: 5.005 ms (199.8 Hz)
  ...
```

→ 간격이 5.005ms와 5.035ms 사이로 매우 규칙적으로 반복됨

---

## 전처리 과정 확인

### preprocess_data.py 분석

**결론: 전처리 과정에서 리샘플링을 하지 않습니다.**

```python
# preprocess_data.py 핵심 로직

def load_data_from_folder(self, folder_path):
    # CSV 파일을 그대로 로드
    df = pd.read_csv(csv_file)
    acc_x = df['Acceleration x (m/s^2)'].values  # 그대로 사용
    acc_y = df['Acceleration y (m/s^2)'].values
    acc_z = df['Acceleration z (m/s^2)'].values
    # ❌ 리샘플링 없음

def create_windows(self, data_list):
    # 슬라이딩 윈도우로 잘라내기만 함
    for start in range(0, len(acc_x) - self.window_size + 1, self.step_size):
        end = start + self.window_size
        window_x = acc_x[start:end]  # 단순 슬라이싱
        window_y = acc_y[start:end]
        window_z = acc_z[start:end]
        # ❌ 보간/리샘플링 없음
```

**전처리 과정:**
1. CSV에서 가속도 값 읽기 → **그대로 사용**
2. 400개씩 슬라이딩 윈도우 생성 → **단순 슬라이싱**
3. StandardScaler로 정규화 → **값만 스케일링**

**가정:**
- `window_size=400` = 2초 @ 200Hz (코드 주석에 명시)
- CSV 데이터가 이미 200Hz라고 가정하고 처리

---

## 핵심 결론

### ✅ 훈련 데이터는 수집 시점부터 200Hz였습니다

1. **CSV 파일**: 198.8~198.9 Hz로 수집됨 (매우 일정)
2. **전처리**: 리샘플링 없이 그대로 사용
3. **모델 훈련**: CSV의 샘플링 레이트를 그대로 사용

---

## 안드로이드 앱에 미치는 영향

### 문제
안드로이드 앱이 `SENSOR_DELAY_FASTEST`를 사용하면:
- 실제 샘플링 레이트: 100~300Hz (기기마다 다름)
- 훈련 데이터: ~198.8 Hz (거의 200Hz)
- **→ 샘플링 레이트 불일치로 패턴 인식 정확도 저하**

### 해결책
안드로이드 앱도 정확히 200Hz로 샘플링해야 합니다:

#### 방법 1: FixedRateSensorCollector (간단)
```java
private FixedRateSensorCollector sensorCollector;
sensorCollector = new FixedRateSensorCollector(this);
```
- 타임스탬프 기반으로 5ms 간격 필터링
- 실제 샘플링 레이트: ~199-200 Hz

#### 방법 2: InterpolatedSensorCollector (정확)
```java
private InterpolatedSensorCollector sensorCollector;
sensorCollector = new InterpolatedSensorCollector(this);
```
- 선형 보간으로 정확히 5ms 간격 생성
- 실제 샘플링 레이트: 정확히 200.0 Hz

---

## 비교표

| | 훈련 데이터 (CSV) | 기존 안드로이드 | 수정 후 안드로이드 |
|---|---|---|---|
| **샘플링 방법** | PhyPhox 앱 수집 | SENSOR_DELAY_FASTEST | FixedRate / Interpolated |
| **샘플링 레이트** | 198.8~198.9 Hz | 가변 (100~300 Hz) | 199~200 Hz |
| **일정성** | 매우 높음 (CV 0.25%) | 낮음 | 높음 |
| **400 샘플 = 2초** | ✅ 예 | ❌ 아니오 | ✅ 예 |
| **패턴 일치** | 기준 | ❌ 불일치 | ✅ 일치 |

---

## 권장 사항

### 즉시 적용
```java
// MainActivity.java onCreate()
sensorCollector = new InterpolatedSensorCollector(this);
```

### 효과
- ✅ 훈련 데이터와 동일한 샘플링 레이트
- ✅ 400 샘플 = 정확히 2초
- ✅ 패턴 인식 정확도 향상
- ✅ 오탐 감소

---

## 검증 방법

### 1. 로그 확인
```java
private void monitorSamplingRate() {
    sampleCount++;

    long elapsed = System.currentTimeMillis() - startTime;
    if (elapsed >= 5000) {  // 5초마다
        float actualRate = sampleCount / (elapsed / 1000.0f);
        Log.i(TAG, "Actual sampling rate: " + String.format("%.1f Hz", actualRate));

        sampleCount = 0;
        startTime = System.currentTimeMillis();
    }
}
```

### 2. 예상 출력
```
// 기존 방식 (SENSOR_DELAY_FASTEST)
Actual sampling rate: 245.3 Hz  ❌

// FixedRateSensorCollector
Actual sampling rate: 199.5 Hz  ✅

// InterpolatedSensorCollector
Actual sampling rate: 200.0 Hz  ✅✅
```

---

## 참고 파일

- `analyze_csv_sampling_rate.py` - CSV 샘플링 레이트 분석 스크립트
- `SENSOR_SAMPLING_GUIDE.md` - 200Hz 샘플링 구현 가이드
- `FixedRateSensorCollector.java` - 타임스탬프 기반 200Hz 수집
- `InterpolatedSensorCollector.java` - 선형 보간 200Hz 수집
