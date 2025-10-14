package com.example.miappopencv;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

public class CameraActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "OCV_MiApp";
    private static final int CAMERA_PERMISSION_REQUEST = 1;

    private CameraBridgeViewBase mOpenCvCameraView;
    private Mat mRgba;
    private Mat mGray;
    private static boolean isLibraryLoaded = false;

    static {
        if (OpenCVLoader.initDebug()) {
            Log.d(TAG, "¡Éxito! La librería nativa de OpenCV se cargó correctamente.");
            isLibraryLoaded = true;
        } else {
            Log.e(TAG, "¡Error! La librería nativa de OpenCV no se pudo cargar.");
            isLibraryLoaded = false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "CameraActivity: onCreate llamado.");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_camera);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.java_camera_view);

        // --- DEPURACIÓN DE VISTA ---
        // Este bloque nos dirá las dimensiones de la vista una vez que esté lista para ser dibujada.
        mOpenCvCameraView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mOpenCvCameraView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                int width = mOpenCvCameraView.getWidth();
                int height = mOpenCvCameraView.getHeight();
                Log.d(TAG, "CameraActivity: Vista de cámara dibujada con dimensiones: " + width + "x" + height);
                // Si las dimensiones son 0x0, la vista no se está mostrando correctamente.
            }
        });
        // -------------------------

        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        mOpenCvCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK);

        verificarPermisosDeCamara();
    }

    private void verificarPermisosDeCamara() {
        Log.d(TAG, "CameraActivity: Verificando permisos...");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "CameraActivity: Permiso ya concedido. Activando cámara...");
            initializeOpenCV();
        } else {
            Log.d(TAG, "CameraActivity: Solicitando permiso...");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "CameraActivity: onPause llamado.");
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isLibraryLoaded && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "CameraActivity: Reactivando cámara desde onResume...");
            initializeOpenCV();
        } else {
            Log.d(TAG, "CameraActivity: Aún no hay permisos o librería no cargada en onResume.");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "CameraActivity: onDestroy llamado.");
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "CameraActivity: Permiso concedido por el usuario.");
                initializeOpenCV();
            } else {
                Log.w(TAG, "CameraActivity: Permiso denegado por el usuario.");
                Toast.makeText(this, "El permiso de la cámara es necesario.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void initializeOpenCV() {
        if (isLibraryLoaded && mOpenCvCameraView != null) {
            Log.d(TAG, "Intentando activar la vista de la cámara...");
            boolean result = mOpenCvCameraView.enableView();
            Log.d(TAG, "mOpenCvCameraView.enableView() devolvió: " + result);
        } else {
            Log.e(TAG, "CameraActivity: No se puede activar la cámara, librería no cargada o vista no inicializada.");
        }
    }

    public void onCameraViewStarted(int width, int height) {
        Log.d(TAG, "¡VISTA DE CÁMARA INICIADA! con resolución: " + width + "x" + height);
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8UC1);
    }

    public void onCameraViewStopped() {
        Log.d(TAG, "¡VISTA DE CÁMARA DETENIDA!");
        mRgba.release();
        mGray.release();
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        Imgproc.cvtColor(mRgba, mGray, Imgproc.COLOR_RGBA2GRAY);
        return mGray;
    }
}

