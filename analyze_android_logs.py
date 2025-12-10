import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import sys

def analyze_android_logs(log_file):
    """
    안드로이드 앱에서 수집한 로그를 분석하여 오탐 원인 파악
    """
    print("=" * 80)
    print("안드로이드 로그 분석 - 오탐 원인 진단")
    print("=" * 80)

    # 로그 파일 로드
    print(f"\n로그 파일 로드: {log_file}")
    df = pd.read_csv(log_file)

    print(f"총 예측 수: {len(df)}")
    print(f"컬럼: {list(df.columns)}")

    # 1. Falls 예측 분석
    print("\n" + "=" * 80)
    print("1. Falls 예측 분석")
    print("=" * 80)

    falls_predictions = df[df['predicted_label'] == 'falls']
    print(f"Falls로 예측된 횟수: {len(falls_predictions)} ({len(falls_predictions)/len(df)*100:.2f}%)")

    if len(falls_predictions) > 0:
        print("\n⚠️  Falls 예측 상세:")
        print(falls_predictions[['timestamp', 'confidence', 'prob_falls',
                                  'prob_walking', 'prob_running',
                                  'prob_standing', 'prob_pocket']].to_string())

        # Falls 예측 시 신뢰도 분포
        print(f"\nFalls 신뢰도 통계:")
        print(f"  평균: {falls_predictions['confidence'].mean():.4f}")
        print(f"  최소: {falls_predictions['confidence'].min():.4f}")
        print(f"  최대: {falls_predictions['confidence'].max():.4f}")
        print(f"  중간: {falls_predictions['confidence'].median():.4f}")

    # 2. 신뢰도 임계값 분석
    print("\n" + "=" * 80)
    print("2. 신뢰도 임계값 분석")
    print("=" * 80)

    thresholds = [0.5, 0.6, 0.7, 0.8, 0.9, 0.95, 0.99]
    print("\n임계값에 따른 Falls 예측 수:")
    for threshold in thresholds:
        count = len(df[(df['predicted_label'] == 'falls') &
                       (df['confidence'] >= threshold)])
        print(f"  신뢰도 >= {threshold:.2f}: {count}건")

    # 3. 센서 데이터 통계
    print("\n" + "=" * 80)
    print("3. 센서 데이터 통계")
    print("=" * 80)

    print(f"\n전체 데이터:")
    print(f"  accX 범위: [{df['accX'].min():.2f}, {df['accX'].max():.2f}]")
    print(f"  accY 범위: [{df['accY'].min():.2f}, {df['accY'].max():.2f}]")
    print(f"  accZ 범위: [{df['accZ'].min():.2f}, {df['accZ'].max():.2f}]")

    if len(falls_predictions) > 0:
        print(f"\nFalls 예측 시:")
        print(f"  accX 범위: [{falls_predictions['accX'].min():.2f}, "
              f"{falls_predictions['accX'].max():.2f}]")
        print(f"  accY 범위: [{falls_predictions['accY'].min():.2f}, "
              f"{falls_predictions['accY'].max():.2f}]")
        print(f"  accZ 범위: [{falls_predictions['accZ'].min():.2f}, "
              f"{falls_predictions['accZ'].max():.2f}]")

    # 4. 활동 분포
    print("\n" + "=" * 80)
    print("4. 활동 분포")
    print("=" * 80)

    activity_counts = df['predicted_label'].value_counts()
    print("\n예측된 활동:")
    for activity, count in activity_counts.items():
        percentage = count / len(df) * 100
        print(f"  {activity:10s}: {count:4d} ({percentage:5.1f}%)")

    # 5. 시계열 시각화
    print("\n" + "=" * 80)
    print("5. 시각화 생성 중...")
    print("=" * 80)

    fig, axes = plt.subplots(3, 1, figsize=(14, 10))

    # 5-1. Falls 확률 시계열
    axes[0].plot(df.index, df['prob_falls'], label='Falls 확률', color='red', alpha=0.7)
    axes[0].axhline(y=0.5, color='orange', linestyle='--', label='임계값 0.5')
    axes[0].axhline(y=0.8, color='darkred', linestyle='--', label='임계값 0.8')
    axes[0].set_ylabel('Falls 확률')
    axes[0].set_title('Falls 예측 확률 시계열')
    axes[0].legend()
    axes[0].grid(True, alpha=0.3)

    # Falls 예측 지점 표시
    if len(falls_predictions) > 0:
        falls_indices = falls_predictions.index
        axes[0].scatter(falls_indices, df.loc[falls_indices, 'prob_falls'],
                       color='red', s=100, marker='x', linewidths=3,
                       label='Falls 감지', zorder=5)

    # 5-2. 센서 데이터
    axes[1].plot(df.index, df['accX'], label='X', alpha=0.7)
    axes[1].plot(df.index, df['accY'], label='Y', alpha=0.7)
    axes[1].plot(df.index, df['accZ'], label='Z', alpha=0.7)
    axes[1].set_ylabel('가속도 (m/s²)')
    axes[1].set_title('센서 데이터')
    axes[1].legend()
    axes[1].grid(True, alpha=0.3)

    # 5-3. 모든 클래스 확률
    axes[2].plot(df.index, df['prob_walking'], label='Walking', alpha=0.7)
    axes[2].plot(df.index, df['prob_running'], label='Running', alpha=0.7)
    axes[2].plot(df.index, df['prob_standing'], label='Standing', alpha=0.7)
    axes[2].plot(df.index, df['prob_pocket'], label='Pocket', alpha=0.7)
    axes[2].plot(df.index, df['prob_falls'], label='Falls', alpha=0.7, linewidth=2)
    axes[2].set_xlabel('예측 번호')
    axes[2].set_ylabel('확률')
    axes[2].set_title('모든 클래스 확률')
    axes[2].legend()
    axes[2].grid(True, alpha=0.3)

    plt.tight_layout()
    output_file = log_file.replace('.csv', '_analysis.png')
    plt.savefig(output_file, dpi=150, bbox_inches='tight')
    print(f"시각화 저장: {output_file}")

    # 6. 권장사항
    print("\n" + "=" * 80)
    print("6. 권장사항")
    print("=" * 80)

    if len(falls_predictions) > 0:
        avg_confidence = falls_predictions['confidence'].mean()

        print("\n⚠️  오탐 감소 방법:")

        if avg_confidence < 0.8:
            print(f"  1. Falls 신뢰도가 낮음 (평균 {avg_confidence:.2f})")
            print(f"     → 신뢰도 임계값을 0.8 이상으로 설정")

        if len(falls_predictions) / len(df) > 0.1:
            print(f"  2. Falls 예측 빈도가 높음 ({len(falls_predictions)/len(df)*100:.1f}%)")
            print(f"     → 모델 재학습 필요 (더 다양한 훈련 데이터)")

        print(f"  3. 디바운싱 추가:")
        print(f"     → 연속된 N번의 Falls 예측이 있을 때만 알림")
    else:
        print("✅ Falls 예측 없음 - 모델이 정상적으로 작동 중")

    print("\n" + "=" * 80)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("사용법: python analyze_android_logs.py <로그파일.csv>")
        print("예시: python analyze_android_logs.py predictions_20241210_123456.csv")
    else:
        analyze_android_logs(sys.argv[1])
