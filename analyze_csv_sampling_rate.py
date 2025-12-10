import pandas as pd
import numpy as np

def analyze_csv_sampling_rate(csv_file):
    """
    CSV 파일의 실제 샘플링 레이트 분석
    """
    print(f"\n{'='*80}")
    print(f"파일: {csv_file}")
    print('='*80)

    # CSV 로드
    df = pd.read_csv(csv_file)
    print(f"\n총 샘플 수: {len(df)}")
    print(f"컬럼: {list(df.columns)}")

    # 시간 컬럼 확인
    time_col = 'Time (s)'
    if time_col not in df.columns:
        print("❌ 'Time (s)' 컬럼이 없습니다!")
        return

    times = df[time_col].values

    # 시간 간격 계산 (초 단위)
    time_diffs = np.diff(times)

    # 샘플링 레이트 계산 (Hz)
    sampling_rates = 1.0 / time_diffs

    print(f"\n시간 간격 통계 (초):")
    print(f"  평균: {np.mean(time_diffs):.6f} s")
    print(f"  최소: {np.min(time_diffs):.6f} s")
    print(f"  최대: {np.max(time_diffs):.6f} s")
    print(f"  표준편차: {np.std(time_diffs):.6f} s")

    print(f"\n샘플링 레이트 통계 (Hz):")
    print(f"  평균: {np.mean(sampling_rates):.2f} Hz")
    print(f"  최소: {np.min(sampling_rates):.2f} Hz")
    print(f"  최대: {np.max(sampling_rates):.2f} Hz")
    print(f"  표준편차: {np.std(sampling_rates):.2f} Hz")
    print(f"  중간값: {np.median(sampling_rates):.2f} Hz")

    # 200Hz와의 차이
    diff_from_200 = np.abs(sampling_rates - 200.0)
    print(f"\n200Hz와의 차이:")
    print(f"  평균 차이: {np.mean(diff_from_200):.2f} Hz")
    print(f"  최대 차이: {np.max(diff_from_200):.2f} Hz")

    # 일정한 간격인지 확인
    time_diff_std = np.std(time_diffs)
    time_diff_mean = np.mean(time_diffs)
    cv = (time_diff_std / time_diff_mean) * 100  # Coefficient of Variation

    print(f"\n일정성 검사:")
    print(f"  변동 계수: {cv:.2f}%")
    if cv < 1.0:
        print(f"  ✅ 매우 일정함")
    elif cv < 5.0:
        print(f"  ⚠️ 대체로 일정함")
    else:
        print(f"  ❌ 불규칙함")

    # 200Hz 기준 (5ms) 확인
    expected_interval = 1.0 / 200.0  # 0.005 seconds
    within_tolerance = np.sum(np.abs(time_diffs - expected_interval) < 0.001)
    percentage = (within_tolerance / len(time_diffs)) * 100

    print(f"\n200Hz 기준 (5ms ± 1ms) 만족:")
    print(f"  {within_tolerance}/{len(time_diffs)} ({percentage:.1f}%)")

    if percentage > 95:
        print(f"  ✅ 200Hz로 수집됨")
    elif percentage > 80:
        print(f"  ⚠️ 대부분 200Hz, 일부 변동")
    else:
        print(f"  ❌ 200Hz 아님")

    # 처음 10개 간격 출력
    print(f"\n처음 10개 샘플 간격:")
    for i in range(min(10, len(time_diffs))):
        interval_ms = time_diffs[i] * 1000
        rate = sampling_rates[i]
        print(f"  {i+1}: {interval_ms:.3f} ms ({rate:.1f} Hz)")

    return {
        'mean_rate': np.mean(sampling_rates),
        'std_rate': np.std(sampling_rates),
        'cv': cv,
        'is_200hz': percentage > 95
    }


def main():
    import glob

    print("="*80)
    print("CSV 파일 샘플링 레이트 분석")
    print("="*80)

    csv_files = glob.glob('sensor_data/*.csv')

    results = {}
    for csv_file in sorted(csv_files):
        result = analyze_csv_sampling_rate(csv_file)
        results[csv_file] = result

    # 전체 요약
    print("\n" + "="*80)
    print("전체 요약")
    print("="*80)

    all_200hz = True
    for csv_file, result in results.items():
        if result:
            status = "✅" if result['is_200hz'] else "❌"
            print(f"{status} {csv_file}: {result['mean_rate']:.1f} Hz (±{result['std_rate']:.1f})")
            if not result['is_200hz']:
                all_200hz = False

    print("\n" + "="*80)
    if all_200hz:
        print("결론: ✅ 모든 CSV 파일이 200Hz로 수집되었습니다!")
    else:
        print("결론: ❌ 일부 CSV 파일이 200Hz가 아닙니다!")
    print("="*80)


if __name__ == "__main__":
    main()
