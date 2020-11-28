package de.j4velin.simple.widget.netatmo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import de.j4velin.simple.widget.netatmo.api.NetatmoWeatherApi
import de.j4velin.simple.widget.netatmo.api.TAG
import de.j4velin.simple.widget.netatmo.settings.DEFAULT_BG_COLOR
import de.j4velin.simple.widget.netatmo.settings.DEFAULT_TEXT_COLOR
import de.j4velin.simple.widget.netatmo.settings.DEFAULT_TEXT_SIZE
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

class Widget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context?,
        widgetManager: AppWidgetManager?,
        widgetIds: IntArray?
    ) {
        super.onUpdate(context, widgetManager, widgetIds)
        if (context != null && widgetManager != null && widgetIds != null) {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            if (prefs.getBoolean("only_wifi", true)) {
                val connManager =
                    context.applicationContext.getSystemService(ConnectivityManager::class.java)
                val wifi = connManager?.getNetworkCapabilities(connManager.activeNetwork)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: true
                if (!wifi) {
                    Log.i(TAG, "No WiFi connection -> don't update widgets")
                    return
                }
            }

            val authorized = NetatmoWeatherApi.getApi(context, { error ->
                Log.e(TAG, "Error getting API: $error")
                // TODO: show error notification
            }) {
                GlobalScope.launch {
                    Log.d(TAG, "updating widgets ${widgetIds.asList()}")
                    val data = it.getStations()
                    for (widgetId in widgetIds) {
                        val moduleId = prefs.getString(widgetId.toString() + "_module_id", null)
                        val module = moduleId?.let { data.getModule(it) }
                        if (module != null) {
                            widgetManager.updateAppWidget(
                                widgetId, getWidgetView(widgetId, context, module, prefs)
                            )
                        } else {
                            Log.e(TAG, "No module found for id=$moduleId, widget=$widgetId")
                        }
                    }
                }
            }
            if (!authorized) {
                Log.e(TAG, "Not authorized!")
                // TODO: show error notification
            }
        } else {
            Log.e(TAG, "Parameter is null!")
        }
    }
}

private val timeFormat = SimpleDateFormat.getTimeInstance(DateFormat.SHORT)

internal fun updateWidget(context: Context, widgetId: Int) {
    val awm = AppWidgetManager.getInstance(context)
    NetatmoWeatherApi.getApi(context, { error ->
        Log.e(TAG, "Error getting API: $error")
        // TODO: show error notification
    }) {
        GlobalScope.launch {
            Log.d(TAG, "updating widget $widgetId on user request")
            val data = it.getStations()
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val moduleId = prefs.getString(widgetId.toString() + "_module_id", null)
            val module = moduleId?.let { data.getModule(it) }
            if (module != null) {
                awm.updateAppWidget(
                    widgetId, getWidgetView(widgetId, context, module, prefs)
                )
            } else {
                Log.e(TAG, "No module found for id=$moduleId, widget=$widgetId")
            }
        }
    }
}

internal fun getWidgetView(
    widgetId: Int,
    context: Context,
    module: NetatmoWeatherApi.Module,
    prefs: SharedPreferences
): RemoteViews {
    val widget = widgetId.toString()
    Log.d(TAG, "update widget=$widget, module=$module")
    val views = RemoteViews(context.packageName, R.layout.widget)
    val openNetatmoApp = PendingIntent.getActivity(
        context,
        0,
        context.packageManager.getLaunchIntentForPackage("com.netatmo.netatmo"),
        0
    )

    views.setInt(
        R.id.bg,
        "setBackgroundColor",
        prefs.getInt(widget + "_background_color", DEFAULT_BG_COLOR)
    )
    views.setOnClickPendingIntent(R.id.bg, openNetatmoApp)

    val textColor = prefs.getInt(widget + "_text_color", DEFAULT_TEXT_COLOR)
    val showIcons = prefs.getBoolean(widget + "_show_icons", true)
    val textSize = prefs.getFloat(widget + "_text_size", DEFAULT_TEXT_SIZE)

    if (prefs.getBoolean(widget + "_show_name", true)) {
        val updateWidget =
            PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, WidgetReceiver::class.java).putExtra("widgetId", widgetId),
                Intent.FILL_IN_DATA
            )
        val time = timeFormat.format(Date(module.dashboard_data.time_utc * 1000))
        views.setOnClickPendingIntent(R.id.name, updateWidget)
        views.setTextViewText(R.id.name, "${module.module_name}\n$time")
        views.setTextColor(R.id.name, textColor)
        views.setFloat(R.id.name, "setTextSize", textSize)
        views.setViewVisibility(R.id.name, View.VISIBLE)
    } else {
        views.setViewVisibility(R.id.name, View.GONE)
    }

    if (module.data_type.contains("Temperature") && prefs.getBoolean(
            widget + "_show_temperature",
            true
        )
    ) {
        views.setTextViewText(R.id.temperature, "${module.dashboard_data.Temperature} °C")
        views.setTextColor(R.id.temperature, textColor)
        views.setFloat(R.id.temperature, "setTextSize", textSize)
        views.setViewVisibility(R.id.temperature, View.VISIBLE)
        if (showIcons) {
            views.setInt(R.id.ic_temperature, "setColorFilter", textColor)
            views.setViewVisibility(R.id.ic_temperature, View.VISIBLE)
        }
    } else {
        views.setViewVisibility(R.id.temperature, View.GONE)
        views.setViewVisibility(R.id.ic_temperature, View.GONE)
    }

    if (module.data_type.contains("CO2") && prefs.getBoolean(
            widget + "_show_co2",
            true
        )
    ) {
        views.setTextViewText(R.id.co2, "${module.dashboard_data.CO2} ppm")
        views.setTextColor(R.id.co2, textColor)
        views.setFloat(R.id.co2, "setTextSize", textSize)
        views.setViewVisibility(R.id.co2, View.VISIBLE)
        if (showIcons) {
            views.setInt(R.id.ic_co2, "setColorFilter", textColor)
            views.setViewVisibility(R.id.ic_co2, View.VISIBLE)
        }
    } else {
        views.setViewVisibility(R.id.co2, View.GONE)
        views.setViewVisibility(R.id.ic_co2, View.GONE)
    }

    if (module.data_type.contains("Humidity") && prefs.getBoolean(
            widget + "_show_humidity",
            true
        )
    ) {
        views.setTextViewText(R.id.humidity, "${module.dashboard_data.Humidity} %")
        views.setTextColor(R.id.humidity, textColor)
        views.setFloat(R.id.humidity, "setTextSize", textSize)
        views.setViewVisibility(R.id.humidity, View.VISIBLE)
        if (showIcons) {
            views.setInt(R.id.ic_humidity, "setColorFilter", textColor)
            views.setViewVisibility(R.id.ic_humidity, View.VISIBLE)
        }
    } else {
        views.setViewVisibility(R.id.humidity, View.GONE)
        views.setViewVisibility(R.id.ic_humidity, View.GONE)
    }

    views.setViewVisibility(R.id.icons, if (showIcons) View.VISIBLE else View.GONE)

    return views
}