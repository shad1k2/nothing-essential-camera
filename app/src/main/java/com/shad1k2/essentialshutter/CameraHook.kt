package com.shad1k2.essentialshutter

import android.app.Activity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Inside the Nothing Camera app: the key down focuses, the key up fires the shutter —
 * mirroring the iPhone Camera Control behaviour. The KEYCODE_CAMERA event arrives
 * injected by [SystemHook] while the camera is in the foreground.
 */
object CameraHook {

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.hookAllMethods(
            Activity::class.java,
            "dispatchKeyEvent",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val ev = param.args.firstOrNull { it is KeyEvent } as? KeyEvent ?: return
                    if (ev.keyCode != KeyEvent.KEYCODE_CAMERA) return

                    val activity = param.thisObject as Activity
                    if (activity.packageName != MainHook.CAMERA_PACKAGE) return

                    when (ev.action) {
                        KeyEvent.ACTION_DOWN ->
                            if (ev.repeatCount == 0) {
                                XposedBridge.log("[EssShutter] focus (camera key down)")
                            }
                        KeyEvent.ACTION_UP -> {
                            // Consume the event so the app does not see a raw camera key.
                            param.result = true
                            shutter(activity)
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

    /**
     * Walk the view hierarchy and pick the most likely shutter control: a clickable,
     * enabled, visible view whose id or contentDescription mentions shutter / capture /
     * photo. Recurse into ViewGroups too, since the shutter may be a container.
     */
    private fun findShutter(root: View): View? {
        var best: View? = null

        fun matches(v: View): Boolean {
            if (!v.isClickable || !v.isEnabled || v.visibility != View.VISIBLE) return false
            val name = try {
                v.resources.getResourceEntryName(v.id)
            } catch (t: Throwable) {
                ""
            }
            val desc = v.contentDescription?.toString()?.lowercase() ?: ""
            return name.contains("shutter", true) ||
                    name.contains("capture", true) ||
                    name.contains("photo", true) ||
                    desc.contains("shutter") ||
                    desc.contains("capture")
        }

        fun walk(v: View) {
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
            if (matches(v)) best = v
        }

        walk(root)
        return best
    }
}
