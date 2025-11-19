package com.example.miappopencv;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.util.Size; // Clase de Android para la cámara
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NumberRecognitionActivity extends AppCompatActivity {

    private static final String TAG = "TFLite_Tracking";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};

    private PreviewView previewView;
    private View reticleView;
    private TextView tvPrediction;
    private ExecutorService cameraExecutor;
    private Interpreter tflite;

    // Configuración
    private static final String TFLITE_MODEL_NAME = "modelo_tinyFinal.tflite";
    private static final int MODEL_INPUT_SIZE = 28;

    // Mats de OpenCV
    private Mat yuvMat, grayMat, rotatedMat, blurredMat, thresholdMat, hierarchy;
    private Mat croppedMat, resizedMat, invertedMat, floatMat;

    static {
        if (OpenCVLoader.initDebug()) Log.d(TAG, "OpenCV OK");
        else Log.e(TAG, "OpenCV Error");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_number_recognition);

        previewView = findViewById(R.id.tflite_preview_view);
        reticleView = findViewById(R.id.reticle_view);
        tvPrediction = findViewById(R.id.tv_prediction);
        cameraExecutor = Executors.newSingleThreadExecutor();

        try {
            tflite = new Interpreter(loadModelFile());
        } catch (IOException e) {
            Toast.makeText(this, "Error modelo", Toast.LENGTH_SHORT).show();
            finish();
        }

        if (allPermissionsGranted()) startCamera();
        else ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Resolución VGA para rastreo rápido
                ImageAnalysis analyzer = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(480, 640)) // android.util.Size
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                analyzer.setAnalyzer(cameraExecutor, new TrackingAnalyzer());

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer);
            } catch (Exception e) {
                Log.e(TAG, "Error CameraX", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // --- ANALIZADOR DE RASTREO ---
    private class TrackingAnalyzer implements ImageAnalysis.Analyzer {
        @Override
        public void analyze(@NonNull ImageProxy image) {
            if (yuvMat == null) {
                yuvMat = new Mat();
                grayMat = new Mat();
                rotatedMat = new Mat();
                blurredMat = new Mat();
                thresholdMat = new Mat();
                hierarchy = new Mat();
                resizedMat = new Mat(MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, CvType.CV_8UC1);
                invertedMat = new Mat(MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, CvType.CV_8UC1);
                floatMat = new Mat(MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, CvType.CV_32F);
            }

            // 1. Preparar imagen (Grises + Rotación)
            convertImageToGrayMat(image);

            // 2. Detección de Contornos (OpenCV)
            Imgproc.GaussianBlur(rotatedMat, blurredMat, new org.opencv.core.Size(5, 5), 0);
            Imgproc.adaptiveThreshold(blurredMat, thresholdMat, 255,
                    Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 11, 2);

            List<MatOfPoint> contours = new ArrayList<>();
            Imgproc.findContours(thresholdMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            // 3. Buscar el mejor candidato
            Rect bestRect = findBestContour(contours);

            if (bestRect != null) {
                // 4. Recortar y Clasificar
                int padding = 15;
                int x = Math.max(bestRect.x - padding, 0);
                int y = Math.max(bestRect.y - padding, 0);
                int w = Math.min(bestRect.width + 2 * padding, rotatedMat.cols() - x);
                int h = Math.min(bestRect.height + 2 * padding, rotatedMat.rows() - y);

                Rect cropRect = new Rect(x, y, w, h);
                croppedMat = new Mat(rotatedMat, cropRect);

                ByteBuffer input = convertMatToByteBuffer(croppedMat);
                Pair<Integer, Float> result = runInference(input);

                // 5. Actualizar UI Dinámica
                int imgW = rotatedMat.cols();
                int imgH = rotatedMat.rows();
                Rect finalRect = bestRect;

                runOnUiThread(() -> updateTrackingUI(finalRect, imgW, imgH, result.first, result.second));
            } else {
                runOnUiThread(() -> {
                    reticleView.setVisibility(View.INVISIBLE);
                    tvPrediction.setVisibility(View.INVISIBLE);
                });
            }
            image.close();
        }

        private Rect findBestContour(List<MatOfPoint> contours) {
            Rect bestRect = null;
            double maxArea = 0;
            for (MatOfPoint contour : contours) {
                double area = Imgproc.contourArea(contour);
                if (area > 400) { // Filtro de ruido
                    Rect rect = Imgproc.boundingRect(contour);
                    float aspect = (float) rect.width / (float) rect.height;
                    if (aspect > 0.2 && aspect < 3.0) { // Filtro de forma
                        if (area > maxArea) {
                            maxArea = area;
                            bestRect = rect;
                        }
                    }
                }
            }
            return bestRect;
        }

        private void convertImageToGrayMat(ImageProxy image) {
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            Mat yMat = new Mat(image.getHeight(), image.getWidth(), CvType.CV_8UC1);
            yMat.put(0, 0, data);

            int rotation = image.getImageInfo().getRotationDegrees();
            if (rotation == 90) Core.rotate(yMat, rotatedMat, Core.ROTATE_90_CLOCKWISE);
            else if (rotation == 270) Core.rotate(yMat, rotatedMat, Core.ROTATE_90_COUNTERCLOCKWISE);
            else if (rotation == 180) Core.rotate(yMat, rotatedMat, Core.ROTATE_180);
            else yMat.copyTo(rotatedMat);
        }
    }

    // --- UI DINÁMICA ---
    private void updateTrackingUI(Rect rect, int imgWidth, int imgHeight, int digit, float confidence) {
        if (previewView.getWidth() == 0) return;

        float scaleX = (float) previewView.getWidth() / imgWidth;
        float scaleY = (float) previewView.getHeight() / imgHeight;
        float scale = Math.max(scaleX, scaleY);

        float offsetX = (previewView.getWidth() - imgWidth * scale) / 2.0f;
        float offsetY = (previewView.getHeight() - imgHeight * scale) / 2.0f;

        int screenX = (int) (rect.x * scale + offsetX);
        int screenY = (int) (rect.y * scale + offsetY);
        int screenW = (int) (rect.width * scale);
        int screenH = (int) (rect.height * scale);

        reticleView.setVisibility(View.VISIBLE);
        ViewGroup.LayoutParams params = reticleView.getLayoutParams();
        params.width = screenW;
        params.height = screenH;
        reticleView.setLayoutParams(params);
        reticleView.setTranslationX(screenX);
        reticleView.setTranslationY(screenY);

        tvPrediction.setVisibility(View.VISIBLE);
        tvPrediction.setTranslationX(screenX);
        tvPrediction.setTranslationY(screenY + screenH);

        if (confidence > 0.6f) {
            tvPrediction.setText(String.format("%d (%.0f%%)", digit, confidence * 100));
            tvPrediction.setTextColor(0xFF00FF00);
        } else {
            tvPrediction.setText("?");
            tvPrediction.setTextColor(0xFFFF0000);
        }
    }

    private ByteBuffer convertMatToByteBuffer(Mat inputMat) {
        // --- CORRECCIÓN DEL ERROR AQUÍ ---
        // Usamos org.opencv.core.Size explícitamente
        Imgproc.resize(inputMat, resizedMat, new org.opencv.core.Size(MODEL_INPUT_SIZE, MODEL_INPUT_SIZE), 0, 0, Imgproc.INTER_AREA);

        Core.bitwise_not(resizedMat, invertedMat);
        invertedMat.convertTo(floatMat, CvType.CV_32F, 1.0 / 255.0);

        ByteBuffer buffer = ByteBuffer.allocateDirect(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * 4);
        buffer.order(ByteOrder.nativeOrder());
        float[] data = new float[MODEL_INPUT_SIZE * MODEL_INPUT_SIZE];
        floatMat.get(0, 0, data);
        buffer.asFloatBuffer().put(data);
        return buffer;
    }

    private Pair<Integer, Float> runInference(ByteBuffer input) {
        float[][] output = new float[1][10];
        tflite.run(input, output);
        int maxIdx = -1;
        float maxConf = 0;
        for (int i = 0; i < 10; i++) {
            if (output[0][i] > maxConf) {
                maxConf = output[0][i];
                maxIdx = i;
            }
        }
        return new Pair<>(maxIdx, maxConf);
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fd = getAssets().openFd(TFLITE_MODEL_NAME);
        FileInputStream is = new FileInputStream(fd.getFileDescriptor());
        return is.getChannel().map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
    }

    private boolean allPermissionsGranted() {
        for (String p : REQUIRED_PERMISSIONS) if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) return false;
        return true;
    }

    @Override public void onRequestPermissionsResult(int r, @NonNull String[] p, @NonNull int[] g) {
        super.onRequestPermissionsResult(r, p, g);
        if (r == REQUEST_CODE_PERMISSIONS && allPermissionsGranted()) startCamera();
    }

    @Override protected void onDestroy() { super.onDestroy(); cameraExecutor.shutdown(); if(tflite!=null) tflite.close(); }
}