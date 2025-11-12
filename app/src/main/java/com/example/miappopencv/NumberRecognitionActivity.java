package com.example.miappopencv;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class NumberRecognitionActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_number_recognition);

        // Mensaje temporal para saber que estamos en la actividad correcta
        Toast.makeText(this, "Actividad del Modelo Tiny - Por Implementar", Toast.LENGTH_LONG).show();

        // Aquí es donde, en el futuro, configurarás CameraX
        // y tu analizador de TFLite para el reconocimiento en tiempo real.
    }
}