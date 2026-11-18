package com.shad1k2.essentialshutter

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView

/** In-app settings screen, styled after Nothing OS (NDot wordmark + Geist body). */
class SettingsActivity : AppCompatActivity() {

    private lateinit var enabled: MaterialSwitch
    private lateinit var cameraOnly: MaterialSwitch
    private lateinit var volumeZoom: MaterialSwitch
    private lateinit var shortAction: MaterialAutoCompleteTextView
    private lateinit var longAction: MaterialAutoCompleteTextView
    private lateinit var doubleAction: MaterialAutoCompleteTextView

    private val shortOptions = arrayOf("Short press: photo", "Short press: focus only")
    private val longOptions = arrayOf("Hold: record video", "Hold: photo", "Hold: nothing")
    private val doubleOptions = arrayOf(
        "Double press: nothing",
        "Double press: switch camera",
        "Double press: zoom in",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        title = "Essential Key"

        val p = getSharedPreferences(Prefs.FILE, MODE_PRIVATE)
        enabled = findViewById(R.id.sw_enabled)
        cameraOnly = findViewById(R.id.sw_camera_only)
        volumeZoom = findViewById(R.id.sw_volume_zoom)
        shortAction = findViewById(R.id.dd_short)
        longAction = findViewById(R.id.dd_long)
        doubleAction = findViewById(R.id.dd_double)

        enabled.isChecked = p.getBoolean(Prefs.KEY_ENABLED, true)
        cameraOnly.isChecked = p.getBoolean(Prefs.KEY_CAMERA_ONLY, true)
        volumeZoom.isChecked = p.getBoolean(Prefs.KEY_VOLUME_ZOOM, false)

        setupDropdown(shortAction, shortOptions, p.getInt(Prefs.KEY_SHORT, Prefs.SHORT_PHOTO))
        setupDropdown(longAction, longOptions, p.getInt(Prefs.KEY_LONG, Prefs.LONG_VIDEO))
        setupDropdown(doubleAction, doubleOptions, p.getInt(Prefs.KEY_DOUBLE, Prefs.DOUBLE_NONE))

        findViewById<MaterialButton>(R.id.btn_save).setOnClickListener {
            p.edit()
                .putBoolean(Prefs.KEY_ENABLED, enabled.isChecked)
                .putBoolean(Prefs.KEY_CAMERA_ONLY, cameraOnly.isChecked)
                .putBoolean(Prefs.KEY_VOLUME_ZOOM, volumeZoom.isChecked)
                .putInt(Prefs.KEY_SHORT, selectedIndex(shortAction, shortOptions))
                .putInt(Prefs.KEY_LONG, selectedIndex(longAction, longOptions))
                .putInt(Prefs.KEY_DOUBLE, selectedIndex(doubleAction, doubleOptions))
                .putInt(Prefs.KEY_CONFIG_VERSION, Prefs.CURRENT_VERSION)
                .apply()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            finish()
        }

        findViewById<MaterialButton>(R.id.btn_reset).setOnClickListener {
            p.edit().clear().apply()
            Toast.makeText(this, "Defaults restored", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupDropdown(view: MaterialAutoCompleteTextView, options: Array<String>, current: Int) {
        view.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, options)
        )
        view.setText(options.getOrElse(current) { options[0] }, false)
        view.setOnClickListener { view.showDropDown() }
    }

    private fun selectedIndex(view: MaterialAutoCompleteTextView, options: Array<String>): Int =
        options.indexOf(view.text.toString()).coerceAtLeast(0)
}
