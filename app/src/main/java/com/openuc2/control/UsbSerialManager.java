package com.openuc2.control;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Wraps usb-serial-for-android. Provides connect/disconnect, baud-rate change,
 * write, and a listener interface for incoming bytes and connection-state changes.
 *
 * Designed to be the only class that touches USB or threads — the UI layer just
 * calls connect()/sendCommand()/disconnect() and listens for callbacks on the
 * main thread.
 */
public class UsbSerialManager implements SerialInputOutputManager.Listener {

    private static final String TAG = "UsbSerialManager";
    private static final String ACTION_USB_PERMISSION = "com.openuc2.control.USB_PERMISSION";

    public interface Listener {
        void onSerialConnected(String deviceName);
        void onSerialDisconnected();
        void onSerialDataReceived(String data);
        void onSerialError(String message);
        void onSerialLog(String message);
    }

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private Listener listener;
    private UsbSerialPort port;
    private SerialInputOutputManager ioManager;
    private int currentBaud = 115200;

    private BroadcastReceiver permissionReceiver;
    private boolean permissionReceiverRegistered = false;

    public UsbSerialManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public boolean isConnected() {
        return port != null && port.isOpen();
    }

    public int getCurrentBaud() {
        return currentBaud;
    }

    /** Look for a connected USB device that matches a known serial driver. */
    public UsbDevice findFirstDevice() {
        UsbManager usbManager = (UsbManager) appContext.getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (drivers.isEmpty()) return null;
        return drivers.get(0).getDevice();
    }

    /**
     * Top-level connect entry point. Finds the first available serial device,
     * requests permission if needed, and opens the port at the given baud rate.
     */
    public void connect(int baudRate) {
        if (isConnected()) {
            log("Already connected");
            return;
        }
        currentBaud = baudRate;

        UsbManager usbManager = (UsbManager) appContext.getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);

        if (drivers.isEmpty()) {
            error("No USB serial device found. Plug in your ESP32 via OTG and try again.");
            return;
        }

        UsbSerialDriver driver = drivers.get(0);
        UsbDevice device = driver.getDevice();
        log("Found device: " + device.getDeviceName()
                + " (VID=0x" + Integer.toHexString(device.getVendorId())
                + " PID=0x" + Integer.toHexString(device.getProductId()) + ")");

        if (!usbManager.hasPermission(device)) {
            requestPermission(usbManager, driver, baudRate);
            return;
        }

        openDriver(usbManager, driver, baudRate);
    }

    private void requestPermission(UsbManager usbManager, UsbSerialDriver driver, int baudRate) {
        log("Requesting USB permission...");

        permissionReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!ACTION_USB_PERMISSION.equals(intent.getAction())) return;
                synchronized (this) {
                    boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    if (granted) {
                        openDriver(usbManager, driver, baudRate);
                    } else {
                        error("USB permission denied");
                    }
                    unregisterPermissionReceiver();
                }
            }
        };

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        int flags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            flags = PendingIntent.FLAG_MUTABLE;
        }
        PendingIntent pi = PendingIntent.getBroadcast(
                appContext, 0, new Intent(ACTION_USB_PERMISSION).setPackage(appContext.getPackageName()), flags);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            appContext.registerReceiver(permissionReceiver, filter);
        }
        permissionReceiverRegistered = true;

        usbManager.requestPermission(driver.getDevice(), pi);
    }

    private void unregisterPermissionReceiver() {
        if (permissionReceiverRegistered && permissionReceiver != null) {
            try {
                appContext.unregisterReceiver(permissionReceiver);
            } catch (IllegalArgumentException ignored) { }
            permissionReceiverRegistered = false;
        }
    }

    private void openDriver(UsbManager usbManager, UsbSerialDriver driver, int baudRate) {
        ioExecutor.submit(() -> {
            try {
                UsbDeviceConnection connection = usbManager.openDevice(driver.getDevice());
                if (connection == null) {
                    error("Could not open USB connection");
                    return;
                }

                List<UsbSerialPort> ports = driver.getPorts();
                if (ports.isEmpty()) {
                    error("Device has no serial ports");
                    return;
                }

                UsbSerialPort newPort = ports.get(0);
                newPort.open(connection);
                newPort.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                try {
                    newPort.setDTR(true);
                    newPort.setRTS(true);
                } catch (Exception e) {
                    // some chips don't support this — non-fatal
                }

                this.port = newPort;
                this.currentBaud = baudRate;

                ioManager = new SerialInputOutputManager(newPort, this);
                ioManager.start();

                final String name = driver.getDevice().getDeviceName();
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onSerialLog("Connected at " + baudRate + " baud");
                        listener.onSerialConnected(name);
                    }
                });
            } catch (IOException e) {
                error("Open failed: " + e.getMessage());
            } catch (Exception e) {
                error("Unexpected error: " + e.getMessage());
            }
        });
    }

    public void disconnect() {
        ioExecutor.submit(() -> {
            try {
                if (ioManager != null) {
                    ioManager.setListener(null);
                    ioManager.stop();
                    ioManager = null;
                }
                if (port != null) {
                    try { port.close(); } catch (IOException ignored) { }
                    port = null;
                }
                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onSerialLog("Disconnected");
                        listener.onSerialDisconnected();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Disconnect error", e);
            }
        });
    }

    /** Re-open the existing port at a new baud rate. */
    public void changeBaud(int newBaud) {
        if (port == null || !port.isOpen()) {
            error("Not connected — cannot change baud rate");
            return;
        }
        ioExecutor.submit(() -> {
            try {
                port.setParameters(newBaud, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                currentBaud = newBaud;
                mainHandler.post(() -> log("Baud rate changed to " + newBaud));
            } catch (IOException e) {
                error("Baud change failed: " + e.getMessage());
            }
        });
    }

    /** Send a string command. A trailing newline is added automatically. */
    public void sendCommand(String command) {
        if (port == null || !port.isOpen()) {
            error("Not connected");
            return;
        }
        final String cmd = command.endsWith("\n") ? command : command + "\n";
        ioExecutor.submit(() -> {
            try {
                port.write(cmd.getBytes(), 1000);
                mainHandler.post(() -> {
                    if (listener != null) listener.onSerialLog("> " + command);
                });
            } catch (IOException e) {
                error("Write failed: " + e.getMessage());
            }
        });
    }

    public void release() {
        unregisterPermissionReceiver();
        disconnect();
        ioExecutor.shutdown();
    }

    // === SerialInputOutputManager.Listener ===

    @Override
    public void onNewData(byte[] data) {
        final String s = new String(data);
        mainHandler.post(() -> {
            if (listener != null) listener.onSerialDataReceived(s);
        });
    }

    @Override
    public void onRunError(Exception e) {
        error("Run error: " + e.getMessage());
        mainHandler.post(() -> {
            if (listener != null) listener.onSerialDisconnected();
        });
    }

    // === helpers ===

    private void log(String s) {
        Log.d(TAG, s);
        mainHandler.post(() -> {
            if (listener != null) listener.onSerialLog(s);
        });
    }

    private void error(String s) {
        Log.e(TAG, s);
        mainHandler.post(() -> {
            if (listener != null) listener.onSerialError(s);
        });
    }
}
