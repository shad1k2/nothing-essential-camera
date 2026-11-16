package com.shad1k2.essentialshutter

import de.robv.android.xposed.XSharedPreferences

/** Shared settings between the settings Activity and both hooks. */
object Prefs {
    const val PACKAGE = "com.shad1k2.essentialshutter"
    const val FILE = "settings"

    const val KEY_ENABLED = "enabled"
    const val KEY_CAMERA_ONLY = "camera_only"
    const val KEY_SHORT = "short_action"
    const val KEY_LONG = "long_action"
    const val KEY_DOUBLE = "double_action"
    const val KEY_VOLUME_ZOOM = "volume_zoom"
    const val KEY_CONFIG_VERSION = "config_version"
    const val CURRENT_VERSION = 2

    // short action
    const val SHORT_PHOTO = 0
    const val SHORT_FOCUS = 1

    // long action
    const val LONG_VIDEO = 0
    const val LONG_PHOTO = 1
    const val LONG_NONE = 2

    // double press action
    const val DOUBLE_NONE = 0
    const val DOUBLE_SWITCH_CAMERA = 1
    const val DOUBLE_ZOOM_IN = 2

    private fun prefs(): XSharedPreferences? = try {
        XSharedPreferences(PACKAGE, FILE).also { it.reload() }
    } catch (t: Throwable) {
        null
    }

    val enabled: Boolean get() = prefs()?.getBoolean(KEY_ENABLED, true) ?: true
    val cameraOnly: Boolean get() = migratedPrefs()?.getBoolean(KEY_CAMERA_ONLY, true) ?: true
    val shortAction: Int get() = migratedPrefs()?.getInt(KEY_SHORT, SHORT_PHOTO) ?: SHORT_PHOTO
    val longAction: Int get() = migratedPrefs()?.getInt(KEY_LONG, LONG_VIDEO) ?: LONG_VIDEO
    val doubleAction: Int get() = migratedPrefs()?.getInt(KEY_DOUBLE, DOUBLE_NONE) ?: DOUBLE_NONE
    val volumeZoom: Boolean get() = migratedPrefs()?.getBoolean(KEY_VOLUME_ZOOM, false) ?: false

    /**
     * v1.1 shipped with bad defaults (double press = zoom, volume zoom on) which made
     * the camera-switch button fire on every press and the volume keys take photos.
     * Detect an old config without the version marker and ignore it.
     */
    private fun migratedPrefs(): XSharedPreferences? {
        val p = prefs() ?: return null
        if (p.getInt(KEY_CONFIG_VERSION, 0) < CURRENT_VERSION) return null
        return p
    }
}
