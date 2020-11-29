package de.j4velin.simple.widget.netatmo.settings

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import de.j4velin.simple.widget.netatmo.GraphWidget
import de.j4velin.simple.widget.netatmo.R
import de.j4velin.simple.widget.netatmo.api.TAG
import kotlinx.android.synthetic.main.graph_widget_config.*
import kotlinx.android.synthetic.main.graph_widget_config.save
import kotlinx.android.synthetic.main.widget_config.*
import kotlinx.android.synthetic.main.widget_config.background_color
import kotlinx.android.synthetic.main.widget_config.show_co2
import kotlinx.android.synthetic.main.widget_config.show_humidity
import kotlinx.android.synthetic.main.widget_config.show_name
import kotlinx.android.synthetic.main.widget_config.show_temperature
import kotlinx.android.synthetic.main.widget_config.text_color
import kotlinx.android.synthetic.main.widget_config.text_size

const val DEFAULT_LIMIT = 20
const val DEFAULT_SCALE = 5
val SCALE_VALUES = listOf(5, 10, 15, 30, 60)

class GraphWidgetConfig : AbstractConfig(PREF_NAME) {

    companion object {
        const val PREF_NAME = "graphwidgets"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.graph_widget_config)

        background_color.setOnClickListener(colorClickListener)
        background_color.color = prefs.getInt(widgetId + "_background_color", DEFAULT_BG_COLOR)
        text_color.setOnClickListener(colorClickListener)
        text_color.color = prefs.getInt(widgetId + "_text_color", DEFAULT_TEXT_COLOR)
        text_size.setText(prefs.getFloat(widgetId + "_text_size", DEFAULT_TEXT_SIZE).toString())
        show_name.isChecked = prefs.getBoolean(widgetId + "_show_name", true)
        show_temperature.isChecked = prefs.getBoolean(widgetId + "_show_temperature", true)
        temperature_color.setOnClickListener(colorClickListener)
        temperature_color.color = prefs.getInt(widgetId + "_color_temperature", DEFAULT_TEXT_COLOR)
        show_co2.isChecked = prefs.getBoolean(widgetId + "_show_co2", true)
        co2_color.setOnClickListener(colorClickListener)
        co2_color.color = prefs.getInt(widgetId + "_color_co2", DEFAULT_TEXT_COLOR)
        show_humidity.isChecked = prefs.getBoolean(widgetId + "_show_humidity", true)
        humidity_color.setOnClickListener(colorClickListener)
        humidity_color.color = prefs.getInt(widgetId + "_color_humidity", DEFAULT_TEXT_COLOR)
        limit.setText(prefs.getInt(widgetId + "_limit", DEFAULT_LIMIT).toString())
        show_latest.isChecked = prefs.getBoolean(widgetId + "_show_latest", true)

        val adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            SCALE_VALUES.map { s -> "$s min" })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        scale.adapter = adapter
        scale.setSelection(
            SCALE_VALUES.indexOf(prefs.getInt(widgetId + "_scale", DEFAULT_SCALE))
        )
    }

    override fun onPause() {
        super.onPause()
        save(save)
    }

    fun save(view: View) {
        saveGeneralSettings()

        val edit = prefs.edit()
        if (selectedModule == null || selectedStation == null) {
            Toast.makeText(this, R.string.no_module_selected, Toast.LENGTH_LONG).show()
            return
        } else {
            selectedModule?.let {
                edit.putString(widgetId + "_module_id", it._id)
                edit.putString(widgetId + "_name", it.module_name)
            }
            selectedStation?.let { edit.putString(widgetId + "_station_id", it._id) }
        }
        edit.putInt(widgetId + "_background_color", background_color.color)
        edit.putInt(widgetId + "_text_color", text_color.color)
        try {
            edit.putFloat(widgetId + "_text_size", text_size.text.toString().toFloat())
        } catch (nfe: NumberFormatException) {
            Log.e(TAG, "Given text.size value is not a number: $nfe", nfe)
        }
        edit.putBoolean(widgetId + "_show_name", show_name.isChecked)
        edit.putBoolean(
            widgetId + "_show_temperature",
            selectedModule?.data_type?.contains("Temperature") ?: false && show_temperature.isChecked
        )
        edit.putBoolean(
            widgetId + "_show_co2",
            selectedModule?.data_type?.contains("CO2") ?: false && show_co2.isChecked
        )
        edit.putBoolean(
            widgetId + "_show_humidity",
            selectedModule?.data_type?.contains("Humidity") ?: false && show_humidity.isChecked
        )
        edit.putBoolean(widgetId + "_show_latest", show_latest.isChecked)

        edit.putInt(widgetId + "_color_temperature", temperature_color.color)
        edit.putInt(widgetId + "_color_co2", co2_color.color)
        edit.putInt(widgetId + "_color_humidity", humidity_color.color)

        try {
            edit.putInt(widgetId + "_limit", limit.text.toString().toInt())
        } catch (nfe: NumberFormatException) {
            Log.e(TAG, "Given limit value is not a number: $nfe", nfe)
        }
        edit.putInt(widgetId + "_scale", scale.selectedItem.toString().replace(" min", "").toInt())
        edit.apply()

        GraphWidget.updateWidget(this, widgetId.toInt())
        finish()
    }
}
