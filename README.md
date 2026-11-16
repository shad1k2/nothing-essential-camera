# Nothing Essential Camera

[![Android CI](https://github.com/shad1k2/nothing-essential-camera/actions/workflows/android.yml/badge.svg)](https://github.com/shad1k2/nothing-essential-camera/actions/workflows/android.yml)

Xposed (LSPosed) module that turns the **Essential Key** on Nothing Phone (3a / 3a Pro)
into a real camera **shutter button** inside the Nothing Camera app — the way
iPhone's Camera Control behaves (light press focuses, full press takes the photo).

Nothing OS 3.1+ can already capture a frame with the Essential Key, but the shot goes
to **Essential Space**, not to the gallery. This module sends the shot to the normal
camera pipeline instead.

## How it works

Device logs showed that Nothing OS consumes the Essential Key inside **`system_server`**:
the key handler launches `com.nothing.ntessentialrecorder/.FocusActivity`, a transparent
activity that runs the Essential Space capture flow (which is why the shot never lands in
the gallery). Rewriting the key code in `PhoneWindowManager` alone does not help — the
launch still happens and steals the capture.

The module therefore hooks:

1. **`system_server` — `ActivityTaskManagerService.startActivity*`** — when the system
   tries to launch `FocusActivity` of `ntessentialrecorder` / `ntessentialspace` **while
   the Nothing Camera is in the foreground**, the launch is cancelled and a synthetic
   `KEYCODE_CAMERA` press/release pair is injected into the focused window (the camera).
   Outside the camera the launch proceeds as usual, so Essential Space keeps working.
2. **`com.nothing.camera` — `Activity.dispatchKeyEvent`** — handles the injected key:
   `ACTION_DOWN` focuses, `ACTION_UP` finds the shutter button in the view hierarchy
   and calls `performClick()`.

## Requirements

- Nothing Phone with the Essential Key: **3a**, **3a Pro** (and other models where the
  key is present — scan code may differ).
- **Root** with **LSPosed** (Zygisk) installed.
- Android 10+ (API 29).

## Preparation on the phone

The module cancels the Essential Space launch itself, so disabling the Essential Space
packages is **optional** — do it only if you want the key to do nothing outside the
camera as well (nothing is deleted, only for user 0):

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
| Button does nothing in camera | the launch hook did not fire | check LSPosed logs for `[EssShutter] Essential launch` — if absent, the launch entry point on your firmware differs (look for `startActivityWithFeature` / new AIDL overloads) |
| Log says "camera foreground=false" | the camera was not the top task | open the camera, wait a second, press again |
| Log says "shutter button not found" | wrong resource id | decompile `com.nothing.camera` and adapt `findShutter()` |
| Photo lands in Essential Space instead of gallery | the launch was not cancelled | check that the log shows `camera foreground=true` and `injected KEYCODE_CAMERA` |
| Nothing happens at all | system_server hooks not applied | make sure LSPosed scope includes `system` / `android` and reboot after enabling |

## License

GPL-3.0
