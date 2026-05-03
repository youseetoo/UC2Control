package com.openuc2.control;

import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.snackbar.Snackbar;

public class MainActivity extends AppCompatActivity implements UsbSerialManager.Listener {

    private UsbSerialManager serial;

    // top bar
    private Button connectBtn;
    private Button changeBaudBtn;
    private Spinner baudSpinner;
    private TextView statusText;

    // jog
    private EditText stepSizeXY;
    private EditText stepSizeZ;
    private EditText stepSizeA;
    private EditText speedInput;

    // lasers
    private SeekBar[] laserSeek = new SeekBar[4];
    private TextView[] laserVal = new TextView[4];

    // console
    private TextView consoleText;
    private EditText cmdInput;

    // baud values matching the spinner entries
    private static final int[] BAUD_VALUES = {9600, 115200, 921600, 250000, 500000, 1000000, 2000000};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        serial = new UsbSerialManager(this);
        serial.setListener(this);

        bindViews();
        wireTopBar();
        wireJogControls();
        wireLaserControls();
        wireLedControls();
        wireMotorEnable();
        wireHoming();
        wireConsole();

        setUiEnabled(false);

        // Auto-connect if launched by USB attach intent
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
            // Slight delay so the device fully enumerates
            consoleText.post(() -> {
                if (!serial.isConnected()) {
                    int baud = currentBaud();
                    serial.connect(baud);
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        if (serial != null) serial.release();
        super.onDestroy();
    }

    // ============================================================
    // View wiring
    // ============================================================

    private void bindViews() {
        connectBtn   = findViewById(R.id.btn_connect);
        changeBaudBtn = findViewById(R.id.btn_change_baud);
        baudSpinner  = findViewById(R.id.spinner_baud);
        statusText   = findViewById(R.id.tv_status);

        stepSizeXY = findViewById(R.id.et_step_xy);
        stepSizeZ  = findViewById(R.id.et_step_z);
        stepSizeA  = findViewById(R.id.et_step_a);
        speedInput = findViewById(R.id.et_speed);

        laserSeek[0] = findViewById(R.id.seek_laser0);
        laserSeek[1] = findViewById(R.id.seek_laser1);
        laserSeek[2] = findViewById(R.id.seek_laser2);
        laserSeek[3] = findViewById(R.id.seek_laser3);
        laserVal[0]  = findViewById(R.id.tv_laser0_val);
        laserVal[1]  = findViewById(R.id.tv_laser1_val);
        laserVal[2]  = findViewById(R.id.tv_laser2_val);
        laserVal[3]  = findViewById(R.id.tv_laser3_val);

        consoleText = findViewById(R.id.tv_console);
        consoleText.setMovementMethod(new ScrollingMovementMethod());
        cmdInput = findViewById(R.id.et_cmd);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.baud_rates, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        baudSpinner.setAdapter(adapter);
        baudSpinner.setSelection(1); // default 115200
    }

    private int currentBaud() {
        int idx = baudSpinner.getSelectedItemPosition();
        if (idx < 0 || idx >= BAUD_VALUES.length) return 115200;
        return BAUD_VALUES[idx];
    }

    private void wireTopBar() {
        connectBtn.setOnClickListener(v -> {
            if (serial.isConnected()) {
                serial.disconnect();
            } else {
                serial.connect(currentBaud());
            }
        });

        changeBaudBtn.setOnClickListener(v -> {
            if (serial.isConnected()) {
                serial.changeBaud(currentBaud());
            } else {
                toast("Connect first");
            }
        });

        // Optional: react when user changes baud while connected — they need to press Change to apply
        baudSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) { }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }

    private void wireJogControls() {
        // X
        findViewById(R.id.btn_x_plus).setOnClickListener(v ->
                send(UC2Commands.motorStep(UC2Commands.STEPPER_X, getStep(stepSizeXY, 1000), getSpeed())));
        findViewById(R.id.btn_x_minus).setOnClickListener(v ->
                send(UC2Commands.motorStep(UC2Commands.STEPPER_X, -getStep(stepSizeXY, 1000), getSpeed())));
        findViewById(R.id.btn_x_forever_plus).setOnClickListener(v ->
                send(UC2Commands.motorForever(UC2Commands.STEPPER_X, 1500)));
        findViewById(R.id.btn_x_forever_minus).setOnClickListener(v ->
                send(UC2Commands.motorForever(UC2Commands.STEPPER_X, -1500)));
        findViewById(R.id.btn_x_stop).setOnClickListener(v ->
                send(UC2Commands.motorStop(UC2Commands.STEPPER_X)));

        // Y
        findViewById(R.id.btn_y_plus).setOnClickListener(v ->
                send(UC2Commands.motorStep(UC2Commands.STEPPER_Y, getStep(stepSizeXY, 1000), getSpeed())));
        findViewById(R.id.btn_y_minus).setOnClickListener(v ->
                send(UC2Commands.motorStep(UC2Commands.STEPPER_Y, -getStep(stepSizeXY, 1000), getSpeed())));
        findViewById(R.id.btn_y_forever_plus).setOnClickListener(v ->
                send(UC2Commands.motorForever(UC2Commands.STEPPER_Y, 1500)));
        findViewById(R.id.btn_y_forever_minus).setOnClickListener(v ->
                send(UC2Commands.motorForever(UC2Commands.STEPPER_Y, -1500)));
        findViewById(R.id.btn_y_stop).setOnClickListener(v ->
                send(UC2Commands.motorStop(UC2Commands.STEPPER_Y)));

        // Z (smaller default step)
        findViewById(R.id.btn_z_plus).setOnClickListener(v ->
                send(UC2Commands.motorStep(UC2Commands.STEPPER_Z, getStep(stepSizeZ, 100), getSpeed())));
        findViewById(R.id.btn_z_minus).setOnClickListener(v ->
                send(UC2Commands.motorStep(UC2Commands.STEPPER_Z, -getStep(stepSizeZ, 100), getSpeed())));
        findViewById(R.id.btn_z_plus_fine).setOnClickListener(v ->
                send(UC2Commands.motorStep(UC2Commands.STEPPER_Z, 4, getSpeed())));
        findViewById(R.id.btn_z_minus_fine).setOnClickListener(v ->
                send(UC2Commands.motorStep(UC2Commands.STEPPER_Z, -4, getSpeed())));
        findViewById(R.id.btn_z_stop).setOnClickListener(v ->
                send(UC2Commands.motorStop(UC2Commands.STEPPER_Z)));

        // A
        findViewById(R.id.btn_a_plus).setOnClickListener(v ->
                send(UC2Commands.motorStep(UC2Commands.STEPPER_A, getStep(stepSizeA, 1000), getSpeed())));
        findViewById(R.id.btn_a_minus).setOnClickListener(v ->
                send(UC2Commands.motorStep(UC2Commands.STEPPER_A, -getStep(stepSizeA, 1000), getSpeed())));
        findViewById(R.id.btn_a_stop).setOnClickListener(v ->
                send(UC2Commands.motorStop(UC2Commands.STEPPER_A)));
    }

    private int getStep(EditText et, int fallback) {
        try {
            String s = et.getText().toString().trim();
            if (s.isEmpty()) return fallback;
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int getSpeed() {
        return getStep(speedInput, 15000);
    }

    private void wireLaserControls() {
        for (int i = 0; i < 4; i++) {
            final int channel = i;
            laserSeek[i].setMax(1023);
            laserSeek[i].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                    laserVal[channel].setText(String.valueOf(progress));
                    if (fromUser && serial.isConnected()) {
                        send(UC2Commands.setLaser(channel, progress));
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {}
            });
        }

        findViewById(R.id.btn_laser0_on).setOnClickListener(v -> { laserSeek[0].setProgress(1023); send(UC2Commands.laserOn(0)); });
        findViewById(R.id.btn_laser0_off).setOnClickListener(v -> { laserSeek[0].setProgress(0); send(UC2Commands.laserOff(0)); });
        findViewById(R.id.btn_laser1_on).setOnClickListener(v -> { laserSeek[1].setProgress(1023); send(UC2Commands.laserOn(1)); });
        findViewById(R.id.btn_laser1_off).setOnClickListener(v -> { laserSeek[1].setProgress(0); send(UC2Commands.laserOff(1)); });
        findViewById(R.id.btn_laser2_on).setOnClickListener(v -> { laserSeek[2].setProgress(1023); send(UC2Commands.laserOn(2)); });
        findViewById(R.id.btn_laser2_off).setOnClickListener(v -> { laserSeek[2].setProgress(0); send(UC2Commands.laserOff(2)); });
        findViewById(R.id.btn_laser3_on).setOnClickListener(v -> { laserSeek[3].setProgress(1023); send(UC2Commands.laserOn(3)); });
        findViewById(R.id.btn_laser3_off).setOnClickListener(v -> { laserSeek[3].setProgress(0); send(UC2Commands.laserOff(3)); });
    }

    private void wireLedControls() {
        findViewById(R.id.btn_led_full_on).setOnClickListener(v  -> send(UC2Commands.ledFullOn()));
        findViewById(R.id.btn_led_full_off).setOnClickListener(v -> send(UC2Commands.ledFullOff()));

        findViewById(R.id.btn_outer_on).setOnClickListener(v   -> send(UC2Commands.ledOuterRingOn()));
        findViewById(R.id.btn_outer_off).setOnClickListener(v  -> send(UC2Commands.ledOuterRingOff()));
        findViewById(R.id.btn_middle_on).setOnClickListener(v  -> send(UC2Commands.ledMiddleRingOn()));
        findViewById(R.id.btn_middle_off).setOnClickListener(v -> send(UC2Commands.ledMiddleRingOff()));
        findViewById(R.id.btn_center_on).setOnClickListener(v  -> send(UC2Commands.ledCenterOn()));
        findViewById(R.id.btn_center_off).setOnClickListener(v -> send(UC2Commands.ledCenterOff()));
    }

    private void wireMotorEnable() {
        SwitchCompat sw = findViewById(R.id.switch_motor_enable);
        sw.setOnCheckedChangeListener((b, checked) -> send(UC2Commands.motorEnable(checked)));
    }

    private void wireHoming() {
        findViewById(R.id.btn_home_x).setOnClickListener(v -> homeAxis(UC2Commands.STEPPER_X));
        findViewById(R.id.btn_home_y).setOnClickListener(v -> homeAxis(UC2Commands.STEPPER_Y));
        findViewById(R.id.btn_home_z).setOnClickListener(v -> homeAxis(UC2Commands.STEPPER_Z));
        findViewById(R.id.btn_home_a).setOnClickListener(v -> homeAxis(UC2Commands.STEPPER_A));
    }

    private void homeAxis(int stepperId) {
        // Sensible defaults matching the WebSerial demo
        send(UC2Commands.homeStepper(stepperId, 20000, 15000, -1, 0));
    }

    private void wireConsole() {
        findViewById(R.id.btn_send_cmd).setOnClickListener(v -> {
            String s = cmdInput.getText().toString().trim();
            if (!s.isEmpty()) {
                send(s);
                cmdInput.setText("");
            }
        });

        findViewById(R.id.btn_clear_console).setOnClickListener(v ->
                consoleText.setText(""));

        findViewById(R.id.btn_get_state).setOnClickListener(v ->
                send(UC2Commands.getState()));
    }

    // ============================================================
    // Helpers
    // ============================================================

    private void send(String cmd) {
        if (!serial.isConnected()) {
            toast("Not connected");
            return;
        }
        serial.sendCommand(cmd);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private void appendConsole(String s) {
        consoleText.append(s);
        if (!s.endsWith("\n")) consoleText.append("\n");

        // auto-scroll
        final int scrollAmount = consoleText.getLayout() == null ? 0
                : consoleText.getLayout().getLineTop(consoleText.getLineCount()) - consoleText.getHeight();
        if (scrollAmount > 0) consoleText.scrollTo(0, scrollAmount);
        else consoleText.scrollTo(0, 0);
    }

    private void setUiEnabled(boolean connected) {
        // Disable all action controls when not connected. The connect button + spinner stay live.
        int[] disabledWhenNotConnected = {
                R.id.btn_change_baud, R.id.btn_get_state, R.id.btn_send_cmd,
                R.id.btn_x_plus, R.id.btn_x_minus, R.id.btn_x_forever_plus,
                R.id.btn_x_forever_minus, R.id.btn_x_stop,
                R.id.btn_y_plus, R.id.btn_y_minus, R.id.btn_y_forever_plus,
                R.id.btn_y_forever_minus, R.id.btn_y_stop,
                R.id.btn_z_plus, R.id.btn_z_minus, R.id.btn_z_plus_fine,
                R.id.btn_z_minus_fine, R.id.btn_z_stop,
                R.id.btn_a_plus, R.id.btn_a_minus, R.id.btn_a_stop,
                R.id.btn_laser0_on, R.id.btn_laser0_off,
                R.id.btn_laser1_on, R.id.btn_laser1_off,
                R.id.btn_laser2_on, R.id.btn_laser2_off,
                R.id.btn_laser3_on, R.id.btn_laser3_off,
                R.id.seek_laser0, R.id.seek_laser1, R.id.seek_laser2, R.id.seek_laser3,
                R.id.btn_led_full_on, R.id.btn_led_full_off,
                R.id.btn_outer_on, R.id.btn_outer_off,
                R.id.btn_middle_on, R.id.btn_middle_off,
                R.id.btn_center_on, R.id.btn_center_off,
                R.id.switch_motor_enable,
                R.id.btn_home_x, R.id.btn_home_y, R.id.btn_home_z, R.id.btn_home_a,
                R.id.et_cmd
        };
        for (int id : disabledWhenNotConnected) {
            View v = findViewById(id);
            if (v != null) v.setEnabled(connected);
        }
        connectBtn.setText(connected ? R.string.disconnect : R.string.connect);
    }

    // ============================================================
    // UsbSerialManager.Listener
    // ============================================================

    @Override
    public void onSerialConnected(String deviceName) {
        statusText.setText(getString(R.string.status_connected, serial.getCurrentBaud()));
        setUiEnabled(true);
        appendConsole("[connected: " + deviceName + "]");
    }

    @Override
    public void onSerialDisconnected() {
        statusText.setText(R.string.status_disconnected);
        setUiEnabled(false);
        appendConsole("[disconnected]");
    }

    @Override
    public void onSerialDataReceived(String data) {
        consoleText.append(data);
    }

    @Override
    public void onSerialError(String message) {
        appendConsole("ERROR: " + message);
        Snackbar.make(connectBtn, message, Snackbar.LENGTH_LONG).show();
    }

    @Override
    public void onSerialLog(String message) {
        appendConsole(message);
    }
}
