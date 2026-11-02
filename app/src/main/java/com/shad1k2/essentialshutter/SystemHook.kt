package com.shad1k2.essentialshutter

import android.content.Context
import android.view.KeyCharacterMap
import android.view.KeyEvent
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Nothing OS consumes the Essential Key at the system policy level, so the key event
 * never reaches the camera app. Here we rewrite it to KEYCODE_CAMERA (27) — but only
 * while the Nothing Camera is in the foreground.
 */
object SystemHook {

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pwm = try {
            XposedHelpers.findClass(
                "com.android.server.policy.PhoneWindowManager",
                lpparam.classLoader
            )
        } catch (t: Throwable) {
            XposedBridge.log("[EssShutter] PhoneWindowManager not found: ${t.message}")
            return
        }

        XposedBridge.hookAllMethods(pwm, "interceptKeyBeforeDispatching",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val ev = param.args.getOrNull(0) as? KeyEvent ?: return
                    if (ev.scanCode != MainHook.SCAN_ESSENTIAL) return
                    if (!isCameraForeground()) return

                    val replacement = KeyEvent(
                        ev.downTime,
                        ev.eventTime,
                        ev.action,
                        KeyEvent.KEYCODE_CAMERA,
                        ev.repeatCount,
                        ev.metaState,
                        KeyCharacterMap.VIRTUAL_KEYBOARD,
                        0,
                        0,
                        KeyEvent.FLAG_FROM_SYSTEM
                    )
                    param.args[0] = replacement
                    XposedBridge.log("[EssShutter] Essential Key -> KEYCODE_CAMERA action=${ev.action}")
                }
            })
    }

    private fun isCameraForeground(): Boolean {
        return try {
            val ctx = XposedHelpers.callStaticMethod(
                Class.forName("android.app.ActivityThread"),
                "currentApplication"
            ) as? Context ?: return false

            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val tasks = am.appTasks
            tasks.isNotEmpty() &&
                tasks[0].taskInfo?.baseActivity?.packageName == MainHook.CAMERA_PACKAGE
        } catch (t: Throwable) {
            XposedBridge.log("[EssShutter] foreground check failed: ${t.message}")
            false
        }
    }
}
