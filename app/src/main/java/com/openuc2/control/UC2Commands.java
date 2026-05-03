package com.openuc2.control;

import java.util.Locale;

/**
 * Builders for the JSON commands the openUC2 ESP32 firmware expects.
 * Matches the protocol used in the WebSerial demo (laser_act, motor_act,
 * ledarr_act, home_act, tmc_act, can_act, bt_scan, state_get).
 */
public final class UC2Commands {

    public static final int STEPPER_A = 0;
    public static final int STEPPER_X = 1;
    public static final int STEPPER_Y = 2;
    public static final int STEPPER_Z = 3;

    private UC2Commands() {}

    // === State ===

    public static String getState() {
        return "{\"task\":\"/state_get\"}";
    }

    // === Lasers (PWM channels 0..3) ===

    /** value in 0..1023 */
    public static String setLaser(int channel, int value) {
        return String.format(Locale.US,
                "{\"task\":\"/laser_act\",\"LASERid\":%d,\"LASERval\":%d}",
                channel, clamp(value, 0, 1023));
    }

    public static String laserOn(int channel) { return setLaser(channel, 1024); }
    public static String laserOff(int channel) { return setLaser(channel, 0); }

    // === Motors ===

    /** Move a stepper a relative number of steps. */
    public static String motorStep(int stepperId, int steps, int speed) {
        return String.format(Locale.US,
                "{\"task\":\"/motor_act\",\"motor\":{\"steppers\":[" +
                "{\"stepperid\":%d,\"position\":%d,\"speed\":%d,\"isabs\":0,\"isaccel\":0}]}}",
                stepperId, steps, speed);
    }

    /** Continuous motion until stop is sent. Negative speed reverses direction. */
    public static String motorForever(int stepperId, int speed) {
        return String.format(Locale.US,
                "{\"task\":\"/motor_act\",\"motor\":{\"steppers\":[" +
                "{\"stepperid\":%d,\"isforever\":1,\"speed\":%d,\"isabs\":0,\"isaccel\":0}]}}",
                stepperId, speed);
    }

    public static String motorStop(int stepperId) {
        return String.format(Locale.US,
                "{\"task\":\"/motor_act\",\"motor\":{\"steppers\":[" +
                "{\"stepperid\":%d,\"isstop\":1}]}}",
                stepperId);
    }

    public static String motorEnable(boolean autoEnable) {
        return "{\"task\":\"/motor_act\",\"isen\":1,\"isenauto\":" + (autoEnable ? 1 : 0) + "}";
    }

    // === LED array ===

    public static String ledFullOn() {
        return "{\"task\":\"/ledarr_act\",\"led\":{\"LEDArrMode\":1,\"led_array\":[{\"id\":0,\"r\":255,\"g\":255,\"b\":255}]}}";
    }

    public static String ledFullOff() {
        return "{\"task\":\"/ledarr_act\",\"led\":{\"LEDArrMode\":1,\"led_array\":[{\"id\":0,\"r\":0,\"g\":0,\"b\":0}]}}";
    }

    /** Build a ring command: turns LEDs in the inclusive id range on or off. */
    public static String ledRing(int startId, int endId, int rgbValue) {
        StringBuilder arr = new StringBuilder();
        arr.append("[");
        for (int i = startId; i <= endId; i++) {
            if (i > startId) arr.append(",");
            arr.append(String.format(Locale.US,
                    "{\"id\":%d,\"r\":%d,\"g\":%d,\"b\":%d}",
                    i, rgbValue, rgbValue, rgbValue));
        }
        arr.append("]");
        return "{\"task\":\"/ledarr_act\",\"led\":{\"LEDArrMode\":8,\"led_array\":" + arr + "}}";
    }

    public static String ledOuterRingOn()   { return ledRing(9, 24, 255); }
    public static String ledOuterRingOff()  { return ledRing(9, 24, 0); }
    public static String ledMiddleRingOn()  { return ledRing(1, 8, 255); }
    public static String ledMiddleRingOff() { return ledRing(1, 8, 0); }
    public static String ledCenterOn()      { return ledRing(0, 0, 255); }
    public static String ledCenterOff()     { return ledRing(0, 0, 0); }

    // === Homing ===

    public static String homeStepper(int stepperId, int timeout, int speed,
                                     int direction, int endstopPolarity) {
        return String.format(Locale.US,
                "{\"task\":\"/home_act\",\"home\":{\"steppers\":[" +
                "{\"stepperid\":%d,\"timeout\":%d,\"speed\":%d,\"direction\":%d,\"endstoppolarity\":%d}]}}",
                stepperId, timeout, speed, direction, endstopPolarity);
    }

    // === Bluetooth ===

    public static String btPair() {
        return "{\"task\":\"/bt_scan\"}";
    }

    // === helpers ===

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
