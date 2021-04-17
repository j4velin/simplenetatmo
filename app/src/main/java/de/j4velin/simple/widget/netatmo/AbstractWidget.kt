package de.j4velin.simple.widget.netatmo

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import de.j4velin.simple.widget.netatmo.api.TAG
import java.text.DateFormat
import java.text.SimpleDateFormat

internal val timeFormat = SimpleDateFormat.getTimeInstance(DateFormat.SHORT)

abstract class AbstractWidget(private val prefName: String) : AppWidgetProvider() {

    override fun onDeleted(context: Context?, widgetIds: IntArray?) {
        super.onDeleted(context, widgetIds)
        if (context != null && widgetIds != null) {
            val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
            prefs.edit().apply {
                prefs.all.keys.filter {
                    widgetIds.contains(
                        it.substringBefore('_').toIntOrNull() ?: -1
                    )
                }.forEach {
                    remove(it)
                }
                apply()
            }
        }
    }

    override fun onUpdate(
        context: Context?, widgetManager: AppWidgetManager?, widgetIds: IntArray?
    ) {
        super.onUpdate(context, widgetManager, widgetIds)
        if (context != null && widgetManager != null && widgetIds != null) {
            setNextAlarm(context)
            val connManager =
                context.applicationContext.getSystemService(ConnectivityManager::class.java)
            val activeNetwork = connManager?.activeNetwork
            if (activeNetwork != null) {
                val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                if (prefs.getBoolean("only_wifi", true)) {
                    val wifi = connManager.getNetworkCapabilities(activeNetwork)
                        ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: true
                    if (!wifi) {
                        Log.i(TAG, "No WiFi connection -> don't update widgets")
                        return
                    }
                }
                updateWidgets(context, widgetManager, widgetIds)
            } else {
                Log.w(TAG, "No network connection")
            }
        } else {
            Log.e(TAG, "Parameter is null!")
        }
    }

    abstract fun updateWidgets(
        context: Context,
        widgetManager: AppWidgetManager,
        widgetIds: IntArray
    )
}