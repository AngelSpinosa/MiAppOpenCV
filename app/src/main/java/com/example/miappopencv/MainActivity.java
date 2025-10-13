package com.example.miappopencv;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
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

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {

    private static final String TAG = "OCV_MiApp";
    private static final int CAMERA_PERMISSION_REQUEST = 1;

    private CameraBridgeViewBase mOpenCvCameraView;
    private Mat mRgba;
    private Mat mGray;
    private static boolean isLibraryLoaded = false;

    // --- BLOQUE DE INICIALIZACIÓN ESTÁTICO ---
    static {
        if (OpenCVLoader.initDebug()) {
            Log.d(TAG, "¡Éxito! La librería nativa de OpenCV se cargó correctamente.");
            isLibraryLoaded = true;
        } else {
            Log.e(TAG, "¡Error! La librería nativa de OpenCV no se pudo cargar.");
            isLibraryLoaded = false;
        }
    }
    // -----------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "Método onCreate llamado.");
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.camera_view);

        // --- CAMBIO CLAVE: Pedimos explícitamente la cámara trasera ---
        mOpenCvCameraView.setCameraIndex(CameraBridgeViewBase.CAMERA_ID_BACK);
        // -----------------------------------------------------------

        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        verificarPermisosDeCamara();
    }

    private void verificarPermisosDeCamara() {
        Log.d(TAG, "Verificando permisos de cámara...");
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permiso ya concedido. Activando la cámara...");
            initializeOpenCV();
        } else {
            Log.d(TAG, "Permiso no concedido. Solicitándolo...");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "Método onPause llamado.");
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(isLibraryLoaded && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED){
            Log.d(TAG, "Permiso concedido. Reactivando la cámara desde onResume...");
            mOpenCvCameraView.enableView();
        } else {
            Log.d(TAG, "Aún no hay permisos de cámara en onResume.");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Método onDestroy llamado.");
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Log.d(TAG, "onRequestPermissionsResult llamado.");
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "El usuario concedió el permiso de cámara. Activando la cámara...");
                initializeOpenCV();
            } else {
                Log.w(TAG, "El usuario denegó el permiso de cámara.");
                Toast.makeText(this, "El permiso de la cámara es necesario para esta aplicación.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void initializeOpenCV() {
        if(isLibraryLoaded) {
            mOpenCvCameraView.enableView();
        } else {
            Log.e(TAG, "No se puede activar la cámara porque la librería de OpenCV no se cargó.");
        }
    }

    public void onCameraViewStarted(int width, int height) {
        Log.d(TAG, "¡Vista de cámara iniciada!");
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mGray = new Mat(height, width, CvType.CV_8UC1);
    }

    public void onCameraViewStopped() {
        Log.d(TAG, "¡Vista de cámara detenida!");
        mRgba.release();
        mGray.release();
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        // Corrección del error de tipeo: Improc -> Imgproc
        Imgproc.cvtColor(mRgba, mGray, Imgproc.COLOR_RGBA2GRAY);
        return mGray;
    }
}

