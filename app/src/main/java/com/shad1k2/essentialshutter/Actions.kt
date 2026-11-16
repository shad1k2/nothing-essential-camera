package com.shad1k2.essentialshutter

/** Command bus from system_server to the camera process. */
object Actions {
    const val BROADCAST = "com.shad1k2.essentialshutter.CAMERA_ACTION"
    const val EXTRA = "action"

    const val FOCUS = "focus"
    const val SWITCH_CAMERA = "switch_camera"
}
