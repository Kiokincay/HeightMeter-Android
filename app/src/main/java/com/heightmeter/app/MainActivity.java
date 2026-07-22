package com.heightmeter.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1201;
    private static final UUID SERIAL_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private WebView webView;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket bluetoothSocket;
    private OutputStream bluetoothOutput;
    private final ExecutorService ioExecutor = Executors.newCachedThreadPool();
    private volatile boolean readerRunning = false;
    private boolean waitingForBluetoothSettings = false;
    private boolean webReady = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                webReady = true;
                sendToWeb("STATUS|READY|Android Bluetooth bridge active");
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForBluetoothSettings) {
            waitingForBluetoothSettings = false;
            webView.postDelayed(this::showPairedDevicePicker, 450);
        }
    }

    @Override
    protected void onDestroy() {
        disconnectBluetooth(false);
        ioExecutor.shutdownNow();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }

    private boolean hasBluetoothPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(new String[]{
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
            }, REQUEST_BLUETOOTH_PERMISSIONS);
        } else {
            openBluetoothSettings();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            boolean granted = grantResults.length > 0;
            for (int result : grantResults) granted &= result == PackageManager.PERMISSION_GRANTED;
            if (granted) openBluetoothSettings();
            else sendToWeb("ERROR|Bluetooth permission is required to connect to HC-05 or HC-06.");
        }
    }

    private void startManualConnectionFlow() {
        if (bluetoothAdapter == null) {
            sendToWeb("ERROR|This Android device does not support Bluetooth.");
            return;
        }
        if (!hasBluetoothPermission()) {
            requestBluetoothPermissions();
            return;
        }
        openBluetoothSettings();
    }

    private void openBluetoothSettings() {
        waitingForBluetoothSettings = true;
        sendToWeb("STATUS|PAIRING|Open Android Bluetooth settings and pair HC-05 or HC-06.");
        try {
            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        } catch (Exception error) {
            waitingForBluetoothSettings = false;
            sendToWeb("ERROR|Could not open Android Bluetooth settings.");
        }
    }

    private void showPairedDevicePicker() {
        if (!hasBluetoothPermission()) {
            requestBluetoothPermissions();
            return;
        }
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            sendToWeb("ERROR|Turn on Bluetooth, then press Connect again.");
            return;
        }

        Set<BluetoothDevice> bonded = bluetoothAdapter.getBondedDevices();
        List<BluetoothDevice> devices = new ArrayList<>();
        for (BluetoothDevice device : bonded) {
            String name = safeDeviceName(device);
            String upper = name.toUpperCase(Locale.ROOT).replace("_", "-");
            if (upper.contains("HC-05") || upper.contains("HC-06") || upper.contains("HC05") || upper.contains("HC06")) {
                devices.add(device);
            }
        }
        devices.sort(Comparator.comparing(this::safeDeviceName, String.CASE_INSENSITIVE_ORDER));

        if (devices.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("No paired height sensor")
                    .setMessage("Pair HC-05 or HC-06 in Android Bluetooth settings first. The app will never connect to a random device.")
                    .setPositiveButton("Open Bluetooth settings", (dialog, which) -> openBluetoothSettings())
                    .setNegativeButton("Cancel", null)
                    .show();
            sendToWeb("STATUS|DISCONNECTED|No paired HC-05 or HC-06 found.");
            return;
        }

        String[] labels = new String[devices.size()];
        for (int i = 0; i < devices.size(); i++) {
            labels[i] = safeDeviceName(devices.get(i)) + "\n" + devices.get(i).getAddress();
        }

        new AlertDialog.Builder(this)
                .setTitle("Select your height sensor")
                .setItems(labels, (dialog, index) -> connectToDevice(devices.get(index)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String safeDeviceName(BluetoothDevice device) {
        try {
            String name = device.getName();
            return name == null || name.trim().isEmpty() ? "Unnamed Bluetooth device" : name.trim();
        } catch (SecurityException error) {
            return "Bluetooth device";
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        disconnectBluetooth(false);
        String name = safeDeviceName(device);
        sendToWeb("STATUS|CONNECTING|" + sanitize(name));

        ioExecutor.execute(() -> {
            try {
                if (!hasBluetoothPermission()) throw new SecurityException("Bluetooth permission denied");
                bluetoothAdapter.cancelDiscovery();
                BluetoothSocket socket = device.createRfcommSocketToServiceRecord(SERIAL_UUID);
                socket.connect();
                bluetoothSocket = socket;
                bluetoothOutput = socket.getOutputStream();
                readerRunning = true;
                sendToWeb("STATUS|CONNECTED|" + sanitize(name));
                readBluetoothLoop(socket, name);
            } catch (Exception error) {
                disconnectBluetooth(false);
                sendToWeb("ERROR|Could not connect to " + sanitize(name) + ". Confirm it is paired and not connected to another app.");
            }
        });
    }

    private void readBluetoothLoop(BluetoothSocket socket, String deviceName) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (readerRunning && socket.isConnected() && (line = reader.readLine()) != null) {
                String clean = line.trim();
                if (!clean.isEmpty()) sendToWeb("DATA|" + sanitize(clean));
            }
        } catch (IOException ignored) {
            // A closed socket naturally exits here.
        } finally {
            if (readerRunning) {
                disconnectBluetooth(false);
                sendToWeb("STATUS|DISCONNECTED|Connection to " + sanitize(deviceName) + " was closed.");
            }
        }
    }

    private void sendBluetoothCommand(String command) {
        if (bluetoothSocket == null || bluetoothOutput == null || !bluetoothSocket.isConnected()) {
            sendToWeb("ERROR|Connect HC-05 or HC-06 before sending a command.");
            return;
        }
        ioExecutor.execute(() -> {
            try {
                String clean = command == null ? "" : command.trim();
                if (clean.isEmpty()) return;
                bluetoothOutput.write((clean + "\n").getBytes(StandardCharsets.UTF_8));
                bluetoothOutput.flush();
                sendToWeb("SENT|" + sanitize(clean));
            } catch (IOException error) {
                disconnectBluetooth(false);
                sendToWeb("ERROR|Bluetooth command failed. Reconnect the sensor.");
            }
        });
    }

    private synchronized void disconnectBluetooth(boolean notifyWeb) {
        readerRunning = false;
        bluetoothOutput = null;
        if (bluetoothSocket != null) {
            try { bluetoothSocket.close(); } catch (IOException ignored) {}
            bluetoothSocket = null;
        }
        if (notifyWeb) sendToWeb("STATUS|DISCONNECTED|Bluetooth disconnected.");
    }

    private String sanitize(String value) {
        return value == null ? "" : value.replace("|", "/").replace("\r", " ").replace("\n", " ");
    }

    private void sendToWeb(String message) {
        runOnUiThread(() -> {
            if (!webReady || webView == null) return;
            String quoted = JSONObject.quote(message);
            webView.evaluateJavascript(
                    "window.HeightMeterNative && window.HeightMeterNative.receive(" + quoted + ");",
                    null
            );
        });
    }

    public class AndroidBridge {
        @JavascriptInterface
        public void postMessage(String message) {
            if (message == null) return;
            String clean = message.trim();
            if (clean.equalsIgnoreCase("CONNECT")) {
                runOnUiThread(MainActivity.this::startManualConnectionFlow);
            } else if (clean.equalsIgnoreCase("DISCONNECT")) {
                disconnectBluetooth(true);
            } else {
                sendBluetoothCommand(clean);
            }
        }

        @JavascriptInterface
        public String platform() {
            return "Android";
        }
    }
}
