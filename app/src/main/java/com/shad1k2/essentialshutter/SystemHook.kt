package com.shad1k2.essentialshutter

import android.content.ComponentName
import android.content.Intent
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
 * Nothing OS consumes the Essential Key inside system_server: the key handler starts
 * `com.nothing.ntessentialrecorder/.FocusActivity`, a transparent activity that runs the
 * Essential Space capture flow (which is why the shot never lands in the gallery).
 *
 * Strategy:
 *  1. Hook the activity-launch entry points of `ActivityTaskManagerService`. When the
 *     system tries to start `FocusActivity` of an Essential Space package **and the
 *     Nothing Camera is in the foreground**, cancel the launch.
 *  2. Inject a synthetic `KEYCODE_CAMERA` press/release pair into the focused window —
 *     the camera app — which `CameraHook` then translates into focus + shutter.
 *
 * Outside the camera the launch proceeds untouched, so Essential Space keeps working.
 */
object SystemHook {

    private const val TAG = "[EssShutter]"

    /** De-dupe guard: the system may fire several launches for one key press. */
    private var lastTrigger = 0L

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        val atms = try {
            XposedHelpers.findClass(
                "com.android.server.wm.ActivityTaskManagerService",
                lpparam.classLoader
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG ActivityTaskManagerService not found: ${t.message}")
            return
        }

        val intercept = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                val intent = param.args.firstOrNull { it is Intent } as? Intent ?: return
                if (!isEssentialLaunch(intent.component)) return

                val camera = isCameraForeground()
                XposedBridge.log(
                    "$TAG Essential launch -> ${intent.component}, camera foreground=$camera"
                )

                if (!camera) return // let Essential Space do its thing

                if ((param.method as java.lang.reflect.Method).returnType != Int::class.javaPrimitiveType) {
                    XposedBridge.log(
                        "$TAG cannot cancel ${param.method.name}: non-int return type"
                    )
                    return
                }
                param.result = startCanceled()
                triggerShutter()
            }
        }

        // Cover the binder entry point and the variants used across Android 11-15.
        for (name in listOf("startActivity", "startActivityAsUser", "startActivityWithFeature")) {
            val count = XposedBridge.hookAllMethods(atms, name, intercept)
            XposedBridge.log("$TAG hooked $name ($count overloads)")
        }
    }

    private fun isEssentialLaunch(component: ComponentName?): Boolean {
        if (component == null) return false
        return (component.packageName == MainHook.RECORDER_PACKAGE ||
                component.packageName == MainHook.SPACE_PACKAGE) &&
                component.className.endsWith(MainHook.FOCUS_ACTIVITY)
    }

    private fun startCanceled(): Int = try {
        XposedHelpers.getStaticIntField(
            Class.forName("android.app.ActivityManager"), "START_CANCELED"
        )
    } catch (t: Throwable) {
        -1 // START_INTENT_NOT_RESOLVED
    }

    private fun isCameraForeground(): Boolean = topActivityPackage() == MainHook.CAMERA_PACKAGE

    /**
     * Top-of-stack package, resolved inside system_server. `IActivityTaskManager.getTasks`
     * exists since Android 11; `IActivityManager.getTasks` is the pre-R fallback.
     */
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
                // AOSP returns ParceledListSlice, but some vendor ROMs hand back a
                // plain ArrayList — handle both.
                val list = when (tasks) {
                    is List<*> -> tasks
                    else -> XposedHelpers.callMethod(tasks, "getList") as? List<*> ?: continue
                }
                val task = list.firstOrNull() ?: continue
                val top = XposedHelpers.getObjectField(task, "topActivity") as? ComponentName
                    ?: continue
                XposedBridge.log("$TAG top activity: ${top.packageName}")
                return top.packageName
            } catch (t: Throwable) {
                XposedBridge.log("$TAG top-activity via $className failed: ${t.message}")
            }
        }
        return null
    }

    /** Emulate a camera shutter key (down + up) delivered to the focused window. */
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
        0,
        0,
        InputDevice.SOURCE_KEYBOARD,
    )
}
