package com.shad1k2.essentialshutter

import android.app.Activity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Inside the Nothing Camera app: light press = focus, full press (release) = shutter,
 * mirroring the iPhone Camera Control behaviour.
 */
object CameraHook {

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.hookAllMethods(Activity::class.java, "dispatchKeyEvent",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val ev = param.args.getOrNull(1) as? KeyEvent ?: return
                    if (ev.keyCode != KeyEvent.KEYCODE_CAMERA) return

                    val activity = param.thisObject as Activity
                    if (activity.packageName != MainHook.CAMERA_PACKAGE) return

                    when {
                        ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0 -> {
                            // light press: focus only
                            XposedBridge.log("[EssShutter] focus")
                        }
                        ev.action == KeyEvent.ACTION_UP -> {
                            shutter(activity)
                            param.result = true
                        }
                    }
                }
            })
    }

    private fun shutter(activity: Activity) {
        activity.runOnUiThread {
            val root = activity.window?.decorView ?: return@runOnUiThread
            val btn = findShutter(root)
            if (btn != null) {
                btn.performClick()
                XposedBridge.log("[EssShutter] shutter clicked: id=${btn.id}")
            } else {
                XposedBridge.log("[EssShutter] shutter button not found in view hierarchy")
            }
        }
    }

    private fun findShutter(root: View): View? {
        var best: View? = null
        fun walk(v: View) {
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) walk(v.getChildAt(i))
                return
            }
            if (v !is View || !v.isClickable || !v.isEnabled) return
            if (v.visibility != View.VISIBLE) return
            val name = try {
                v.resources.getResourceEntryName(v.id)
            } catch (t: Throwable) {
                ""
            }
            val desc = v.contentDescription?.toString()?.lowercase() ?: ""
            if (name.contains("shutter", true) ||
                name.contains("capture", true) ||
                name.contains("photo", true) ||
                desc.contains("shutter") ||
                desc.contains("capture") ||
                desc.contains("съём")
            ) {
                best = v
            }
        }
        walk(root)
        return best
    }
}
