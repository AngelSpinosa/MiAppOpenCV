package com.example.miappopencv;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraInfo;
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
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NumberRecognitionActivity extends AppCompatActivity {

    private static final String TAG = "TFLite_Activity";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private final String[] REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};

    // --- Vistas de Layout ---
    private PreviewView previewView;
    private TextView tvPrediction;

    // --- CameraX ---
    private ExecutorService cameraExecutor;

    // --- TFLite ---
    private Interpreter tflite;
    private static final String TFLITE_MODEL_NAME = "Modelo_mejorado.tflite";
    private static final int MODEL_INPUT_WIDTH = 28;
    private static final int MODEL_INPUT_HEIGHT = 28;

    // --- OpenCV (para pre-procesamiento) ---
    private Mat yuvMat, grayMat, resizedMat, invertedMat, floatMat;

    // Cargar OpenCV (¡necesario para el pre-procesamiento!)
    static {
        if (OpenCVLoader.initDebug()) {
            Log.d(TAG, "OpenCV cargado exitosamente.");
        } else {
            Log.e(TAG, "Fallo al cargar OpenCV.");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_number_recognition);

        previewView = findViewById(R.id.tflite_preview_view);
        tvPrediction = findViewById(R.id.tv_prediction);
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Inicializar el intérprete de TFLite
        try {
            tflite = new Interpreter(loadModelFile());
            Log.d(TAG, "Intérprete de TFLite cargado.");
        } catch (IOException e) {
            Log.e(TAG, "Error al cargar el modelo de TFLite.", e);
            Toast.makeText(this, "Error al cargar modelo.", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Pedir permisos de cámara
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .addCameraFilter(cameraInfos -> {
                            for (CameraInfo cameraInfo : cameraInfos) {
                                Integer lensFacing = cameraInfo.getLensFacing();
                                if (lensFacing != null && lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    return Collections.singletonList(cameraInfo);
                                }
                            }
                            return Collections.emptyList();
                        })
                        .build();

                // Configurar el analizador de imagen
                ImageAnalysis imageAnalyzer = new ImageAnalysis.Builder()
                        // Usar una resolución cercana a la del modelo si es posible,
                        // pero 640x480 es estándar y funciona bien.
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalyzer.setAnalyzer(cameraExecutor, new TFLiteAnalyzer());

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer);
                Log.d(TAG, "CameraX iniciado y enlazado.");

            } catch (Exception e) {
                Log.e(TAG, "Fallo al iniciar o enlazar CameraX.", e);
                runOnUiThread(() -> Toast.makeText(this, "No se pudo iniciar la cámara.", Toast.LENGTH_LONG).show());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // --- Clase Analizadora (El "Puente") ---
    private class TFLiteAnalyzer implements ImageAnalysis.Analyzer {

        @Override
        public void analyze(@NonNull ImageProxy image) {
            // Inicializar Mats si es la primera vez
            if (yuvMat == null) {
                // El formato es YUV_420_888, que tiene 1.5 bytes por píxel
                yuvMat = new Mat(image.getHeight() + image.getHeight() / 2, image.getWidth(), CvType.CV_8UC1);
                grayMat = new Mat();
                resizedMat = new Mat(MODEL_INPUT_WIDTH, MODEL_INPUT_HEIGHT, CvType.CV_8UC1);
                invertedMat = new Mat(MODEL_INPUT_WIDTH, MODEL_INPUT_HEIGHT, CvType.CV_8UC1);
                floatMat = new Mat(MODEL_INPUT_WIDTH, MODEL_INPUT_HEIGHT, CvType.CV_32F);
            }

            // 1. Convertir ImageProxy (YUV) a Mat (YUV)
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();
            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();
            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);
            yuvMat.put(0, 0, nv21);

            // 2. Convertir Mat (YUV) a Mat (Escala de Grises)
            // (COLOR_YUV2GRAY_NV21 es más directo que pasar por RGBA)
            Imgproc.cvtColor(yuvMat, grayMat, Imgproc.COLOR_YUV2GRAY_NV21);

            // 3. Aplicar rotación
            // (Obtenemos la rotación de la cámara y la aplicamos al Mat)
            int rotationDegrees = image.getImageInfo().getRotationDegrees();
            if (rotationDegrees != 0) {
                Core.rotate(grayMat, grayMat, getRotationConstant(rotationDegrees));
            }

            // 4. Pre-procesar para el modelo TFLite
            ByteBuffer inputBuffer = convertMatToByteBuffer(grayMat);

            // 5. Correr Inferencia
            Pair<Integer, Float> result = runInference(inputBuffer);

            // 6. Mostrar Resultado en el Hilo Principal
            int predictedDigit = result.first;
            float confidence = result.second * 100.0f; // Convertir a porcentaje

            runOnUiThread(() -> {
                tvPrediction.setText(String.format("Dígito: %d\nConfianza: %.2f%%", predictedDigit, confidence));
            });

            // ¡MUY IMPORTANTE! Cerrar la imagen para que venga la siguiente.
            image.close();
        }
    }

    /**
     * Pre-procesa el Mat de OpenCV y lo convierte en el ByteBuffer
     * que el modelo TFLite espera.
     *
     * @param inputMat Mat de entrada (escala de grises, tamaño completo)
     * @return ByteBuffer listo para la inferencia.
     */
    private ByteBuffer convertMatToByteBuffer(Mat inputMat) {
        // 1. Redimensionar el Mat al tamaño del modelo (28x28)
        // INTER_AREA es bueno para reducir tamaño.
        Imgproc.resize(inputMat, resizedMat, resizedMat.size(), 0, 0, Imgproc.INTER_AREA);

        // 2. Invertir la imagen (de negro-sobre-blanco a blanco-sobre-negro)
        // El modelo de "Tiny" (y MNIST) espera 1.0f - (pixel)
        // Core.subtract(new Scalar(255), resizedMat, invertedMat); <-- REEMPLAZAR ESTA LÍNEA
        Core.bitwise_not(resizedMat, invertedMat); // <-- CON ESTA LÍNEA

        // 3. Normalizar a float (0.0 - 1.0)
        // Convierte el Mat de 8-bit (0-255) a 32-bit float (0.0-1.0)
        invertedMat.convertTo(floatMat, CvType.CV_32F, 1.0 / 255.0);

        // 4. Crear el ByteBuffer
        int bufferSize = MODEL_INPUT_WIDTH * MODEL_INPUT_HEIGHT * 4; // 4 bytes por float
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(bufferSize);
        inputBuffer.order(ByteOrder.nativeOrder());

        // 5. Copiar los datos del Mat al ByteBuffer
        // (Es más rápido copiar a un array y luego al buffer)
        float[] floatArray = new float[MODEL_INPUT_WIDTH * MODEL_INPUT_HEIGHT];
        floatMat.get(0, 0, floatArray);
        inputBuffer.asFloatBuffer().put(floatArray);

        return inputBuffer;
    }

    /**
     * Corre la inferencia de TFLite.
     * (Traducción de la función "inferencia" de Kotlin a Java)
     *
     * @param inputBuffer El ByteBuffer pre-procesado.
     * @return Un Par (Pair) que contiene el dígito (Índice) y la confianza (Valor).
     */
    private Pair<Integer, Float> runInference(ByteBuffer inputBuffer) {
        // El modelo tiene 1 salida (un array de 10 floats)
        float[][] output = new float[1][10];

        // Correr el modelo
        tflite.run(inputBuffer, output);

        // Encontrar el índice con la probabilidad más alta
        int maxIndex = -1;
        float maxConfidence = 0.0f;

        for (int i = 0; i < output[0].length; i++) {
            if (output[0][i] > maxConfidence) {
                maxConfidence = output[0][i];
                maxIndex = i;
            }
        }

        return new Pair<>(maxIndex, maxConfidence);
    }

    /**
     * Carga el archivo del modelo .tflite desde la carpeta /assets.
     * (Traducción de la función "cargarModelo" de Kotlin a Java)
     */
    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = this.getAssets().openFd(TFLITE_MODEL_NAME);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    /**
     * Ayudante para convertir grados de rotación a constantes de OpenCV.
     */
    private int getRotationConstant(int rotationDegrees) {
        switch (rotationDegrees) {
            case 90:
                return Core.ROTATE_90_CLOCKWISE;
            case 180:
                return Core.ROTATE_180;
            case 270:
                return Core.ROTATE_90_COUNTERCLOCKWISE;
            default:
                return -1; // No rotar
        }
    }

    // --- Lógica de Permisos (copiada de tu CameraActivity) ---
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Permisos no concedidos por el usuario.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (tflite != null) {
            tflite.close();
        }
    }
}