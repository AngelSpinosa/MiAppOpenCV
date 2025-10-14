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

        Button btnAbrirCamara = findViewById(R.id.btnAbrirCamara);
        btnAbrirCamara.setOnClickListener(v -> {
            // Inicia la nueva CameraActivity
            startActivity(new Intent(MainActivity.this, CameraActivity.class));
        });
    }
}

