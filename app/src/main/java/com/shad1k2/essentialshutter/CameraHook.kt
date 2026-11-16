package com.shad1k2.essentialshutter

import android.app.Activity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodHook.MethodHookParam
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Inside the Nothing Camera app: the key down focuses, the key up fires the shutter —
 * mirroring the iPhone Camera Control behaviour. The KEYCODE_CAMERA event arrives
 * injected by [SystemHook] while the camera is in the foreground.
 *
 * The shutter is a custom view, `com.nothing.camera.ui.commonui.ShutterButton`, whose
 * `performClick()` calls the listener's `onShutterButtonClick()` — the real capture
 * path. Its `callShutterButtonFocus(boolean)` drives the focus step.
 */
object CameraHook {

    private const val TAG = "[EssShutter]"
    private const val SHUTTER_CLASS = "com.nothing.camera.ui.commonui.ShutterButton"
    private const val UI_NODE_CLASS = "com.nothing.camera.ui.uinode.CameraBottomShutterGroupUINode"

    @Volatile
    private var shutterButton: View? = null

    @Volatile
    private var uiNode: Any? = null

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        // Fallback capture path: the app's own UI-node handler, if the view click fails.
        try {
            val nodeClass = XposedHelpers.findClass(UI_NODE_CLASS, lpparam.classLoader)
            XposedHelpers.findAndHookConstructor(nodeClass, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    uiNode = param.thisObject
                }
            })
            XposedBridge.log("$TAG hooked CameraBottomShutterGroupUINode")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG UI node hook unavailable: ${t.message}")
        }

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
                        KeyEvent.ACTION_DOWN ->
                            if (ev.repeatCount == 0) {
                                val btn = activity.window?.decorView?.let(::findShutter)
                                shutterButton = btn
                                XposedBridge.log(
                                    "$TAG focus (camera key down), shutter view: ${btn?.javaClass?.simpleName}"
                                )
                                if (btn != null) {
                                    try {
                                        XposedHelpers.callMethod(btn, "callShutterButtonFocus", true)
                                    } catch (t: Throwable) {
                                        XposedBridge.log("$TAG focus call failed: ${t.message}")
                                    }
                                }
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
            val btn = shutterButton ?: activity.window?.decorView?.let(::findShutter)
            var clicked = false
            if (btn != null) {
                try {
                    XposedHelpers.callMethod(btn, "callShutterButtonFocus", false)
                } catch (t: Throwable) {
                    XposedBridge.log("$TAG focus release failed: ${t.message}")
                }
                try {
                    val result = XposedHelpers.callMethod(btn, "performClick") as? Boolean
                    clicked = result == true
                    XposedBridge.log("$TAG shutter performClick -> $result")
                } catch (t: Throwable) {
                    XposedBridge.log("$TAG shutter performClick failed: ${t.message}")
                }
            } else {
                XposedBridge.log("$TAG shutter button not found in view hierarchy")
            }

            if (!clicked) fireViaUiNode()
        }
    }

    /** Last resort: call the camera's own `shutterOnClick()` handler on its UI node. */
    private fun fireViaUiNode() {
        val node = uiNode ?: return
        try {
            val result = XposedHelpers.callMethod(node, "shutterOnClick") as? Boolean
            XposedBridge.log("$TAG shutterOnClick via UI node -> $result")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG shutterOnClick via UI node failed: ${t.message}")
        }
    }

    /**
     * Locate the real shutter view. The class name is the reliable discriminator on
     * Nothing OS; id/contentDescription heuristics are kept as a fallback.
     */
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
