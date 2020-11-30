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
                    updateAllWidgets(context, widgetManager, widgetIds)
                }
            }
        } else {
            Log.e(TAG, "Parameter is null!")
        }
    }

    abstract fun updateAllWidgets(
        context: Context,
        widgetManager: AppWidgetManager,
        widgetIds: IntArray
    )

}