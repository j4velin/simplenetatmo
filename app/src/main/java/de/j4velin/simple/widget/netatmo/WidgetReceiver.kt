package de.j4velin.simple.widget.netatmo

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import de.j4velin.simple.widget.netatmo.api.TAG
import de.j4velin.simple.widget.netatmo.settings.DEFAULT_INTERVAL

internal const val ACTION_UPDATE_WIDGETS = "UPDATE_WIDGETS"
internal const val ACTION_UPDATE_GRAPH_WIDGET = "UPDATE_GRAPH_WIDGET"
internal const val EXTRA_KEY_WIDGET_ID = "widgetId"
private const val EXTRA_REASON = "reason"
private const val REASON_TIMER = "timer"

class WidgetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) {
            return
        }
        val awm = AppWidgetManager.getInstance(context)
        if (intent?.action?.equals(ACTION_UPDATE_WIDGETS) == true) {
            setNextAlarm(context)
            if (intent.getStringExtra(EXTRA_REASON) == REASON_TIMER && !shouldUpdate(context)) {
                Log.i(TAG, "Skipping timed update due to network restrictions")
                return
            }
            Widget.updateWidgets(
                context, awm, *awm.getAppWidgetIds(ComponentName(context, Widget::class.java))
            )
            awm.getAppWidgetIds(ComponentName(context, GraphWidget::class.java)).forEach {
                GraphWidget.updateWidget(context, it)
            }
        } else if (intent?.action?.equals(ACTION_UPDATE_GRAPH_WIDGET) == true) {
            if (intent.hasExtra(EXTRA_KEY_WIDGET_ID)) {
                GraphWidget.updateWidget(context, intent.extras!!.getInt(EXTRA_KEY_WIDGET_ID))
            }
        }
    }
}

/**
 * @param context the context
 * @return false, if updating the widgets should be skipped (for example due to network restrictions)
 */
private fun shouldUpdate(context: Context): Boolean {
    val connManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)
    return connManager?.activeNetwork?.let {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        return if (prefs.getBoolean("only_wifi", true)) {
            connManager.getNetworkCapabilities(it)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: true
        } else {
            true
        }
    } ?: false
}

/**
 * Sets an alarm for the next scheduled widget update
 * @param context the context
 */
internal fun setNextAlarm(context: Context) {
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val nextUpdate =
        System.currentTimeMillis() + prefs.getInt("interval", DEFAULT_INTERVAL) * 60 * 1000
    alarmManager?.set(
        AlarmManager.RTC, nextUpdate,
        PendingIntent.getBroadcast(
            context, 1,
            Intent(context, WidgetReceiver::class.java).setAction(ACTION_UPDATE_WIDGETS).putExtra(
                EXTRA_REASON, REASON_TIMER
            ),
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    )
    Log.i(TAG, "Next update scheduled at $nextUpdate")
}