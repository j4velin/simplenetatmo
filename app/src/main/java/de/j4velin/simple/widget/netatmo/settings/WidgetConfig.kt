package de.j4velin.simple.widget.netatmo.settings

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import de.j4velin.simple.widget.netatmo.R
import de.j4velin.simple.widget.netatmo.Widget
import de.j4velin.simple.widget.netatmo.api.TAG
import kotlinx.android.synthetic.main.widget_config.*

const val DEFAULT_BG_COLOR = 1694498816
const val DEFAULT_TEXT_COLOR = Color.WHITE
const val DEFAULT_TEXT_SIZE = 12f

class WidgetConfig : AbstractConfig(PREF_NAME) {

    companion object {
        const val PREF_NAME = "widgets"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.widget_config)

        background_color.setOnClickListener(colorClickListener)
        background_color.color = prefs.getInt(widgetId + "_background_color", DEFAULT_BG_COLOR)
        text_color.setOnClickListener(colorClickListener)
        text_color.color = prefs.getInt(widgetId + "_text_color", DEFAULT_TEXT_COLOR)
        text_size.setText(prefs.getFloat(widgetId + "_text_size", DEFAULT_TEXT_SIZE).toString())
        show_icons.isChecked = prefs.getBoolean(widgetId + "_show_icons", true)
        show_name.isChecked = prefs.getBoolean(widgetId + "_show_name", true)
        show_temperature.isChecked = prefs.getBoolean(widgetId + "_show_temperature", true)
        show_co2.isChecked = prefs.getBoolean(widgetId + "_show_co2", true)
        show_humidity.isChecked = prefs.getBoolean(widgetId + "_show_humidity", true)
    }

    override fun onPause() {
        super.onPause()
        save(save)
    }

    fun save(view: View) {
        saveGeneralSettings()

        val edit = prefs.edit()
        if (selectedModule == null) {
            Toast.makeText(this, R.string.no_module_selected, Toast.LENGTH_LONG).show()
            return
        } else {
            selectedModule?.let {
                edit.putString(widgetId + "_module_id", it._id)
                edit.putString(widgetId + "_name", it.module_name)
            }
        }
        edit.putInt(widgetId + "_background_color", background_color.color)
        edit.putInt(widgetId + "_text_color", text_color.color)
        try {
            edit.putFloat(widgetId + "_text_size", text_size.text.toString().toFloat())
        } catch (nfe: NumberFormatException) {
            Log.e(TAG, "Given text.size value is not a number: $nfe", nfe)
        }
        edit.putBoolean(widgetId + "_show_icons", show_icons.isChecked)
        edit.putBoolean(widgetId + "_show_name", show_name.isChecked)
        edit.putBoolean(widgetId + "_show_temperature", show_temperature.isChecked)
        edit.putBoolean(widgetId + "_show_co2", show_co2.isChecked)
        edit.putBoolean(widgetId + "_show_humidity", show_humidity.isChecked)

        edit.apply()
        Widget.updateWidget(this, widgetId.toInt())
        finish()
    }
}
