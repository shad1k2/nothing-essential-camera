package com.shad1k2.essentialshutter

import android.app.Activity
import android.view.KeyEvent
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Read-only observer: confirm that the KEYCODE_CAMERA event rewritten by [SystemHook]
 * actually arrives in the camera app. The app handles it natively — CameraUIContext
 * turns the key into a real shutter press — so this hook deliberately does NOT
 * consume the event or click any view.
 */
object CameraHook {

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.hookAllMethods(
            Activity::class.java,
            "dispatchKeyEvent",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val ev = param.args.firstOrNull { it is KeyEvent } as? KeyEvent ?: return
                    if (ev.keyCode != KeyEvent.KEYCODE_CAMERA) return
                    XposedBridge.log(
                        "[EssShutter] camera received KEYCODE_CAMERA action=${ev.action} " +
                                "(native shutter path, not consumed)"
                    )
                }
            })
    }
}
