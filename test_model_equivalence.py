import numpy as np
import tensorflow as tf
import pickle

def test_model_equivalence():
    """
    H5 모델과 TFLite 모델이 같은 입력에 대해 같은 출력을 내는지 테스트
    """
    print("=" * 80)
    print("H5 vs TFLite 모델 등가성 테스트")
    print("=" * 80)

    # 1. 테스트 데이터 로드
    print("\n1. 테스트 데이터 로드...")
    X_test = np.load('processed_data/X_test.npy')
    y_test = np.load('processed_data/y_test.npy')

    with open('processed_data/metadata.pkl', 'rb') as f:
        metadata = pickle.load(f)

    print(f"   ✓ 테스트 샘플 수: {len(X_test)}")
    print(f"   ✓ 데이터 shape: {X_test.shape}")
    print(f"   ✓ 클래스: {metadata['label_names']}")

    # 2. H5 모델 로드
    print("\n2. H5 모델 로드...")
    h5_model = tf.keras.models.load_model('models/fall_detection_model.h5', compile=False)
    print("   ✓ H5 모델 로드 완료")

    # 3. TFLite 모델 로드
    print("\n3. TFLite 모델 로드...")
    tflite_interpreter = tf.lite.Interpreter(model_path='models/fall_detection_model.tflite')
    tflite_interpreter.allocate_tensors()

    input_details = tflite_interpreter.get_input_details()
    output_details = tflite_interpreter.get_output_details()

    print("   ✓ TFLite 모델 로드 완료")
    print(f"   ✓ Input shape: {input_details[0]['shape']}")
    print(f"   ✓ Output shape: {output_details[0]['shape']}")

    # 4. 전체 테스트 데이터로 예측
    print("\n4. 전체 테스트 데이터로 예측 중...")

    # H5 모델 예측
    h5_predictions = h5_model.predict(X_test, verbose=0)
    h5_classes = np.argmax(h5_predictions, axis=1)

    # TFLite 모델 예측
    tflite_predictions = []
    tflite_classes = []

    for i, sample in enumerate(X_test):
        if (i + 1) % 50 == 0:
            print(f"   처리 중: {i+1}/{len(X_test)}")

        # TFLite 입력 형식으로 변환
        input_data = sample.reshape(1, *sample.shape).astype(np.float32)
        tflite_interpreter.set_tensor(input_details[0]['index'], input_data)
        tflite_interpreter.invoke()
        output = tflite_interpreter.get_tensor(output_details[0]['index'])

        tflite_predictions.append(output[0])
        tflite_classes.append(np.argmax(output[0]))

    tflite_predictions = np.array(tflite_predictions)
    tflite_classes = np.array(tflite_classes)

    print(f"   ✓ 예측 완료: {len(X_test)} 샘플")

    # 5. 출력 비교
    print("\n" + "=" * 80)
    print("5. 결과 비교")
    print("=" * 80)

    # 5-1. 확률 값 차이 분석
    prob_diff = np.abs(h5_predictions - tflite_predictions)
    max_diff = np.max(prob_diff)
    mean_diff = np.mean(prob_diff)

    print(f"\n확률 값 차이:")
    print(f"   최대 차이: {max_diff:.10f}")
    print(f"   평균 차이: {mean_diff:.10f}")
    print(f"   중간 차이: {np.median(prob_diff):.10f}")

    # 5-2. 예측 클래스 일치도
    class_match = (h5_classes == tflite_classes)
    match_rate = np.mean(class_match) * 100

    print(f"\n예측 클래스 일치도:")
    print(f"   일치: {np.sum(class_match)}/{len(X_test)} ({match_rate:.2f}%)")
    print(f"   불일치: {np.sum(~class_match)}/{len(X_test)} ({100-match_rate:.2f}%)")

    # 5-3. 불일치 샘플 상세 분석
    if np.sum(~class_match) > 0:
        print(f"\n⚠️  불일치 샘플 분석:")
        mismatch_indices = np.where(~class_match)[0]

        for idx in mismatch_indices[:10]:  # 처음 10개만 표시
            print(f"\n   샘플 #{idx}:")
            print(f"   실제 레이블: {metadata['label_names'][y_test[idx]]}")
            print(f"   H5 예측: {metadata['label_names'][h5_classes[idx]]} (확률: {h5_predictions[idx]})")
            print(f"   TFLite 예측: {metadata['label_names'][tflite_classes[idx]]} (확률: {tflite_predictions[idx]})")
            print(f"   확률 차이: {prob_diff[idx]}")

    # 5-4. 클래스별 일치도
    print(f"\n클래스별 일치도:")
    for class_id, class_name in metadata['label_names'].items():
        class_mask = (y_test == class_id)
        if np.sum(class_mask) > 0:
            class_match_rate = np.mean(class_match[class_mask]) * 100
            print(f"   {class_name:10s}: {class_match_rate:6.2f}% ({np.sum(class_match[class_mask])}/{np.sum(class_mask)})")

    # 5-5. Falls 클래스 특별 분석
    print(f"\n" + "=" * 80)
    print("Falls 클래스 상세 분석 (오탐 확인)")
    print("=" * 80)

    falls_class_id = 4  # falls

    # H5 모델의 Falls 예측
    h5_falls_pred = (h5_classes == falls_class_id)
    h5_falls_true = (y_test == falls_class_id)

    h5_tp = np.sum(h5_falls_pred & h5_falls_true)  # True Positive
    h5_fp = np.sum(h5_falls_pred & ~h5_falls_true)  # False Positive (오탐)
    h5_fn = np.sum(~h5_falls_pred & h5_falls_true)  # False Negative (놓침)
    h5_tn = np.sum(~h5_falls_pred & ~h5_falls_true)  # True Negative

    # TFLite 모델의 Falls 예측
    tflite_falls_pred = (tflite_classes == falls_class_id)
    tflite_falls_true = (y_test == falls_class_id)

    tflite_tp = np.sum(tflite_falls_pred & tflite_falls_true)
    tflite_fp = np.sum(tflite_falls_pred & ~tflite_falls_true)  # 오탐
    tflite_fn = np.sum(~tflite_falls_pred & tflite_falls_true)
    tflite_tn = np.sum(~tflite_falls_pred & ~tflite_falls_true)

    print(f"\nH5 모델:")
    print(f"   True Positive (정확히 감지):  {h5_tp}")
    print(f"   False Positive (오탐):        {h5_fp} ⚠️")
    print(f"   False Negative (놓침):        {h5_fn}")
    print(f"   True Negative (정확히 무시):  {h5_tn}")
    if h5_tp + h5_fp > 0:
        h5_precision = h5_tp / (h5_tp + h5_fp)
        print(f"   Precision: {h5_precision:.4f} ({h5_precision*100:.2f}%)")

    print(f"\nTFLite 모델:")
    print(f"   True Positive (정확히 감지):  {tflite_tp}")
    print(f"   False Positive (오탐):        {tflite_fp} ⚠️")
    print(f"   False Negative (놓침):        {tflite_fn}")
    print(f"   True Negative (정확히 무시):  {tflite_tn}")
    if tflite_tp + tflite_fp > 0:
        tflite_precision = tflite_tp / (tflite_tp + tflite_fp)
        print(f"   Precision: {tflite_precision:.4f} ({tflite_precision*100:.2f}%)")

    # 오탐 샘플 상세 분석
    if h5_fp > 0:
        print(f"\n⚠️  H5 모델 오탐 샘플 분석:")
        h5_fp_indices = np.where(h5_falls_pred & ~h5_falls_true)[0]
        for idx in h5_fp_indices[:5]:
            print(f"\n   샘플 #{idx}:")
            print(f"   실제: {metadata['label_names'][y_test[idx]]}")
            print(f"   예측: falls (확률: {h5_predictions[idx][falls_class_id]:.4f})")
            print(f"   모든 확률: {h5_predictions[idx]}")

    if tflite_fp > 0:
        print(f"\n⚠️  TFLite 모델 오탐 샘플 분석:")
        tflite_fp_indices = np.where(tflite_falls_pred & ~tflite_falls_true)[0]
        for idx in tflite_fp_indices[:5]:
            print(f"\n   샘플 #{idx}:")
            print(f"   실제: {metadata['label_names'][y_test[idx]]}")
            print(f"   예측: falls (확률: {tflite_predictions[idx][falls_class_id]:.4f})")
            print(f"   모든 확률: {tflite_predictions[idx]}")

    # 6. 결론
    print("\n" + "=" * 80)
    print("결론")
    print("=" * 80)

    if max_diff < 1e-5:
        print("✅ H5와 TFLite 모델이 거의 동일한 출력을 생성합니다.")
    elif max_diff < 1e-3:
        print("⚠️  H5와 TFLite 모델 간 작은 차이가 있지만 허용 범위입니다.")
    else:
        print("❌ H5와 TFLite 모델 간 큰 차이가 발견되었습니다!")

    if match_rate == 100.0:
        print("✅ 모든 샘플에서 동일한 클래스를 예측합니다.")
    elif match_rate >= 99.0:
        print(f"⚠️  대부분의 샘플({match_rate:.2f}%)에서 동일한 클래스를 예측합니다.")
    else:
        print(f"❌ 많은 샘플({100-match_rate:.2f}%)에서 다른 클래스를 예측합니다!")

    print("\n" + "=" * 80)

if __name__ == "__main__":
    test_model_equivalence()
