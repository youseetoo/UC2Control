# UC2 Control — Android USB OTG App

A native Android app to control an openUC2 ESP32 device over USB OTG. It mirrors the core functionality of the `indexWebSerialTest.html` WebSerial demo: motor jog (XYZA), laser PWM control, LED ring control, homing, motor enable, and a raw JSON serial console.

## Building

1. Open the project root (`UC2Control/`) in **Android Studio** (Hedgehog or newer recommended).
2. Let Gradle sync — it will fetch `usb-serial-for-android` from JitPack automatically.
3. Connect an Android device with USB debugging enabled (or use an emulator that supports USB host — most don't, so a real device is best).
4. Press Run.

> **Note:** The app requires `android.hardware.usb.host`, so it can only be installed on devices that support USB OTG.

## Using

1. Plug your ESP32 into the phone via a USB-C OTG adapter (or directly with a USB-C ESP32 board).
2. Launch the app — Android will ask whether to open the app for the device. Allow it.
3. Pick the baud rate (default **115200**) and tap **Connect to ESP32**.
4. Grant USB permission when prompted.
5. Use the jog/laser/LED/homing controls. Output appears in the serial console at the bottom.

To change baud rate while connected, pick a new value and tap **Apply**.

## Supported USB-to-serial chips

The `device_filter.xml` covers:

- **CP210x** (Silicon Labs) — most common on ESP32 dev boards
- **CH340 / CH341** (WCH) — cheap clone boards
- **FTDI** FT232 / FT2232
- **CDC-ACM** — native USB on ESP32-S3 / ESP32-C3
- **Espressif** native USB-JTAG (VID 0x303A)

If your board is not auto-detected, add its VID/PID to `app/src/main/res/xml/device_filter.xml`.

## Project structure

```
app/src/main/
├── AndroidManifest.xml
├── java/com/openuc2/control/
│   ├── MainActivity.java       — UI wiring, button handlers
│   ├── UsbSerialManager.java   — USB OTG serial connect/read/write/threading
│   └── UC2Commands.java        — JSON command builders (laser_act, motor_act, etc.)
└── res/
    ├── layout/activity_main.xml
    ├── values/{strings,colors,themes}.xml
    └── xml/device_filter.xml   — USB VID/PID auto-attach filter
```

## Command protocol

`UC2Commands.java` mirrors the JSON commands used by the openUC2 firmware:

| Action | Task |
|---|---|
| Move stepper N steps | `/motor_act` (with `position`, `speed`) |
| Continuous motion | `/motor_act` (with `isforever:1`) |
| Stop stepper | `/motor_act` (with `isstop:1`) |
| Set laser PWM | `/laser_act` (`LASERid`, `LASERval` 0–1023) |
| LED on/off / rings | `/ledarr_act` (`LEDArrMode`, `led_array`) |
| Home an axis | `/home_act` |
| Motor enable | `/motor_act` (`isen`, `isenauto`) |
| Get state | `/state_get` |

You can send any other JSON command directly from the **Serial Console** at the bottom.

## Stepper IDs

| ID | Axis |
|---|---|
| 0 | A |
| 1 | X |
| 2 | Y |
| 3 | Z |

## Known limitations

- WebRTC / camera streaming from the original web demo is not included.
- No timelapse / image capture.
- Only the first detected USB serial port is used. If your adapter exposes multiple ports, edit `UsbSerialManager.openDriver` to pick the right index.
