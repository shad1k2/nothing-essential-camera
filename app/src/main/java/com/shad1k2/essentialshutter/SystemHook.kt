package com.shad1k2.essentialshutter

import android.content.ComponentName
import android.content.Intent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Nothing OS claims the Essential Key (keyCode 0 / scanCode 250) inside system_server:
 * the policy launches `ntessentialrecorder/.FocusActivity` and the shot goes to
 * Essential Space instead of the gallery.
 *
 * The camera app already handles KEYCODE_CAMERA (27) as a real shutter press — its
 * CameraUIContext.onKeyUp() calls simulateKeyDownAndUp() which dispatches touch
 * DOWN/UP to the photo shutter button. So the cleanest fix is to rewrite the
 * Essential Key into KEYCODE_CAMERA in PhoneWindowManager while the camera is in
 * the foreground: Nothing's policy no longer recognizes the key (no Essential Space
 * launch), and the camera receives a normal shutter key.
 *
 * Outside the camera nothing is rewritten, so Essential Space keeps working.
 */
object SystemHook {

    private const val TAG = "[EssShutter]"

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pwm = try {
            XposedHelpers.findClass(
                "com.android.server.policy.PhoneWindowManager", lpparam.classLoader
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG PhoneWindowManager not found: ${t.message}")
            return
        }

        val rewrite = object : XC_MethodHook() {
            override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                val index = param.args.indexOfFirst { it is KeyEvent }
                if (index < 0) return
                val event = param.args[index] as KeyEvent
                if (event.scanCode != MainHook.SCAN_ESSENTIAL) return

                val camera = isCameraForeground()
                XposedBridge.log(
                    "$TAG PWM ${param.method.name}: key=${event.keyCode} " +
                            "scan=${event.scanCode} action=${event.action}, " +
                            "camera foreground=$camera"
                )
                if (!camera) return // Essential Space handles it as usual

                param.args[index] = cameraKey(event)
                XposedBridge.log("$TAG rewritten Essential Key -> KEYCODE_CAMERA")
            }
        }

        // Rewrite as early as possible: before queueing (policy decision) and before
        // dispatch (the event that actually reaches the focused window).
        for (name in listOf("interceptKeyBeforeQueueing", "interceptKeyBeforeDispatching")) {
            val count = XposedBridge.hookAllMethods(pwm, name, rewrite)
            XposedBridge.log("$TAG hooked $name ($count overloads)")
        }
    }

    private fun cameraKey(ev: KeyEvent) = KeyEvent(
        ev.downTime,
        ev.eventTime,
        ev.action,
        KeyEvent.KEYCODE_CAMERA,
        ev.repeatCount,
        ev.metaState,
        KeyCharacterMap.VIRTUAL_KEYBOARD,
        0, // scanCode: do not let Nothing's policy recognize the Essential Key
        ev.flags,
        ev.source,
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
