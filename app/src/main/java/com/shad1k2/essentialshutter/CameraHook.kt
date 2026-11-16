package com.shad1k2.essentialshutter

import android.app.Activity
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Turns KEYCODE_CAMERA from [SystemHook] into real touch events on the camera's
 * ShutterButton. Short press = photo, hold = video (quick record) — exactly the same
 * path as touching the on-screen shutter, because the camera's own listener sees the
 * DOWN/UP events and its long-click handler starts/stops recording.
 */
object CameraHook {

    private const val SHUTTER_CLASS = "com.nothing.camera.ui.commonui.ShutterButton"
    private var shutterButton: View? = null
    private var downTime = 0L

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        XposedBridge.hookAllMethods(
            Activity::class.java,
            "dispatchKeyEvent",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val ev = param.args.firstOrNull { it is KeyEvent } as? KeyEvent ?: return
                    if (ev.keyCode != KeyEvent.KEYCODE_CAMERA) return

                    val activity = param.thisObject as Activity
                    if (activity.packageName != MainHook.CAMERA_PACKAGE) return

                    when (ev.action) {
                        KeyEvent.ACTION_DOWN -> {
                            val btn = activity.window?.decorView?.let(::findShutter)
                            shutterButton = btn
                            if (btn == null) {
                                XposedBridge.log("[EssShutter] shutter button not found")
                                return
                            }
                            downTime = SystemClock.uptimeMillis()
                            dispatchTouch(btn, MotionEvent.ACTION_DOWN)
                            XposedBridge.log(
                                "[EssShutter] shutter DOWN -> ${btn.javaClass.simpleName}, hold to record"
                            )
                        }
                        KeyEvent.ACTION_UP -> {
                            val btn = shutterButton
                            if (btn == null) {
                                XposedBridge.log("[EssShutter] no pending shutter DOWN")
                                return
                            }
                            dispatchTouch(btn, MotionEvent.ACTION_UP)
                            shutterButton = null
                            XposedBridge.log("[EssShutter] shutter UP")
                        }
                    }
                    // Do not consume the key: the camera's own onKeyUp native shutter
                    // path would see it too, but our touch already triggered the same
                    // listener — see the note in README. Consumers are filtered below.
                    param.result = true
                }
            })
    }

    private fun dispatchTouch(view: View, action: Int) {
        val now = SystemClock.uptimeMillis()
        val ev = MotionEvent.obtain(
            if (action == MotionEvent.ACTION_DOWN) now else downTime,
            now,
            action,
            view.width / 2f,
            view.height / 2f,
            0
        )
        view.dispatchTouchEvent(ev)
        ev.recycle()
    }

    /** Find the real ShutterButton by class name, with an id/description fallback. */
    private fun findShutter(root: View): View? {
        var classHit: View? = null
        var nameHit: View? = null

        fun walk(v: View) {
            if (v.visibility == View.VISIBLE) {
                val simpleName = v.javaClass.simpleName
                if (v.javaClass.name == SHUTTER_CLASS || simpleName.endsWith("ShutterButton")) {
                    classHit = v
                } else if (matchesName(v)) {
                    nameHit = v
                }
            }
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
        }

        walk(root)
        return classHit ?: nameHit
    }

    private fun matchesName(v: View): Boolean {
        if (!v.isClickable || !v.isEnabled) return false
        val name = try {
            v.resources.getResourceEntryName(v.id)
        } catch (t: Throwable) {
            ""
        }
        val desc = v.contentDescription?.toString()?.lowercase() ?: ""
        return name.contains("shutter", true) ||
                name.contains("capture", true) ||
                desc.contains("shutter") ||
                desc.contains("capture")
    }
}
