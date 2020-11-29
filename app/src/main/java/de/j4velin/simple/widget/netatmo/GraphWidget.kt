package de.j4velin.simple.widget.netatmo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.androidplot.xy.LineAndPointFormatter
import com.androidplot.xy.NormedXYSeries
import com.androidplot.xy.SimpleXYSeries
import com.androidplot.xy.XYPlot
import de.j4velin.simple.widget.netatmo.api.NetatmoWeatherApi.Companion.tryGetApi
import de.j4velin.simple.widget.netatmo.api.TAG
import de.j4velin.simple.widget.netatmo.settings.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


class GraphWidget : AppWidgetProvider() {

    override fun onDeleted(context: Context?, widgetIds: IntArray?) {
        super.onDeleted(context, widgetIds)
        if (context != null && widgetIds != null) {
            val prefs =
                context.getSharedPreferences(GraphWidgetConfig.PREF_NAME, Context.MODE_PRIVATE)
            val edit = prefs.edit()
            for (widgetId in widgetIds) {
                for (key in prefs.all.keys) {
                    if (key.startsWith(widgetId.toString() + "_")) {
                        edit.remove(key)
                    }
                }
            }
            edit.apply()
        }
    }

    /*
    override fun onAppWidgetOptionsChanged(
        context: Context?,
        appWidgetManager: AppWidgetManager?,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        if (context != null) {
            val awm = AppWidgetManager.getInstance(context)
            awm.updateAppWidget(
                appWidgetId,
                getWidgetView(
                    appWidgetId,
                    context,
                    null,
                    context.getSharedPreferences("graphwidgets", Context.MODE_PRIVATE),
                    awm
                )
            )
        }
    }
    */

    override fun onUpdate(
        context: Context?, widgetManager: AppWidgetManager?, widgetIds: IntArray?
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
                } else {
                    for (widgetId in widgetIds) {
                        updateWidget(context, widgetId)
                    }
                }
            }
        } else {
            Log.e(TAG, "Parameter is null!")
        }
    }

    companion object {

        internal fun updateWidget(context: Context, widgetId: Int) {
            tryGetApi(context) {
                GlobalScope.launch {
                    val prefs = context.getSharedPreferences(
                        GraphWidgetConfig.PREF_NAME, Context.MODE_PRIVATE
                    )

                    val widget = widgetId.toString()
                    val moduleId = prefs.getString(widget + "_module_id", null)
                    val stationId = prefs.getString(widget + "_station_id", null)
                    if (moduleId != null && stationId != null) {
                        val scale = prefs.getInt(widget + "_scale", DEFAULT_SCALE)
                        val limit = prefs.getInt(widget + "_limit", DEFAULT_LIMIT) + 1
                        val duration = scale * 60 * limit // in seconds
                        val date = (System.currentTimeMillis() / 1000).toInt() - duration
                        val types = getTypes(widget, prefs)
                        val data = it.getMeasurements(
                            stationId, moduleId, scale.toString() + "min",
                            types.joinToString().replace(" ", ""), date
                        )
                        val awm = AppWidgetManager.getInstance(context)
                        awm.updateAppWidget(
                            widgetId, getWidgetView(
                                widgetId, context, data.body.map { b -> b.value }.flatten(),
                                prefs, awm, types
                            )
                        )
                    }
                }
            }
        }

        private fun getTypes(widget: String, prefs: SharedPreferences): List<String> {
            val list = mutableListOf<String>()
            for (type in listOf("temperature", "co2", "humidity")) {
                if (prefs.getBoolean(widget + "_show_$type", false)) {
                    list.add(type)
                }
            }
            return list
        }

        private fun getWidgetView(
            widgetId: Int, context: Context, measurements: List<List<Number>>,
            prefs: SharedPreferences, awm: AppWidgetManager, types: List<String>
        ): RemoteViews {
            val widget = widgetId.toString()
            Log.d(TAG, "update widget=$widget, measurements=$measurements")
            val views = RemoteViews(context.packageName, R.layout.graph_widget)
            val openNetatmoApp = PendingIntent.getActivity(
                context, 0,
                context.packageManager.getLaunchIntentForPackage("com.netatmo.netatmo"),
                0
            )

            views.setInt(
                R.id.bg, "setBackgroundColor",
                prefs.getInt(widget + "_background_color", DEFAULT_BG_COLOR)
            )
            views.setOnClickPendingIntent(R.id.bg, openNetatmoApp)

            val textSize = prefs.getFloat(widget + "_text_size", DEFAULT_TEXT_SIZE)

            if (prefs.getBoolean(widget + "_show_name", true)) {
                val updateWidget =
                    PendingIntent.getBroadcast(
                        context, widgetId,
                        Intent(context, WidgetReceiver::class.java)
                            .setAction(ACTION_UPDATE_WIDGETS),
                        Intent.FILL_IN_DATA or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                views.setOnClickPendingIntent(R.id.name, updateWidget)
                views.setTextViewText(R.id.name, prefs.getString(widget + "_name", ""))
                views.setTextColor(
                    R.id.name, prefs.getInt(widget + "_text_color", DEFAULT_TEXT_COLOR)
                )
                views.setFloat(R.id.name, "setTextSize", textSize)
                views.setViewVisibility(R.id.name, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.name, View.GONE)
            }

            if (prefs.getBoolean(widget + "_show_latest", true)) {
                var index = 0

                if (types.contains("temperature")) {
                    index = types.indexOf("temperature")
                    views.setTextViewText(
                        R.id.temperature, "${measurements.last()[index]} °C"
                    )
                    views.setTextColor(
                        R.id.temperature,
                        prefs.getInt(widget + "_color_temperature", DEFAULT_TEXT_COLOR)
                    )
                    views.setFloat(R.id.temperature, "setTextSize", textSize)
                } else {
                    views.setViewVisibility(R.id.temperature, View.GONE)
                }

                if (types.contains("co2")) {
                    index = types.indexOf("co2")
                    views.setTextViewText(
                        R.id.co2, "${measurements.last()[index]} ppm"
                    )
                    views.setTextColor(
                        R.id.co2,
                        prefs.getInt(widget + "_color_co2", DEFAULT_TEXT_COLOR)
                    )
                    views.setFloat(R.id.co2, "setTextSize", textSize)
                } else {
                    views.setViewVisibility(R.id.co2, View.GONE)
                }

                if (types.contains("humidity")) {
                    index = types.indexOf("humidity")
                    views.setTextViewText(
                        R.id.humidity, "${measurements.last()[index]} %"
                    )
                    views.setTextColor(
                        R.id.humidity,
                        prefs.getInt(widget + "_color_humidity", DEFAULT_TEXT_COLOR)
                    )
                    views.setFloat(R.id.humidity, "setTextSize", textSize)
                } else {
                    views.setViewVisibility(R.id.humidity, View.GONE)
                }

                views.setViewVisibility(R.id.latest, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.latest, View.GONE)
            }

            val options: Bundle = awm.getAppWidgetOptions(widgetId)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)

            val plot = XYPlot(context, "")
            plot.layout(0, 0, width, height)
            plot.graph.backgroundPaint = null
            plot.graph.domainGridLinePaint = null
            plot.graph.gridBackgroundPaint = null
            plot.graph.rangeGridLinePaint = null
            plot.graph.rangeOriginLinePaint = null
            plot.graph.domainOriginLinePaint = null
            plot.backgroundPaint = null
            plot.borderPaint = null
            plot.legend.isVisible = false
            plot.graph.setLineLabelEdges();

            plot.isDrawingCacheEnabled = true

            for (i in types.indices) {
                val seriesData = measurements.map { it[i] }
                val series = NormedXYSeries(
                    SimpleXYSeries(seriesData, SimpleXYSeries.ArrayFormat.Y_VALS_ONLY, "")
                )
                val color = prefs.getInt(widget + "_color_" + types[i], DEFAULT_TEXT_COLOR)
                val seriesFormat = LineAndPointFormatter(color, null, null, null)
                plot.addSeries(series, seriesFormat)
            }

            views.setImageViewBitmap(R.id.graph, plot.drawingCache)
            return views
        }
    }
}

