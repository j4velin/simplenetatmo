package de.j4velin.simple.widget.netatmo.settings

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Toast
import de.j4velin.lib.colorpicker.ColorPickerDialog
import de.j4velin.lib.colorpicker.ColorPreviewButton
import de.j4velin.simple.widget.netatmo.R
import de.j4velin.simple.widget.netatmo.Widget
import de.j4velin.simple.widget.netatmo.api.NetatmoWeatherApi
import de.j4velin.simple.widget.netatmo.api.TAG
import kotlinx.android.synthetic.main.widget_config.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

const val DEFAULT_BG_COLOR = 1694498816
const val DEFAULT_TEXT_COLOR = Color.WHITE
const val DEFAULT_TEXT_SIZE = 12f

class WidgetConfig : Activity() {

    private lateinit var prefs: SharedPreferences
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID.toString()
    private var selectedModule: NetatmoWeatherApi.Module? = null

    private val colorClickListener = { view: View ->
        val dialog = ColorPickerDialog(this, (view as ColorPreviewButton).color)
        dialog.hexValueEnabled = true
        dialog.alphaSliderVisible = view.id == R.id.background_color
        dialog.setOnColorChangedListener { color ->
            view.color = color
            view.setTag(color)
        }
        dialog.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("widgets", Context.MODE_PRIVATE)
        setResult(RESULT_CANCELED)
        val extras = intent.extras
        if (extras != null) {
            widgetId = (if (extras.containsKey("editId")) {
                extras.getInt("editId")
            } else {
                extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
            }).toString()
        }
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID.toString()) {
            Log.e(TAG, "No valid widget id")
            finish()
        }
        setContentView(R.layout.widget_config)

        onlywifi.isChecked =
            getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("only_wifi", true)

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

    override fun onResume() {
        super.onResume()
        val authorized = NetatmoWeatherApi.getApi(this, { error ->
            Toast.makeText(
                this,
                getString(R.string.data_error, error),
                Toast.LENGTH_LONG
            ).show()
        }) {
            GlobalScope.launch {
                val data = it.getStations()
                runOnUiThread { dataReceived(data) }
            }
        }

        if (!authorized) {
            // request authorization
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun dataReceived(data: NetatmoWeatherApi.StationResponse) {
        val moduleList = mutableListOf<NetatmoWeatherApi.Module>()
        val moduleNameList = mutableListOf<String>()
        for (station in data.body.devices) {
            val stationName = station.home_name
            val modules = station.allModules
            moduleList.addAll(modules)
            moduleNameList.addAll(modules.map { m -> "$stationName - ${m.module_name}" })
        }
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            moduleNameList
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modules.adapter = adapter
        modules.onItemSelectedListener = object : OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedModule = null
            }

            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                val module = moduleList[position]
                selectedModule = module
                show_co2.isEnabled = module.data_type.contains("CO2")
                show_temperature.isEnabled = module.data_type.contains("Temperature")
                show_humidity.isEnabled = module.data_type.contains("Humidity")
            }
        }
        if (prefs.contains(widgetId + "_module_id")) {
            val savedId = prefs.getString(widgetId + "_module_id", "")
            modules.setSelection(moduleList.indexOfFirst { m -> m._id == savedId })
        } else {
            modules.setSelection(0)
        }
    }

    fun save(view: View) {
        getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
            .putBoolean("only_wifi", onlywifi.isChecked).apply()

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

        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId.toInt())
        setResult(RESULT_OK, resultValue)
        Widget.updateWidget(this, widgetId.toInt())
        finish()
    }
}
