# UC2 Control — Android USB OTG app for openUC2

Native Android app that drives an openUC2 ESP32 / ESP32-S3 board over USB OTG.
It covers the hardware control surface of the
[WebSerial demo](https://youseetoo.github.io) — stage jog, illumination, LED
matrix, homing, TMC and CAN setup, and a raw JSON console — without needing a
laptop in the lab.

<!-- Screenshots go here once you have a board to point it at. -->

---

## Install

Grab an APK from the [Releases](../../releases) page, or from the artifacts of
any [Build APK](../../actions/workflows/build.yml) run, and sideload it.

Requirements: Android 5.0 (API 21) or newer, and a phone that supports **USB
host / OTG**.

---

## Build

```bash
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. Requires **JDK 17** — newer
JDKs fail with `Unsupported class file major version`.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # macOS
./gradlew assembleRelease                          # unsigned release build
./gradlew test                                     # protocol unit tests
./gradlew lintDebug                                # static analysis
```

### Build in CI

`.github/workflows/build.yml` builds on every push, PR and tag.

| Trigger | Result |
|---|---|
| push / PR | debug + release APK as workflow artifacts, plus test and lint reports |
| `workflow_dispatch` | same, with a debug/release/both choice |
| tag `v*` | a GitHub Release with both APKs attached |

Run it from the command line with the [`gh`](https://cli.github.com) CLI:

```bash
gh workflow run build.yml -f build_type=both
```

Watch it and download the result:

```bash
gh run watch
gh run download --name UC2Control-apk-0.0.0-main+abc1234
```

Cut a release:

```bash
git tag v2.0 && git push origin v2.0
```

Version numbers come from the tag; untagged builds get
`0.0.0-<branch>+<sha>` and use the run number as the version code.

### Signed release builds (optional)

Without a keystore, CI still produces a working *unsigned* release APK. To get
signed output, add four repository secrets:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -i release.jks` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

Locally, put the same values in a `keystore.properties` file in the repo root
(it is git-ignored).

---

## Using the app

1. Connect the board to the phone through a USB-C OTG adapter — or directly,
   if the board has USB-C. **The cable must be a data cable**; charge-only
   cables enumerate nothing at all.
2. Android asks which app to open. Pick UC2 Control and allow USB access.
3. Choose a baud rate (**115200** is the firmware default) and tap **Connect**.

Tabs:

| Tab | What is in it |
|---|---|
| **Stage** | XYZA jog, continuous motion, per-axis stop, big STOP ALL, motor power |
| **Light** | 5 laser/LED PWM channels, LED ring and half-field patterns, 8×8 single-LED grid |
| **Setup** | Homing, TMC driver, CAN address, board queries, connection troubleshooting |
| **Console** | Full serial log, raw JSON entry, share/export |

To change baud while connected, pick a new rate and tap **Apply** — the port is
closed and re-opened, which also resets the board.

---

## When it will not connect

This is the part that used to be painful, so the app now tells you what it
sees instead of failing silently.

**Tap `Diagnostics`.** It lists every attached USB device with VID/PID,
interface classes, permission state, and whether a driver matched. `Share log`
in the Console tab sends the same dump plus the full session log.

Common causes, in the order worth checking:

1. **Nothing listed at all** — the phone is not in host mode. Most phones need
   a real OTG adapter, and every cable must carry data.
2. **Device listed, `driver: none`** — an unrecognised USB-serial chip. Set
   **Setup ▸ Force driver** to CDC-ACM (ESP32-S3 native USB) or CH34x, then
   reconnect. Please also send us the dump so the chip can be added.
3. **Connects, but no output** — wrong baud rate. The board's boot banner is
   the giveaway: garbage characters mean the rate is wrong. Try 115200, then
   921600.
4. **Connects, board silent, ESP32-S3 with native USB** — check that
   **DTR asserted** is on in Setup. Arduino's `USBCDC` only transmits once the
   host raises DTR.
5. **Board seems held in reset** — leave **RTS asserted** on. On boards with
   the classic auto-reset circuit, DTR low with RTS high holds EN low and the
   ESP32 never starts.

### Supported USB-serial chips

CP210x · CH340 / CH341 · FTDI · Prolific · CDC-ACM, including **Espressif
native USB (VID 0x303A)** on ESP32-S2/S3/C3.

`usb-serial-for-android` 3.7.0 has no entry for the Espressif VID at all —
`UsbDrivers.java` adds it, so S3 boards are found deterministically rather than
relying on a descriptor walk.

**CH343 / CH9102F (VID 0x1A86, PID 0x55Dx)** — common on current ESP32-S3
boards — are detected as a *last-resort fallback*: the app tries the CH34x
driver on any otherwise-unmatched WCH device and marks it `[guess]` in
Diagnostics. These parts ship in either CDC or vendor-specific mode and only
some of them speak the CH340 protocol closely enough. If a guess misbehaves,
set **Force driver ▸ CDC-ACM** and reconnect — and please send us the
Diagnostics dump so the id can be mapped properly.

If your board is not detected at all, add its VID/PID to `UsbDrivers.java`
*and* to `res/xml/device_filter.xml` (which takes **decimal** ids).

---

## Command protocol

`UC2Commands.java` builds the JSON the firmware expects. Every builder is
pinned to the WebSerial reference by `UC2CommandsTest`, so a protocol drift
shows up as a failing test rather than a dead board.

| Action | Task |
|---|---|
| Move stepper N steps | `/motor_act` (`position`, `speed`) |
| Continuous motion | `/motor_act` (`isforever`) |
| Stop stepper | `/motor_act` (`isstop`) |
| Motor power | `/motor_act` (`isen`, `isenauto`) |
| Laser / LED PWM | `/laser_act` (`LASERid`, `LASERval` 0–1023) |
| LED matrix | `/ledarr_act` (`action`: `fill` / `off` / `rings` / `halves` / `single`) |
| Home an axis | `/home_act` |
| TMC driver | `/tmc_act` |
| CAN address | `/can_act` |
| Board state | `/state_get`, `/motor_get`, `/modules_get`, `/tmc_get` |

Anything else can be typed straight into the Console tab.

**Stepper ids:** A = 0, X = 1, Y = 2, Z = 3.

> LED commands use the current `action` form. Older firmware that expects
> `LEDArrMode` / `led_array` is covered by `UC2Commands.ledLegacyFill`.

---

## Project layout

```
app/src/main/
├── AndroidManifest.xml
├── java/com/openuc2/control/
│   ├── MainActivity.java       UI wiring, tabs, all click handlers
│   ├── UsbSerialManager.java   connect/read/write/baud, threading, error recovery
│   ├── UsbDrivers.java         probe table, forced drivers, USB diagnostics
│   ├── UC2Commands.java        JSON command builders
│   └── ConsoleBuffer.java      bounded, colourised console
└── res/
    ├── layout/                 activity_main + one layout per tab
    ├── values/                 colors (brand palette), themes, strings
    ├── font/                   Space Grotesk, IBM Plex Mono
    └── xml/device_filter.xml   USB auto-attach filter (decimal ids!)

app/src/test/                   protocol tests, no device needed
```

---

## Brand

Colours follow `openUC2_brandguide.pdf`:

| | Hex | Role |
|---|---|---|
| Blue | `#023773` | main brand colour — header, primary actions |
| Green | `#85B918` | secondary — "on"/go states, tab indicator |
| Turquoise | `#1F9C7C` | third — continuous motion, secondary actions |
| Light grey | `#FAF9F9` | body background |
| Grey | `#999999` | muted text, "off" states |

The brand font is **Stolzl**, which is licensed and cannot ship in a public
repo. The app bundles **Space Grotesk** — the same substitute the openUC2 web
frontend already falls back to — plus IBM Plex Mono for the console. If you
have a Stolzl licence, drop the TTFs into `res/font/` and repoint
`themes.xml`.

---

## Not included

Webcam preview, WebRTC streaming and timelapse from the web demo are out of
scope — they need a camera pipeline rather than a serial link.
