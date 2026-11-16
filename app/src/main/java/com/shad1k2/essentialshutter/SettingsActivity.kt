package com.shad1k2.essentialshutter

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** In-app settings for the Essential Key actions. Launchable from LSPosed. */
class SettingsActivity : Activity() {

    private lateinit var enabled: Switch
    private lateinit var cameraOnly: Switch
    private lateinit var shortAction: Spinner
    private lateinit var longAction: Spinner
    private lateinit var doubleAction: Spinner
    private lateinit var volumeZoom: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Essential Key · Camera"

        val p = getSharedPreferences(Prefs.FILE, MODE_PRIVATE)
        val pad = 40

        fun switch(label: String, checked: Boolean) = Switch(this).apply {
            text = label
            isChecked = checked
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = pad / 2; bottomMargin = pad / 2 }
        }

        fun spinner(items: Array<String>, selection: Int) = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SettingsActivity,
                android.R.layout.simple_spinner_item,
                items
            ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            setSelection(selection)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = pad }
        }

        enabled = switch("Module enabled", p.getBoolean(Prefs.KEY_ENABLED, true))
        cameraOnly = switch(
            "Only remap while the camera is in the foreground (keep Essential Space elsewhere)",
            p.getBoolean(Prefs.KEY_CAMERA_ONLY, true)
        )
        shortAction = spinner(
            arrayOf("Short press: photo", "Short press: focus only"),
            p.getInt(Prefs.KEY_SHORT, Prefs.SHORT_PHOTO)
        )
        longAction = spinner(
            arrayOf("Hold: record video", "Hold: photo", "Hold: nothing"),
            p.getInt(Prefs.KEY_LONG, Prefs.LONG_VIDEO)
        )
        doubleAction = spinner(
            arrayOf("Double press: nothing", "Double press: switch camera", "Double press: zoom in"),
            p.getInt(Prefs.KEY_DOUBLE, Prefs.DOUBLE_NONE)
        )
        volumeZoom = switch(
            "While the key is held: volume buttons = zoom",
            p.getBoolean(Prefs.KEY_VOLUME_ZOOM, true)
        )

        val save = Button(this).apply {
            text = "Save"
            setOnClickListener {
                p.edit()
                    .putBoolean(Prefs.KEY_ENABLED, enabled.isChecked)
                    .putBoolean(Prefs.KEY_CAMERA_ONLY, cameraOnly.isChecked)
                    .putInt(Prefs.KEY_SHORT, shortAction.selectedItemPosition)
                    .putInt(Prefs.KEY_LONG, longAction.selectedItemPosition)
                    .putInt(Prefs.KEY_DOUBLE, doubleAction.selectedItemPosition)
                    .putBoolean(Prefs.KEY_VOLUME_ZOOM, volumeZoom.isChecked)
                    .apply()
                Toast.makeText(this@SettingsActivity, "Saved", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        val reset = Button(this).apply {
            text = "Reset"
            setOnClickListener {
                p.edit().clear().apply()
                Toast.makeText(this@SettingsActivity, "Defaults restored", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        val info = TextView(this).apply {
            text = "Tip: a quick tap takes a photo, holding records video. " +
                    "Double-press and volume-zoom require the module to stay enabled. " +
                    "Force-stop the camera app after changing settings."
            textSize = 13f
            setPadding(0, pad, 0, pad)
        }

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(save, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(reset, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(TextView(this@SettingsActivity).apply {
                text = "Essential Key · Camera Control"
                textSize = 20f
            })
            addView(enabled)
            addView(cameraOnly)
            addView(TextView(this@SettingsActivity).apply {
                text = "Short press"
                textSize = 15f
                setPadding(0, pad, 0, 0)
            })
            addView(shortAction)
            addView(TextView(this@SettingsActivity).apply {
                text = "Hold"
                textSize = 15f
            })
            addView(longAction)
            addView(TextView(this@SettingsActivity).apply {
                text = "Double press"
                textSize = 15f
            })
            addView(doubleAction)
            addView(volumeZoom)
            addView(info)
            addView(buttons)
        }

        setContentView(ScrollView(this).apply { addView(root) })
    }
}
