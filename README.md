# Nothing Essential Camera

[![Android CI](https://github.com/shad1k2/nothing-essential-camera/actions/workflows/android.yml/badge.svg)](https://github.com/shad1k2/nothing-essential-camera/actions/workflows/android.yml)

Xposed (LSPosed) module that turns the **Essential Key** on Nothing Phone (3a / 3a Pro)
into a real camera **shutter button** inside the Nothing Camera app — the way
iPhone's Camera Control behaves (light press focuses, full press takes the photo).

Nothing OS 3.1+ can already capture a frame with the Essential Key, but the shot goes
to **Essential Space**, not to the gallery. This module sends the shot to the normal
camera pipeline instead.

## How it works

The Essential Key arrives as `keyCode = 0` (`KEYCODE_UNKNOWN`) with `scanCode = 250`.
Nothing OS's policy recognizes that scan code in `PhoneWindowManager` and launches
`com.nothing.ntessentialrecorder/.FocusActivity`, which runs the Essential Space capture
flow — that is why the shot never reaches the gallery.

Nothing Camera already knows how to handle `KEYCODE_CAMERA` natively: `CameraUIContext`
(`isKeyCodeNeedCaptrue(27)` → `onKeyUp()` → `simulateKeyDownAndUp()`) dispatches real
touch DOWN/UP events to the photo shutter button — exactly the same path as tapping the
shutter on screen.

So the module hooks only one thing:

1. **`system_server` — `PhoneWindowManager.interceptKeyBeforeQueueing` and
   `interceptKeyBeforeDispatching`** — while the Nothing Camera is in the foreground,
   the Essential Key event is rewritten to `KEYCODE_CAMERA` with `scanCode = 0`. Nothing's
   policy no longer recognizes it (no Essential Space launch), and the camera receives a
   normal shutter key. Outside the camera nothing is rewritten, so Essential Space and
   its screenshots keep working as usual.
2. **`com.nothing.camera` — `Activity.dispatchKeyEvent`** — read-only observer that logs
   the arrival of the rewritten key. It deliberately does not consume the event or click
   any view: the app's own native shutter path handles it.

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
| Button does nothing in camera | key not rewritten | check logs for `[EssShutter] PWM interceptKeyBeforeQueueing: key=0 scan=250 ... camera foreground=true` followed by `rewritten Essential Key -> KEYCODE_CAMERA` |
| Log says "camera foreground=false" | the camera was not the top task | open the camera, wait a second, press again |
| Log says "camera received KEYCODE_CAMERA" but no photo | the app's native shutter path is blocked | make sure no other module consumes the key; check the camera's own logcat for `simulateKeyDownAndUp` |
| Photo lands in Essential Space instead of gallery | the key still reaches Nothing's policy | verify the rewritten key has `scanCode=0`; if the ROM matches on other fields, adapt `cameraKey()` in `SystemHook` |
| Nothing happens at all | system_server hooks not applied | make sure LSPosed scope includes `system` / `android` and reboot after enabling |

## License

GPL-3.0
