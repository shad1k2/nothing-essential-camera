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

        /** Packages that implement the Essential Space side of the Essential Key. */
        const val RECORDER_PACKAGE = "com.nothing.ntessentialrecorder"
        const val SPACE_PACKAGE = "com.nothing.ntessentialspace"

        /**
         * Transparent activity the system launches when the key is pressed.
         * Device logs show the launch goes through system_server regardless of
         * whether the Essential Space packages are disabled.
         */
        const val FOCUS_ACTIVITY = "FocusActivity"

        /** Legacy scan code of the Essential Key (keyCode 0 / KEYCODE_UNKNOWN). Kept for logging. */
        const val SCAN_ESSENTIAL = 250
    }
}
