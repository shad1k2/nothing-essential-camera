# Nothing Essential Camera

Xposed (LSPosed) module that turns the **Essential Key** on Nothing Phone (3a / 3a Pro)
into a real camera **shutter button** inside the Nothing Camera app — the way
iPhone's Camera Control behaves (light press focuses, full press takes the photo).

Nothing OS 3.1+ can already capture a frame with the Essential Key, but the shot goes
to **Essential Space**, not to the gallery. This module sends the shot to the normal
camera pipeline instead.

## How it works

The Essential Key enters the input pipeline as `keyCode = 0` (`KEYCODE_UNKNOWN`) with
`scanCode = 250`, and Nothing OS claims the single press at the **system policy** level,
so the event never reaches the camera app.

The module therefore hooks two places:

1. **`system_server`** — `PhoneWindowManager.interceptKeyBeforeDispatching` rewrites the
   event to `KEYCODE_CAMERA` (27), but only while the Nothing Camera is in the foreground.
2. **`com.nothing.camera`** — `Activity.dispatchKeyEvent` handles the rewritten key:
   `ACTION_DOWN` focuses, `ACTION_UP` finds the shutter button in the view hierarchy
   and calls `performClick()`.

## Requirements

- Nothing Phone with the Essential Key: **3a**, **3a Pro** (and other models where the
  key is present — scan code may differ).
- **Root** with **LSPosed** (Zygisk) installed.
- Android 10+ (API 29).

## Preparation on the phone

Nothing OS holds the single press via the Essential Space packages. Disable them
(nothing is deleted, only for user 0):

```bash
adb shell pm disable-user --user 0 com.nothing.ntessentialspace
adb shell pm disable-user --user 0 com.nothing.ntessentialrecorder
```

To restore:

```bash
adb shell pm enable com.nothing.ntessentialspace
adb shell pm enable com.nothing.ntessentialrecorder
```

## Build

```bash
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

> The Xposed API (`de.robv.android.xposed:api:82`) is `compileOnly` — it is provided by
> LSPosed at runtime. If your mirror does not serve it, fetch the API jar from
> <https://api.xposed.info/> and put it in `app/libs/`.

## Install

1. Install the APK.
2. Enable the module in **LSPosed** and set the scope to `Nothing Camera` (+ system, if
   the module asks for it — the `system_server` hook lives in the `android` process).
3. Force-stop the Nothing Camera app so it restarts with the hooks applied.

## Verify your scan code

```bash
adb shell getevent -lp
```

Look for the `gpio-keys` input device and check that its key code is `250`.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| Button does nothing in camera | system policy eats the event | make sure the Essential Space packages are disabled |
| Log says "shutter button not found" | wrong resource id | decompile `com.nothing.camera` and adapt `findShutter()` |
| Photo lands in Essential Space instead of gallery | Nothing recorder still active | disable `com.nothing.ntessentialrecorder` too |
| Nothing happens at all | wrong scan code on your firmware | log the scan code and update `MainHook.SCAN_ESSENTIAL` |

## License

GPL-3.0
