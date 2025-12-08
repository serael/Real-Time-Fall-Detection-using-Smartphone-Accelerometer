package com.falldetection;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Fall Detection using TensorFlow Lite
 * Performs real-time fall detection using smartphone accelerometer data
 */
public class FallDetector {
    private static final String TAG = "FallDetector";

    private static final String MODEL_PATH = "fall_detection_model.tflite";
    private static final String SCALER_PATH = "scaler_params.json";
    private static final String LABEL_PATH = "label_info.json";

    private Interpreter tfliteInterpreter;
    private int windowSize;
    private String[] labels;
    private float[] scalerMean;
    private float[] scalerScale;

    // Sliding window buffer for sensor data
    private LinkedList<float[]> sensorBuffer;

    public FallDetector(Context context) throws IOException {
        // Load TFLite model
        tfliteInterpreter = new Interpreter(loadModelFile(context.getAssets(), MODEL_PATH));
        Log.d(TAG, "TFLite model loaded successfully");

        // Load scaler parameters
        loadScalerParams(context.getAssets());
        Log.d(TAG, "Scaler parameters loaded");

        // Load label info
        loadLabelInfo(context.getAssets());
        Log.d(TAG, "Label info loaded: " + labels.length + " classes");

        // Initialize sensor buffer
        sensorBuffer = new LinkedList<>();

        Log.d(TAG, "FallDetector initialized successfully");
        Log.d(TAG, "Window size: " + windowSize);
        Log.d(TAG, "Classes: " + String.join(", ", labels));
    }

    /**
     * Load TFLite model from assets
     */
    private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /**
     * Load scaler parameters from JSON
     */
    private void loadScalerParams(AssetManager assetManager) throws IOException {
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(assetManager.open(SCALER_PATH))
        );

        StringBuilder json = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            json.append(line);
        }
        reader.close();

        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, List<Double>>>(){}.getType();
        Map<String, List<Double>> scalerData = gson.fromJson(json.toString(), type);

        List<Double> meanList = scalerData.get("mean");
        List<Double> scaleList = scalerData.get("scale");

        scalerMean = new float[meanList.size()];
        scalerScale = new float[scaleList.size()];

        for (int i = 0; i < meanList.size(); i++) {
            scalerMean[i] = meanList.get(i).floatValue();
            scalerScale[i] = scaleList.get(i).floatValue();
        }

        Log.d(TAG, "Scaler mean: [" + scalerMean[0] + ", " + scalerMean[1] + ", " + scalerMean[2] + "]");
        Log.d(TAG, "Scaler scale: [" + scalerScale[0] + ", " + scalerScale[1] + ", " + scalerScale[2] + "]");
    }

    /**
     * Load label information from JSON
     */
    private void loadLabelInfo(AssetManager assetManager) throws IOException {
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(assetManager.open(LABEL_PATH))
        );

        StringBuilder json = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            json.append(line);
        }
        reader.close();

        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, Object>>(){}.getType();
        Map<String, Object> labelData = gson.fromJson(json.toString(), type);

        List<String> labelsList = (List<String>) labelData.get("labels");
        labels = labelsList.toArray(new String[0]);

        windowSize = ((Double) labelData.get("window_size")).intValue();
    }

    /**
     * Add new sensor reading to buffer
     * @param accX X-axis acceleration
     * @param accY Y-axis acceleration
     * @param accZ Z-axis acceleration
     */
    public void addSensorData(float accX, float accY, float accZ) {
        float[] data = new float[]{accX, accY, accZ};
        sensorBuffer.add(data);

        // Maintain window size
        if (sensorBuffer.size() > windowSize) {
            sensorBuffer.removeFirst();
        }
    }

    /**
     * Check if buffer has enough data for prediction
     */
    public boolean isReady() {
        return sensorBuffer.size() == windowSize;
    }

    /**
     * Normalize sensor data using StandardScaler parameters
     */
    private float[][][] normalizeData(List<float[]> data) {
        float[][][] normalized = new float[1][windowSize][3];

        for (int i = 0; i < data.size(); i++) {
            for (int j = 0; j < 3; j++) {
                normalized[0][i][j] = (data.get(i)[j] - scalerMean[j]) / scalerScale[j];
            }
        }

        return normalized;
    }

    /**
     * Perform fall detection prediction
     * @return Prediction result
     */
    public PredictionResult predict() {
        if (!isReady()) {
            throw new IllegalStateException("Not enough data in buffer. Need " + windowSize + " samples.");
        }

        // Convert buffer to list
        List<float[]> dataList = new ArrayList<>(sensorBuffer);

        // Normalize data
        float[][][] inputData = normalizeData(dataList);

        // Prepare output
        float[][] output = new float[1][labels.length];

        // Run inference
        tfliteInterpreter.run(inputData, output);

        // Find predicted class
        int predictedClass = 0;
        float maxProb = output[0][0];

        for (int i = 1; i < output[0].length; i++) {
            if (output[0][i] > maxProb) {
                maxProb = output[0][i];
                predictedClass = i;
            }
        }

        String predictedLabel = labels[predictedClass];

        // Clear some buffer for sliding window (25% step)
        int removeCount = windowSize / 4;
        for (int i = 0; i < removeCount && !sensorBuffer.isEmpty(); i++) {
            sensorBuffer.removeFirst();
        }

        Log.d(TAG, "Prediction: " + predictedLabel + " (confidence: " + String.format("%.2f%%", maxProb * 100) + ")");

        return new PredictionResult(predictedLabel, predictedClass, maxProb, output[0]);
    }

    /**
     * Clear sensor buffer
     */
    public void clearBuffer() {
        sensorBuffer.clear();
    }

    /**
     * Get current buffer size
     */
    public int getBufferSize() {
        return sensorBuffer.size();
    }

    /**
     * Get window size
     */
    public int getWindowSize() {
        return windowSize;
    }

    /**
     * Get label names
     */
    public String[] getLabels() {
        return labels;
    }

    /**
     * Release resources
     */
    public void close() {
        if (tfliteInterpreter != null) {
            tfliteInterpreter.close();
            tfliteInterpreter = null;
        }
        sensorBuffer.clear();
        Log.d(TAG, "FallDetector closed");
    }

    /**
     * Prediction result class
     */
    public static class PredictionResult {
        public final String label;
        public final int classIndex;
        public final float confidence;
        public final float[] probabilities;

        public PredictionResult(String label, int classIndex, float confidence, float[] probabilities) {
            this.label = label;
            this.classIndex = classIndex;
            this.confidence = confidence;
            this.probabilities = probabilities;
        }

        public boolean isFall() {
            return label.equalsIgnoreCase("falls");
        }
    }
}
