# ParkFlow car screen (ESP32-S3 CrowPanel 5.0")

Firmware for the in-car screen: log in as a customer, type a destination, see the 5 closest
parking buildings. It uses the same ParkFlow API as the website and never touches the database.
Internet comes from a phone hotspot.

Built with **Arduino core 3 (pioarduino platform) + LVGL 9 + Espressif's RGB LCD driver** with a
10-line bounce buffer, in VS Code + PlatformIO. No ESP-IDF install is needed: Arduino core 3 ships
ESP-IDF 5 prebuilt.

## How the screen stays smooth

The first version (Arduino core 2 + LovyanGFX) redrew slowly and the picture moved. The ESP32-S3
sends the picture to this RGB panel straight from PSRAM, and when PSRAM is busy the panel runs short of
pixels. Arduino core 3 includes the ESP-IDF 5 RGB LCD driver, which fixes this:

| Setting | Where | What it does |
|---|---|---|
| Bounce buffer (10 lines) | `src/display.cpp` | The panel reads from a small buffer in fast internal RAM, refilled from PSRAM. |
| LVGL partial drawing into internal RAM | `src/display.cpp` | Only changed areas are drawn and copied. |
| UI in `loop()` on core 1, network on core 0 | `src/main.cpp` | Wi-Fi and HTTPS never block drawing; a spinner shows while waiting. |
| Octal PSRAM, one 3 MB app partition | `platformio.ini` | Room for LVGL, Wi-Fi and HTTPS. |

## Set up

1. Open this folder in VS Code (File > Open Folder > `esp32-car-screen`).
2. Copy `src/secrets.example.h` to `src/secrets.h` and fill in your hotspot name and password.
3. Check the COM port in `platformio.ini` (`COM6`).
4. Upload (PlatformIO arrow) and open the Serial Monitor. The first build downloads the platform
   (Arduino core 3) and LVGL and takes several minutes.

## If the picture looks wrong

- **Colors shifted or pixels shimmer:** flip `pclk_active_neg` (0 or 1) in `src/display.cpp`.
- **Picture drifts while Wi-Fi is busy:** raise `BOUNCE_LINES` in `src/display.cpp` (for example to 20).

## Screens

Connecting, Log in (on-screen keyboard; customer accounts only; the login is saved), Search,
Results (5 closest, numbered, closest in green), Details (with Previous / Next).
The top bar shows the screen name, an Online / No Wi-Fi pill and Log out. Everything has a fixed
place on the screen, so nothing moves when text or the Wi-Fi status changes.

## API calls

| Call | When |
|---|---|
| `POST /api/auth/login` | Log in. Token saved in flash (Preferences). |
| `GET /api/me` | At start, to check the saved login. |
| `GET /api/search?q=...` | Best matching place + 5 closest buildings. |

HTTPS is encrypted, but the server's certificate is not checked yet (demo).

## Files

```
platformio.ini        board, Arduino core 3, libraries (LVGL, ArduinoJson)
include/lv_conf.h     LVGL settings (fonts, refresh rate)
src/display.*         RGB LCD (bounce buffer) + LVGL + touch
src/gt911.*           touch driver (I2C 19/20)
src/net.*             Wi-Fi, saved login, ParkFlow API (ArduinoJson)
src/main.cpp          screens (LVGL)
src/theme.h           ParkFlow colors (dark theme)
src/secrets.h         your Wi-Fi and server (not committed)
```
