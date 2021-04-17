package de.j4velin.simple.widget.netatmo

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import de.j4velin.simple.widget.netatmo.api.TAG
import de.j4velin.simple.widget.netatmo.settings.DEFAULT_INTERVAL
import java.text.DateFormat
import java.text.SimpleDateFormat

internal val timeFormat = SimpleDateFormat.getTimeInstance(DateFormat.SHORT)
private const val ACTION_UPDATE = "de.j4velin.simple.widget.netatmo.UPDATE"
private const val INTENT_EXTRA_ID = "id"

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

    override fun onReceive(context: Context?, intent: Intent?) =
        if (intent?.action == ACTION_UPDATE) {
            onUpdate(
                context,
                AppWidgetManager.getInstance(context),
                intArrayOf(intent.getIntExtra("id", -1))
            )
        } else {
            super.onReceive(context, intent)
        }

    override fun onUpdate(
        context: Context?, widgetManager: AppWidgetManager?, widgetIds: IntArray?
    ) {
        super.onUpdate(context, widgetManager, widgetIds)
        if (context != null && widgetManager != null && widgetIds != null) {
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
            setNextAlarms(context, widgetIds)
        } else {
            Log.e(TAG, "Parameter is null!")
        }
    }

    abstract fun updateWidgets(
        context: Context,
        widgetManager: AppWidgetManager,
        widgetIds: IntArray
    )

    private fun setNextAlarms(context: Context, ids: IntArray) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        for (widgetId in ids) {
            val nextUpdate = System.currentTimeMillis() + prefs.getInt(
                "${widgetId}_interval",
                DEFAULT_INTERVAL
            ) * 60 * 1000
            alarmManager?.set(
                AlarmManager.RTC, nextUpdate,
                PendingIntent.getBroadcast(
                    context, widgetId,
                    Intent(ACTION_UPDATE).setPackage(context.packageName)
                        .putExtra(INTENT_EXTRA_ID, widgetId),
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            Log.i(TAG, "Next update of widget $widgetId scheduled at $nextUpdate")
        }
    }
}