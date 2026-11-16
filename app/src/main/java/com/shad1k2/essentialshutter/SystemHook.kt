package com.shad1k2.essentialshutter

import android.content.ComponentName
import android.hardware.input.InputManager
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Nothing OS claims the Essential Key (keyCode 0 / scanCode 250) in PhoneWindowManager:
 * its policy launches `ntessentialrecorder/.FocusActivity`, which runs the Essential
 * Space capture flow (that is why the shot goes to Essential Space, not the gallery).
 *
 * Replacing the KeyEvent argument of interceptKeyBefore* does NOT work — the event that
 * reaches the window is created by native InputDispatcher. Instead, while the camera is
 * in the foreground we swallow the original key at the policy level (result = 0 drops
 * FLAG_PASS_TO_USER and skips Nothing's policy entirely) and inject a clean
 * KEYCODE_CAMERA press/release. The camera app then handles it with its own native
 * shutter path (CameraUIContext.onKeyUp -> simulateKeyDownAndUp).
 *
 * Outside the camera nothing is swallowed, so Essential Space and its screenshots keep
 * working as usual.
 */
object SystemHook {

    private const val TAG = "[EssShutter]"
    private var lastTrigger = 0L

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pwm = try {
            XposedHelpers.findClass(
                "com.android.server.policy.PhoneWindowManager", lpparam.classLoader
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG PhoneWindowManager not found: ${t.message}")
            return
        }

        XposedBridge.hookAllMethods(
            pwm,
            "interceptKeyBeforeQueueing",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val event = param.args.firstOrNull { it is KeyEvent } as? KeyEvent ?: return
                    if (event.scanCode != MainHook.SCAN_ESSENTIAL) return

                    val camera = isCameraForeground()
                    XposedBridge.log(
                        "$TAG PWM interceptKeyBeforeQueueing: key=${event.keyCode} " +
                                "scan=${event.scanCode} action=${event.action}, " +
                                "camera foreground=$camera"
                    )
                    if (!camera) return // Essential Space handles the key as usual

                    // Skip the original policy (no FocusActivity launch) and drop the
                    // event before it can be dispatched to any window.
                    param.result = 0
                    triggerShutter()
                }
            })
        XposedBridge.log("$TAG hooked interceptKeyBeforeQueueing")
    }

    /** Inject a synthetic KEYCODE_CAMERA pair into the focused window (the camera). */
    private fun triggerShutter() {
        val now = SystemClock.uptimeMillis()
        if (now - lastTrigger < 300) {
            XposedBridge.log("$TAG trigger throttled")
            return
        }
        lastTrigger = now

        try {
            val im = XposedHelpers.callStaticMethod(InputManager::class.java, "getInstance")
            XposedHelpers.callMethod(im, "injectInputEvent", cameraKey(KeyEvent.ACTION_DOWN, now), 0)
            XposedHelpers.callMethod(im, "injectInputEvent", cameraKey(KeyEvent.ACTION_UP, now), 0)
            XposedBridge.log("$TAG injected KEYCODE_CAMERA into the focused window")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG injection failed: ${t.message}")
        }
    }

    private fun cameraKey(action: Int, now: Long) = KeyEvent(
        now,
        now,
        action,
        KeyEvent.KEYCODE_CAMERA,
        0,
        0,
        KeyCharacterMap.VIRTUAL_KEYBOARD,
        0, // scanCode 0: not an Essential Key
        0,
        InputDevice.SOURCE_KEYBOARD,
    )

    private fun isCameraForeground(): Boolean = topActivityPackage() == MainHook.CAMERA_PACKAGE

    /** Top-of-stack package resolved inside system_server. */
    private fun topActivityPackage(): String? {
        val sources = listOf(
            "android.app.ActivityTaskManager" to "IActivityTaskManagerSingleton",
            "android.app.ActivityManager" to "IActivityManagerSingleton",
        )
        for ((className, fieldName) in sources) {
            try {
                val singleton = XposedHelpers.getStaticObjectField(
                    Class.forName(className), fieldName
                )
                val service = XposedHelpers.callMethod(singleton, "get")
                val tasks = XposedHelpers.callMethod(service, "getTasks", 1)
                // AOSP returns ParceledListSlice; some vendor ROMs return ArrayList.
                val list = when (tasks) {
                    is List<*> -> tasks
                    else -> XposedHelpers.callMethod(tasks, "getList") as? List<*> ?: continue
                }
                val task = list.firstOrNull() ?: continue
                val top = XposedHelpers.getObjectField(task, "topActivity") as? ComponentName
                    ?: continue
                return top.packageName
            } catch (t: Throwable) {
                XposedBridge.log("$TAG top-activity via $className failed: ${t.message}")
            }
        }
        return null
    }
}
