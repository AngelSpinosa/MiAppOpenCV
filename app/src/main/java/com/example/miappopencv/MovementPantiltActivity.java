package com.example.miappopencv;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.List;

public class MovementPantiltActivity extends AppCompatActivity {

    private UsbSerialPort port;
    private TextView tvStatus, tvPanLabel, tvTiltLabel;
    private SeekBar seekPan, seekTilt;

    private static final String ACTION_USB_PERMISSION = "com.example.miappopencv.USB_PERMISSION";
    private static final String TAG = "RobotControl";

    private final Gson gson = new Gson();

    // Límites de seguridad físicos
    private static final int LIMIT_YAW_MIN = -180;
    private static final int LIMIT_YAW_MAX = 180;
    private static final int LIMIT_PITCH_MIN = -30;
    private static final int LIMIT_PITCH_MAX = 90;

    // Variables para control de flujo (Throttling)
    private long lastSendTime = 0;
    private static final int SEND_INTERVAL_MS = 50; // Enviar máximo cada 50ms para no saturar

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_movement_pantilt);

        // Referencias UI
        tvStatus = findViewById(R.id.tv_status);
        tvPanLabel = findViewById(R.id.tv_pan_label);
        tvTiltLabel = findViewById(R.id.tv_tilt_label);

        seekPan = findViewById(R.id.seek_pan);
        seekTilt = findViewById(R.id.seek_tilt);

        Button btnConnect = findViewById(R.id.btn_connect);
        Button btnCenter = findViewById(R.id.btn_center);

        // Configuración inicial de Sliders
        // PAN: Rango -180 a 180. Offset lógico = 180.
        // SeekBar va de 0 a 360. 180 es el centro (0 grados).
        seekPan.setMax(360);
        seekPan.setProgress(180);

        // TILT: Rango -30 a 90. Offset lógico = 30.
        // SeekBar va de 0 a 120. 30 es el centro (0 grados).
        seekTilt.setMax(120);
        seekTilt.setProgress(30);

        // Listeners
        btnConnect.setOnClickListener(v -> connectUsb());

        // Listener para PAN
        seekPan.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Matemática: Si progress es 180 -> ángulo es 0.
                // Si progress es 0 -> ángulo es -180.
                int angle = progress - 180;
                tvPanLabel.setText("PAN: " + angle + "°");

                // Obtenemos el tilt actual del otro slider para no moverlo
                int currentTiltAngle = seekTilt.getProgress() - 30;

                // Enviar con control de flujo
                sendThrottleCommand(angle, currentTiltAngle);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                // Al soltar, aseguramos enviar la última posición exacta
                int angle = seekBar.getProgress() - 180;
                int currentTiltAngle = seekTilt.getProgress() - 30;
                sendServoCommand(angle, currentTiltAngle);
            }
        });

        // Listener para TILT
        seekTilt.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Matemática: Si progress es 30 -> ángulo es 0.
                // Si progress es 0 -> ángulo es -30.
                int angle = progress - 30;
                tvTiltLabel.setText("TILT: " + angle + "°");

                int currentPanAngle = seekPan.getProgress() - 180;

                sendThrottleCommand(currentPanAngle, angle);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                int angle = seekBar.getProgress() - 30;
                int currentPanAngle = seekPan.getProgress() - 180;
                sendServoCommand(currentPanAngle, angle);
            }
        });

        // Botón Centrar
        btnCenter.setOnClickListener(v -> {
            seekPan.setProgress(180); // Físicamente 0°
            seekTilt.setProgress(30); // Físicamente 0°
            sendServoCommand(0, 0);
        });
    }

    // Función inteligente que evita saturar el puerto USB
    private void sendThrottleCommand(int pan, int tilt) {
        long currentTime = System.currentTimeMillis();
        // Solo enviamos si han pasado X ms desde la última vez
        if (currentTime - lastSendTime > SEND_INTERVAL_MS) {
            sendServoCommand(pan, tilt);
            lastSendTime = currentTime;
        }
    }

    private void connectUsb() {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);

        if (availableDrivers.isEmpty()) {
            updateStatus("No detectado");
            return;
        }

        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());

        if (connection == null) {
            PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
            manager.requestPermission(driver.getDevice(), permissionIntent);
            updateStatus("Pidiendo permiso...");
            return;
        }

        port = driver.getPorts().get(0);
        try {
            port.open(connection);
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            port.setDTR(true);
            port.setRTS(true);

            updateStatus("Conectado: " + driver.getDevice().getProductName());
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));

        } catch (IOException e) {
            updateStatus("Error: " + e.getMessage());
            try { port.close(); } catch (IOException ignored) {}
            port = null;
        }
    }

    private void sendServoCommand(int targetPan, int targetTilt) {
        // Clamping de seguridad
        int safePan = Math.max(LIMIT_YAW_MIN, Math.min(targetPan, LIMIT_YAW_MAX));
        int safeTilt = Math.max(LIMIT_PITCH_MIN, Math.min(targetTilt, LIMIT_PITCH_MAX));

        // Estructura para Waveshare
        RobotCommand command = new RobotCommand(133, safePan, safeTilt, 0, 0);
        String jsonString = gson.toJson(command);
        String finalData = jsonString + "\n";

        Log.d(TAG, "Enviando: " + finalData);

        if (port != null) {
            try {
                port.write(finalData.getBytes(), 1000);
            } catch (IOException e) {
                Log.e(TAG, "Error enviando: " + e.getMessage());
                updateStatus("Error escritura");
            }
        }
    }

    private void updateStatus(String status) {
        tvStatus.setText("Estado: " + status);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (port != null) {
            try { port.close(); } catch (IOException e) { e.printStackTrace(); }
        }
    }

    private static class RobotCommand {
        @SerializedName("T") private int type;
        @SerializedName("X") private int yaw;
        @SerializedName("Y") private int pitch;
        @SerializedName("SPD") private int speed;
        @SerializedName("ACC") private int acceleration;

        public RobotCommand(int type, int yaw, int pitch, int speed, int acceleration) {
            this.type = type;
            this.yaw = yaw;
            this.pitch = pitch;
            this.speed = speed;
            this.acceleration = acceleration;
        }
    }
}