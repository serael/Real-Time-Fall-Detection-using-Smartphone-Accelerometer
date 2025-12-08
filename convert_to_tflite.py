import tensorflow as tf
import numpy as np
import pickle

def convert_h5_to_tflite():
    """
    Convert fall_detection_model.h5 to TensorFlow Lite format
    Also saves scaler parameters for Android app
    """
    print("=" * 60)
    print("CONVERTING H5 MODEL TO TENSORFLOW LITE")
    print("=" * 60)

    # Load the trained Keras model
    print("\n1. Loading H5 model...")
    model = tf.keras.models.load_model('models/fall_detection_model.h5', compile=False)
    print("   ✓ Model loaded successfully")

    # Print model summary
    print("\n2. Model Summary:")
    model.summary()

    # Load metadata to understand model configuration
    print("\n3. Loading metadata...")
    with open('processed_data/metadata.pkl', 'rb') as f:
        metadata = pickle.load(f)
    print(f"   ✓ Window size: {metadata['window_size']}")
    print(f"   ✓ Classes: {metadata['label_names']}")

    # Load scaler
    print("\n4. Loading scaler...")
    with open('processed_data/scaler.pkl', 'rb') as f:
        scaler = pickle.load(f)
    print("   ✓ Scaler loaded")
    print(f"   ✓ Scaler mean shape: {scaler.mean_.shape}")
    print(f"   ✓ Scaler scale shape: {scaler.scale_.shape}")

    # Save scaler parameters to text file for Android
    print("\n5. Saving scaler parameters for Android...")
    scaler_params = {
        'mean': scaler.mean_.tolist(),
        'scale': scaler.scale_.tolist()
    }

    import json
    with open('models/scaler_params.json', 'w') as f:
        json.dump(scaler_params, f, indent=2)
    print("   ✓ Saved to models/scaler_params.json")

    # Save label names for Android
    print("\n6. Saving label names for Android...")
    label_info = {
        'labels': list(metadata['label_names'].values()),
        'window_size': metadata['window_size']
    }
    with open('models/label_info.json', 'w') as f:
        json.dump(label_info, f, indent=2)
    print("   ✓ Saved to models/label_info.json")

    # Convert to TensorFlow Lite
    print("\n7. Converting to TensorFlow Lite...")

    # Create converter with concrete function to avoid BatchNorm issues
    run_model = tf.function(lambda x: model(x, training=False))
    concrete_func = run_model.get_concrete_function(
        tf.TensorSpec(shape=[1, metadata['window_size'], 3], dtype=tf.float32)
    )

    converter = tf.lite.TFLiteConverter.from_concrete_functions([concrete_func])

    # Optimization settings (optional, comment out if issues persist)
    # converter.optimizations = [tf.lite.Optimize.DEFAULT]

    # Convert the model
    tflite_model = converter.convert()

    # Save the TFLite model
    tflite_path = 'models/fall_detection_model.tflite'
    with open(tflite_path, 'wb') as f:
        f.write(tflite_model)

    print(f"   ✓ TFLite model saved to {tflite_path}")
    print(f"   ✓ Model size: {len(tflite_model) / 1024:.2f} KB")

    # Test the TFLite model
    print("\n8. Testing TFLite model...")
    interpreter = tf.lite.Interpreter(model_path=tflite_path)
    interpreter.allocate_tensors()

    # Get input and output details
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    print(f"   ✓ Input shape: {input_details[0]['shape']}")
    print(f"   ✓ Input type: {input_details[0]['dtype']}")
    print(f"   ✓ Output shape: {output_details[0]['shape']}")
    print(f"   ✓ Output type: {output_details[0]['dtype']}")

    # Test with random data
    test_data = np.random.randn(1, metadata['window_size'], 3).astype(np.float32)
    interpreter.set_tensor(input_details[0]['index'], test_data)
    interpreter.invoke()
    output = interpreter.get_tensor(output_details[0]['index'])

    print(f"   ✓ Test inference successful!")
    print(f"   ✓ Output probabilities: {output[0]}")
    print(f"   ✓ Predicted class: {np.argmax(output[0])} ({metadata['label_names'][np.argmax(output[0])]})")

    # Compare with original model
    print("\n9. Comparing with original H5 model...")
    h5_output = model.predict(test_data, verbose=0)

    max_diff = np.max(np.abs(h5_output - output))
    print(f"   ✓ Maximum difference: {max_diff}")

    if max_diff < 1e-5:
        print("   ✓ Models match perfectly!")
    elif max_diff < 1e-3:
        print("   ✓ Models match closely (acceptable difference)")
    else:
        print("   ⚠ Warning: Models have significant differences")

    print("\n" + "=" * 60)
    print("CONVERSION COMPLETE!")
    print("=" * 60)
    print("\nGenerated files:")
    print("  ✓ models/fall_detection_model.tflite")
    print("  ✓ models/scaler_params.json")
    print("  ✓ models/label_info.json")
    print("\nNext step: Integrate into Android app!")
    print("=" * 60)

if __name__ == "__main__":
    convert_h5_to_tflite()
