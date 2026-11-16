package com.shad1k2.essentialshutter

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Turns KEYCODE_CAMERA from [SystemHook] into real touch events on the camera's
 * ShutterButton: short press = photo, hold = video, plus the actions selected in the
 * settings screen (focus-only, "hold does nothing", double-press commands).
 */
object CameraHook {

    private const val SHUTTER_CLASS = "com.nothing.camera.ui.commonui.ShutterButton"
    private const val LONG_PRESS_MS = 500L

    private val handler = Handler(Looper.getMainLooper())
    private var shutterButton: View? = null
    private var downTime = 0L
    private var uiEventProxy: Any? = null

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        registerCommands(lpparam)
        hookUiEventProxy(lpparam)

        XposedBridge.hookAllMethods(
            Activity::class.java,
            "dispatchKeyEvent",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val ev = param.args.firstOrNull { it is KeyEvent } as? KeyEvent ?: return
                    if (ev.keyCode != KeyEvent.KEYCODE_CAMERA) return

                    val activity = param.thisObject as Activity
                    if (activity.packageName != MainHook.CAMERA_PACKAGE) return
                    // Consume the key: our touches drive the shutter, the native key
                    // handler must not also fire.
                    param.result = true

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
                            XposedBridge.log("[EssShutter] shutter DOWN (hold to record)")
                        }
                        KeyEvent.ACTION_UP -> {
                            val btn = shutterButton ?: return
                            val held = SystemClock.uptimeMillis() - downTime
                            val action = when {
                                held >= LONG_PRESS_MS && Prefs.longAction == Prefs.LONG_NONE ->
                                    MotionEvent.ACTION_CANCEL
                                held < LONG_PRESS_MS && Prefs.shortAction == Prefs.SHORT_FOCUS ->
                                    MotionEvent.ACTION_CANCEL
                                else -> MotionEvent.ACTION_UP
                            }
                            dispatchTouch(btn, action)
                            shutterButton = null
                            XposedBridge.log("[EssShutter] shutter UP, held=${held}ms -> $action")
                        }
                    }
                }
            })
    }

    private fun registerCommands(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val app = XposedHelpers.callStaticMethod(
                Class.forName("android.app.ActivityThread"), "currentApplication"
            ) as? Context ?: return
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.getStringExtra(Actions.EXTRA)) {
                        Actions.FOCUS -> focusOnly()
                        Actions.SWITCH_CAMERA -> switchCamera()
                    }
                }
            }
            val filter = IntentFilter(Actions.BROADCAST)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                app.registerReceiver(receiver, filter)
            }
            XposedBridge.log("[EssShutter] command receiver registered")
        } catch (t: Throwable) {
            XposedBridge.log("[EssShutter] command receiver failed: ${t.message}")
        }
    }

    private fun hookUiEventProxy(lpparam: XC_LoadPackage.LoadPackageParam) {
        try {
            val cls = XposedHelpers.findClass(
                "com.nothing.camera.scheduler.UiEventProxy", lpparam.classLoader
            )
            XposedBridge.hookAllConstructors(cls, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    uiEventProxy = param.thisObject
                }
            })
        } catch (t: Throwable) {
            XposedBridge.log("[EssShutter] UiEventProxy hook failed: ${t.message}")
        }
    }

    private fun focusOnly() {
        val btn = shutterButton
        if (btn == null) {
            XposedBridge.log("[EssShutter] focus: no shutter view")
            return
        }
        try {
            XposedHelpers.callMethod(btn, "callShutterButtonFocus", true)
            handler.postDelayed({
                try {
                    XposedHelpers.callMethod(btn, "callShutterButtonFocus", false)
                } catch (t: Throwable) {
                    XposedBridge.log("[EssShutter] focus release failed: ${t.message}")
                }
            }, 250L)
            XposedBridge.log("[EssShutter] focus command executed")
        } catch (t: Throwable) {
            XposedBridge.log("[EssShutter] focus command failed: ${t.message}")
        }
    }

    private fun switchCamera() {
        val proxy = uiEventProxy
        if (proxy == null) {
            XposedBridge.log("[EssShutter] switch camera: proxy unavailable")
            return
        }
        try {
            XposedHelpers.callMethod(proxy, "onSwitchCameraClick")
            XposedBridge.log("[EssShutter] switch camera executed")
        } catch (t: Throwable) {
            XposedBridge.log("[EssShutter] switch camera failed: ${t.message}")
        }
    }

    private fun dispatchTouch(view: View, action: Int) {
        val now = SystemClock.uptimeMillis()
        val ev = MotionEvent.obtain(
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_CANCEL) now else downTime,
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
