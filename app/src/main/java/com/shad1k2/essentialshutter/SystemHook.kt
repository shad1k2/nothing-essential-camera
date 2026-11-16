package com.shad1k2.essentialshutter

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * Nothing OS claims the Essential Key (keyCode 0 / scanCode 250) in PhoneWindowManager
 * and launches `ntessentialrecorder/.FocusActivity`, so the shot goes to Essential Space.
 *
 * We swallow that key at policy level while the camera is foreground (result = 0 skips
 * Nothing's policy), then drive the camera with injected keys. Actions are configured by
 * the in-app Settings screen ([Prefs]). Volume keys become a virtual zoom slider while
 * the Essential Key is held.
 */
object SystemHook {

    private const val TAG = "[EssShutter]"
    private const val DOUBLE_WINDOW_MS = 320L

    private val handler = Handler(Looper.getMainLooper())
    private var pressCount = 0
    private var lastDown = 0L
    private var downInjected = false
    private var pendingDown: Runnable? = null
    private var essentialHeld = false
    private var context: Context? = null

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        context = try {
            XposedHelpers.callStaticMethod(
                Class.forName("android.app.ActivityThread"), "currentApplication"
            ) as? Context
        } catch (t: Throwable) {
            null
        }

        val pwm = try {
            XposedHelpers.findClass(
                "com.android.server.policy.PhoneWindowManager", lpparam.classLoader
            )
        } catch (t: Throwable) {
            XposedBridge.log("$TAG PhoneWindowManager not found: ${t.message}")
            return
        }

        XposedBridge.hookAllMethods(
            pwm,
            "interceptKeyBeforeQueueing",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: XC_MethodHook.MethodHookParam) {
                    val ev = param.args.firstOrNull { it is KeyEvent } as? KeyEvent ?: return

                    if (!Prefs.enabled) return // module off: Essential Space works normally

                    if (ev.scanCode == MainHook.SCAN_ESSENTIAL) {
                        handleEssential(ev, param)
                    } else if (essentialHeld && Prefs.volumeZoom && ev.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                        essentialHeld && Prefs.volumeZoom && ev.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                    ) {
                        handleVolumeAsZoom(ev, param)
                    }
                }
            })
        XposedBridge.log("$TAG hooked interceptKeyBeforeQueueing")
    }

    private fun handleEssential(ev: KeyEvent, param: XC_MethodHook.MethodHookParam) {
        val camera = isCameraForeground()
        if (Prefs.cameraOnly && !camera) return
        param.result = 0 // no FocusActivity launch, no dispatch of the original key

        val now = SystemClock.uptimeMillis()
        if (ev.action == KeyEvent.ACTION_DOWN) {
            essentialHeld = true
            pressCount = if (now - lastDown < DOUBLE_WINDOW_MS) pressCount + 1 else 1
            lastDown = now
            XposedBridge.log("$TAG Essential DOWN, pressCount=$pressCount, camera=$camera")

            if (!camera || Prefs.doubleAction == Prefs.DOUBLE_NONE) {
                inject(KeyEvent.ACTION_DOWN)
            } else if (pressCount == 1) {
                pendingDown = Runnable {
                    inject(KeyEvent.ACTION_DOWN)
                    pendingDown = null
                }.also { handler.postDelayed(it, DOUBLE_WINDOW_MS) }
            } else {
                pendingDown?.let { handler.removeCallbacks(it) }
                pendingDown = null
            }
        } else if (ev.action == KeyEvent.ACTION_UP) {
            essentialHeld = false
            XposedBridge.log("$TAG Essential UP, pressCount=$pressCount")

            if (pressCount >= 2 && camera && Prefs.doubleAction != Prefs.DOUBLE_NONE) {
                pendingDown?.let { handler.removeCallbacks(it) }
                pendingDown = null
                if (downInjected) inject(KeyEvent.ACTION_UP)
                performDoubleAction(Prefs.doubleAction)
            } else {
                pendingDown?.let { handler.removeCallbacks(it) }
                pendingDown = null
                if (!downInjected) inject(KeyEvent.ACTION_DOWN) // very short tap
                inject(KeyEvent.ACTION_UP)
            }
            pressCount = 0
            downInjected = false
        }
    }

    private fun handleVolumeAsZoom(ev: KeyEvent, param: XC_MethodHook.MethodHookParam) {
        if (!isCameraForeground()) return
        param.result = 0
        if (ev.action != KeyEvent.ACTION_DOWN) return
        val code = if (ev.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            KeyEvent.KEYCODE_ZOOM_IN
        } else {
            KeyEvent.KEYCODE_ZOOM_OUT
        }
        injectRaw(code, KeyEvent.ACTION_DOWN)
        injectRaw(code, KeyEvent.ACTION_UP)
        XposedBridge.log("$TAG volume -> zoom key=$code")
    }

    private fun performDoubleAction(action: Int) {
        when (action) {
            Prefs.DOUBLE_SWITCH_CAMERA -> {
                val ctx = context
                if (ctx != null) {
                    val intent = Intent(Actions.BROADCAST)
                        .setPackage(MainHook.CAMERA_PACKAGE)
                        .putExtra(Actions.EXTRA, Actions.SWITCH_CAMERA)
                    ctx.sendBroadcast(intent)
                    XposedBridge.log("$TAG double press -> switch camera")
                }
            }
            Prefs.DOUBLE_ZOOM_IN -> {
                injectRaw(KeyEvent.KEYCODE_ZOOM_IN, KeyEvent.ACTION_DOWN)
                injectRaw(KeyEvent.KEYCODE_ZOOM_IN, KeyEvent.ACTION_UP)
                XposedBridge.log("$TAG double press -> zoom in")
            }
        }
    }

    private fun inject(action: Int) {
        downInjected = action == KeyEvent.ACTION_DOWN
        injectRaw(KeyEvent.KEYCODE_CAMERA, action)
    }

    private fun injectRaw(code: Int, action: Int) {
        val now = SystemClock.uptimeMillis()
        try {
            val im = XposedHelpers.callStaticMethod(InputManager::class.java, "getInstance")
            XposedHelpers.callMethod(
                im,
                "injectInputEvent",
                KeyEvent(
                    now, now, action, code, 0, 0,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD
                ),
                0
            )
            XposedBridge.log("$TAG injected keyCode=$code action=$action")
        } catch (t: Throwable) {
            XposedBridge.log("$TAG injection failed: ${t.message}")
        }
    }

    private fun isCameraForeground(): Boolean = topActivityPackage() == MainHook.CAMERA_PACKAGE

    /** Top-of-stack package resolved inside system_server. */
    private fun topActivityPackage(): String? {
        val sources = listOf(
            "android.app.ActivityTaskManager" to "IActivityTaskManagerSingleton",
            "android.app.ActivityManager" to "IActivityManagerSingleton",
        )
        for ((className, fieldName) in sources) {
            try {
                val singleton = XposedHelpers.getStaticObjectField(
                    Class.forName(className), fieldName
                )
                val service = XposedHelpers.callMethod(singleton, "get")
                val tasks = XposedHelpers.callMethod(service, "getTasks", 1)
                val list = when (tasks) {
                    is List<*> -> tasks
                    else -> XposedHelpers.callMethod(tasks, "getList") as? List<*> ?: continue
                }
                val top = XposedHelpers.getObjectField(
                    list.firstOrNull() ?: continue, "topActivity"
                ) as? ComponentName ?: continue
                return top.packageName
            } catch (t: Throwable) {
                XposedBridge.log("$TAG top-activity via $className failed: ${t.message}")
            }
        }
        return null
    }
}
