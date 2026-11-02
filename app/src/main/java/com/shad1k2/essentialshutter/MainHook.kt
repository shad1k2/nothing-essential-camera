package com.shad1k2.essentialshutter

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MainHook : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            CAMERA_PACKAGE -> {
                XposedBridge.log("[EssShutter] hooking ${lpparam.packageName}")
                CameraHook.hook(lpparam)
            }
            "android" -> {
                // system_server
                XposedBridge.log("[EssShutter] hooking system_server")
                SystemHook.hook(lpparam)
            }
        }
    }

    companion object {
        const val CAMERA_PACKAGE = "com.nothing.camera"
        const val SCAN_ESSENTIAL = 250
    }
}
