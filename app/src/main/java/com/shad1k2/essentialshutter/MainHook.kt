package com.shad1k2.essentialshutter

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            "android" -> {
                // system_server: Nothing claims the Essential Key here and launches the
                // Essential Space capture activity, so this is where we intercept it.
                XposedBridge.log("[EssShutter] hooking system_server")
                SystemHook.hook(lpparam)
            }
            CAMERA_PACKAGE -> {
                XposedBridge.log("[EssShutter] hooking ${lpparam.packageName}")
                CameraHook.hook(lpparam)
            }
        }
    }

    companion object {
        const val CAMERA_PACKAGE = "com.nothing.camera"

        /** Essential Key scan code (arrives with keyCode 0 / KEYCODE_UNKNOWN). */
        const val SCAN_ESSENTIAL = 250
    }
}
