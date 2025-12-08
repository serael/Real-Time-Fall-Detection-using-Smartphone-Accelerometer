# 안드로이드 낙상 감지 앱

스마트폰의 가속도계 센서를 사용하여 단독으로 실시간 낙상을 감지하는 안드로이드 애플리케이션입니다.

## 주요 기능

- **완전 오프라인 동작**: 서버 연결 없이 스마트폰에서 단독으로 낙상 감지
- **TensorFlow Lite 사용**: 경량화된 딥러닝 모델로 실시간 추론
- **5가지 활동 분류**: 걷기, 달리기, 서있기, 주머니, 낙상
- **실시간 모니터링**: 현재 활동 및 신뢰도 표시
- **낙상 알림**: 낙상 감지 시 진동 알림

## 모델 정보

- **입력**: 400개 샘플 (2초 @ 200Hz) x 3축 가속도계 데이터
- **출력**: 5가지 활동 클래스에 대한 확률
- **모델 크기**: 6.4 MB
- **정확도**: 96.17%
- **낙상 감지 정밀도**: 100% (오탐 0건)

## 프로젝트 구조

```
android/
├── app/
│   ├── build.gradle              # 앱 빌드 설정 (TFLite 의존성 포함)
│   └── src/main/
│       ├── AndroidManifest.xml   # 앱 매니페스트 (권한 설정)
│       ├── assets/               # 모델 및 설정 파일
│       │   ├── fall_detection_model.tflite
│       │   ├── scaler_params.json
│       │   └── label_info.json
│       ├── java/com/falldetection/
│       │   ├── FallDetector.java      # TFLite 모델 래퍼 클래스
│       │   └── MainActivity.java      # 메인 액티비티
│       └── res/
│           ├── layout/
│           │   └── activity_main.xml  # UI 레이아웃
│           └── values/
│               └── strings.xml        # 문자열 리소스
├── build.gradle                  # 프로젝트 빌드 설정
├── settings.gradle               # Gradle 설정
└── gradle.properties             # Gradle 속성
```

## 핵심 컴포넌트

### 1. FallDetector.java
- TFLite 모델 로드 및 관리
- 가속도계 데이터 버퍼링 (슬라이딩 윈도우)
- StandardScaler를 사용한 데이터 정규화
- 모델 추론 실행
- 낙상 여부 판단

### 2. MainActivity.java
- 가속도계 센서 데이터 수집
- FallDetector를 사용한 실시간 추론
- UI 업데이트 및 통계 표시
- 낙상 감지 시 알림 (진동)

## 빌드 및 실행

### 필요 사항
- Android Studio Arctic Fox 이상
- Android SDK API 24 (Android 7.0) 이상
- 실제 안드로이드 기기 (가속도계 센서 필요)

### 빌드 방법

1. Android Studio에서 `android` 폴더 열기
2. Gradle 동기화 대기
3. 실제 안드로이드 기기를 USB로 연결
4. 개발자 옵션 및 USB 디버깅 활성화
5. Run 버튼 클릭 (또는 Shift + F10)

### APK 생성

```bash
cd android
./gradlew assembleRelease
# APK 위치: app/build/outputs/apk/release/app-release-unsigned.apk
```

## 사용 방법

1. **앱 실행**: 앱을 시작하면 초기화 메시지가 표시됩니다
2. **모니터링 시작**: "START" 버튼을 눌러 낙상 감지를 시작합니다
3. **데이터 수집**: 버퍼가 400개 샘플로 채워질 때까지 대기합니다 (~2초)
4. **실시간 분석**:
   - 현재 활동이 화면에 표시됩니다
   - 신뢰도가 퍼센트로 표시됩니다
   - 통계 정보가 업데이트됩니다
5. **낙상 감지**: 낙상이 감지되면:
   - 화면에 "⚠️ FALL DETECTED! ⚠️" 경고 표시
   - 진동 알림 (1초)
   - 낙상 카운터 증가

## 주요 파라미터

### 센서 설정
- **샘플링 레이트**: `SENSOR_DELAY_FASTEST` (~200Hz)
- **윈도우 크기**: 400 샘플 (2초)
- **슬라이딩**: 25% 중복 (100 샘플씩 이동)

### 정규화
- **방법**: StandardScaler (학습 시와 동일한 파라미터 사용)
- **평균 및 표준편차**: `scaler_params.json`에서 로드

## 성능

- **추론 시간**: < 100ms (대부분의 최신 스마트폰)
- **메모리 사용량**: ~50MB
- **배터리 영향**: 최소 (센서만 사용, GPU 불필요)

## 제한사항 및 개선사항

### 현재 제한사항
1. 낙상 데이터가 제한적이어서 재현율이 완벽하지 않음 (89.36%)
2. 가속도계만 사용 (자이로스코프 미사용)
3. 단일 기기에서만 테스트됨

### 향후 개선 방향
1. 더 많은 낙상 데이터 수집
2. 자이로스코프 센서 데이터 추가
3. 다양한 기기에서 검증
4. 낙상 후 긴급 연락 기능 추가
5. 데이터 로깅 및 분석 기능

## 기술 스택

- **언어**: Java
- **ML 프레임워크**: TensorFlow Lite
- **센서**: Android Sensor API
- **UI**: Android XML Layouts
- **빌드 도구**: Gradle

## 라이선스

이 프로젝트는 원본 프로젝트와 동일한 라이선스를 따릅니다.

## 문제 해결

### 앱이 실행되지 않음
- 안드로이드 버전이 7.0 (API 24) 이상인지 확인
- 가속도계 센서가 있는지 확인

### 모델 로드 실패
- assets 폴더에 모든 파일이 있는지 확인:
  - fall_detection_model.tflite
  - scaler_params.json
  - label_info.json

### 정확도가 낮음
- 스마트폰을 주머니나 손에 제대로 쥐고 있는지 확인
- 학습 데이터와 유사한 방식으로 활동 수행
