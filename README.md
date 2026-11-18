# Nothing Essential Camera

![Kotlin](https://img.shields.io/badge/Kotlin-1.9-purple?logo=kotlin)
![Android](https://img.shields.io/badge/Android-10%2B-green?logo=android)
![LSPosed](https://img.shields.io/badge/LSPosed-Zygisk-blue?logo=android)
![Xposed](https://img.shields.io/badge/Xposed-API%2082-orange)
![Nothing Phone](https://img.shields.io/badge/Nothing-3a%2F3a%20Pro-gray?logo=android)
![License](https://img.shields.io/badge/License-GPL--3.0-red)

Xposed (LSPosed) module that turns the **Essential Key** on Nothing Phone (3a / 3a Pro) into a real camera **shutter button** — short press takes a photo, holding records video.

## Features

- Short press → photo
- Long press → video recording
- Double press → switch camera / zoom
- Volume keys = zoom while holding
- Works only in Nothing Camera; Essential Space stays untouched elsewhere

## Requirements

- Nothing Phone with Essential Key (3a, 3a Pro, etc.)
- Root + LSPosed (Zygisk)
- Android 10+ (API 29)

## Install

1. Build the APK:
   ```bash
   ./gradlew assembleDebug
   ```
   APK: `app/build/outputs/apk/debug/`

2. Install the APK on your phone.

3. Enable the module in **LSPosed** and set scope to **Nothing Camera** (+ `system`/`android` if asked — the `system_server` hook runs in the `android` process).

4. Force-stop Nothing Camera to apply hooks.

## Settings

Open the module in LSPosed → **Launch**. The screen uses Nothing OS styling: NDot
dot-matrix wordmark and section labels, Geist body text and Geist Mono metadata on a
dark `#1B1B1D` / red `#D71921` Material 3 palette.

- Module enabled
- Only remap in camera foreground
- Short press: photo / focus
- Hold: video / nothing
- Double press: switch camera / zoom
- Volume = zoom while holding

## Verify scan code

```bash
adb shell getevent -lp
```
Look for `gpio-keys` with key code **250**.

## License

GPL-3.0