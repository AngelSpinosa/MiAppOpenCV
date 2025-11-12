package com.example.miappopencv;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Referencia al botón del filtro OpenCV
        Button btnLaunchOpenCVFilter = findViewById(R.id.btn_launch_opencv_filter);
        btnLaunchOpenCVFilter.setOnClickListener(v -> {
            // Lanza la CameraActivity existente
            Intent intent = new Intent(MainActivity.this, CameraActivity.class);
            startActivity(intent);
        });

        // Referencia al botón del reconocedor de números
        Button btnLaunchNumberRecognition = findViewById(R.id.btn_launch_number_recognition);
        btnLaunchNumberRecognition.setOnClickListener(v -> {
            // Lanza la NUEVA NumberRecognitionActivity
            Intent intent = new Intent(MainActivity.this, NumberRecognitionActivity.class);
            startActivity(intent);
        });
    }
}