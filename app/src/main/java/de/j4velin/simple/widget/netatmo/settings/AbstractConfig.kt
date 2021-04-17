package de.j4velin.simple.widget.netatmo.settings

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import de.j4velin.lib.colorpicker.ColorPickerDialog
import de.j4velin.lib.colorpicker.ColorPreviewButton
import de.j4velin.simple.widget.netatmo.R
import de.j4velin.simple.widget.netatmo.api.NetatmoWeatherApi
import de.j4velin.simple.widget.netatmo.api.TAG
import de.j4velin.simple.widget.netatmo.setNextAlarm
import kotlinx.android.synthetic.main.global_config.*
import kotlinx.android.synthetic.main.module_config.*
import kotlinx.android.synthetic.main.widget_config.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

const val DEFAULT_INTERVAL = 30

abstract class AbstractConfig(private val prefName: String) : Activity() {

    protected var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID.toString()
    protected var selectedModule: NetatmoWeatherApi.Module? = null
    protected var selectedStation: NetatmoWeatherApi.Station? = null
    protected lateinit var prefs: SharedPreferences

    protected val colorClickListener = { view: View ->
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
        prefs = getSharedPreferences(prefName, Context.MODE_PRIVATE)
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
    }

    override fun onResume() {
        super.onResume()
        val authorized = NetatmoWeatherApi.getApi(this, { error ->
            Toast.makeText(
                this, getString(R.string.data_error, error), Toast.LENGTH_LONG
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
        } else {
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            onlywifi.isChecked = prefs.getBoolean("only_wifi", true)
            interval.setText(prefs.getInt("interval", DEFAULT_INTERVAL).toString())
        }
    }

    private fun dataReceived(data: NetatmoWeatherApi.StationResponse) {
        if (data.error != null) {
            showErrorDialog(this, getString(R.string.error_retrieving_stations, data.error.message))
            return
        } else
            if (data.body?.devices?.isEmpty() != false) {
                showErrorDialog(this, getString(R.string.no_stations))
                return
            }
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
        modules.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedModule = null
                selectedStation = null
            }

            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                val module = moduleList[position]
                selectedModule = module
                selectedStation =
                    data.body.devices.firstOrNull { s -> s.allModules.contains(module) }
                show_co2.isEnabled = module.data_type.contains("CO2")
                show_temperature.isEnabled = module.data_type.contains("Temperature")
                show_humidity.isEnabled = module.data_type.contains("Humidity")
            }
        }
        modules.setSelection(if (prefs.contains(widgetId + "_module_id")) {
            val savedId = prefs.getString(widgetId + "_module_id", "")
            moduleList.indexOfFirst { m -> m._id == savedId }
        } else {
            0
        })
    }

    protected fun saveGeneralSettings() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().apply {
            try {
                putInt("interval", interval.text.toString().toInt())
            } catch (nfe: NumberFormatException) {
                Log.e(TAG, "Given interval value is not a number: $nfe", nfe)
            }
            putBoolean("only_wifi", onlywifi.isChecked)
            apply()
        }

        val resultValue = Intent()
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId.toInt())
        setResult(RESULT_OK, resultValue)
        setNextAlarm(this)
    }
}