package com.example.miappopencv;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.List;

public class MovementPantiltActivity extends AppCompatActivity {

    private UsbSerialPort port;
    private TextView tvStatus, tvLogs;
    private static final String ACTION_USB_PERMISSION = "com.example.miappopencv.USB_PERMISSION";

    private final Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_movement_pantilt);

        tvStatus = findViewById(R.id.tv_status);
        tvLogs = findViewById(R.id.tv_logs);
        Button btnConnect = findViewById(R.id.btn_connect);
        Button btnPan180 = findViewById(R.id.btn_pan_180);
        Button btnTilt35 = findViewById(R.id.btn_tilt_35);
        Button btnReset = findViewById(R.id.btn_reset_00);

        btnConnect.setOnClickListener(v -> connectUsb());

        btnPan180.setOnClickListener(v -> sendServoCommand(180, 0));
        btnTilt35.setOnClickListener(v -> sendServoCommand(0, 35));
        btnReset.setOnClickListener(v -> sendServoCommand(0, 0));

        log("Listo para pruebas.");
    }

    private void connectUsb() {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);

        if (availableDrivers.isEmpty()) {
            updateStatus("No detectado. ¿Cable OTG conectado?");
            return;
        }

        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());

        if (connection == null) {
            PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
            manager.requestPermission(driver.getDevice(), permissionIntent);
            updateStatus("Solicitando permiso...");
            return;
        }

        port = driver.getPorts().get(0);
        try {
            port.open(connection);
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

            // Activamos las señales de control para que el chip 'escuche'.
            port.setDTR(true);
            port.setRTS(true);

            updateStatus("Conectado: " + driver.getDevice().getProductName());
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            log("Conexión establecida (DTR/RTS activos).");

        } catch (IOException e) {
            updateStatus("Error puerto: " + e.getMessage());
            try {
                port.close();
            } catch (IOException ignored) {}
            port = null;
        }
    }

    private void sendServoCommand(int panAngle, int tiltAngle) {
        // Objeto comando
        RobotCommand command = new RobotCommand(panAngle, tiltAngle);
        String jsonToSend = gson.toJson(command);

        // El salto de linea \n es vital para Arduino
        String dataWithNewline = jsonToSend + "\n";

        if (port != null) {
            try {
                // Escribimos los datos
                port.write(dataWithNewline.getBytes(), 1000);
                log("Enviado: " + jsonToSend); // Mostramos el JSON limpio en log
            } catch (IOException e) {
                log("Error enviando: " + e.getMessage());
                updateStatus("Error escritura");
            }
        } else {
            log("Simulado: " + jsonToSend);
            updateStatus("No conectado");
        }
    }

    private void updateStatus(String status) {
        tvStatus.setText("Estado: " + status);
    }

    private void log(String msg) {
        tvLogs.append(msg + "\n");
        final android.widget.ScrollView scroll = findViewById(R.id.scroll_logs);
        scroll.post(() -> scroll.fullScroll(android.view.View.FOCUS_DOWN));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (port != null) {
            try {
                port.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static class RobotCommand {
        private int pan;
        private int tilt;

        public RobotCommand(int pan, int tilt) {
            this.pan = pan;
            this.tilt = tilt;
        }
    }
}