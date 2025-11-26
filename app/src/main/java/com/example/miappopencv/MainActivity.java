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

        // --- CÓDIGO DEL FILTRO B/N ELIMINADO ---
        // Se ha eliminado el listener del botón 'btn_launch_opencv_filter'
        // para quitar el acceso a CameraActivity desde la UI.
        // La clase CameraActivity sigue existiendo en el proyecto para referencia futura.
        //Referencia al boton de controles para el movimiento del servo
        // Referencia al botón del reconocedor de números
        Button btnLaunchMovementPantilt = findViewById(R.id.btn_launch_movement_pantilt);
        btnLaunchMovementPantilt.setOnClickListener(v -> {
            // Lanza la actividad de reconocimiento
            Intent intent = new Intent(MainActivity.this, MovementPantiltActivity.class);
            startActivity(intent);
        });


        // Referencia al botón del reconocedor de números
        Button btnLaunchNumberRecognition = findViewById(R.id.btn_launch_number_recognition);
        btnLaunchNumberRecognition.setOnClickListener(v -> {
            // Lanza la actividad de reconocimiento
            Intent intent = new Intent(MainActivity.this, NumberRecognitionActivity.class);
            startActivity(intent);
        });
    }
}