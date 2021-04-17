package de.j4velin.simple.widget.netatmo

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import de.j4velin.simple.widget.netatmo.api.TAG
import de.j4velin.simple.widget.netatmo.settings.DEFAULT_INTERVAL

internal const val ACTION_UPDATE_WIDGETS = "UPDATE_WIDGETS"
internal const val ACTION_UPDATE_GRAPH_WIDGET = "UPDATE_GRAPH_WIDGET"
internal const val EXTRA_KEY_WIDGET_ID = "widgetId"

class WidgetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) {
            return
        }
        val awm = AppWidgetManager.getInstance(context)
        if (intent?.action?.equals(ACTION_UPDATE_WIDGETS) == true) {
            Widget.updateWidgets(
                context, awm, *awm.getAppWidgetIds(ComponentName(context, Widget::class.java))
            )
            awm.getAppWidgetIds(ComponentName(context, GraphWidget::class.java)).forEach {
                GraphWidget.updateWidget(context, it)
            }
            setNextAlarm(context)
        } else if (intent?.action?.equals(ACTION_UPDATE_GRAPH_WIDGET) == true) {
            if (intent.hasExtra(EXTRA_KEY_WIDGET_ID)) {
                GraphWidget.updateWidget(context, intent.extras!!.getInt(EXTRA_KEY_WIDGET_ID))
            }
        }
    }
}

internal fun setNextAlarm(context: Context) {
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val nextUpdate =
        System.currentTimeMillis() + prefs.getInt("interval", DEFAULT_INTERVAL) * 60 * 1000
    alarmManager?.set(
        AlarmManager.RTC, nextUpdate,
        PendingIntent.getBroadcast(
            context, 1,
            Intent(context, WidgetReceiver::class.java).setAction(ACTION_UPDATE_WIDGETS),
            PendingIntent.FLAG_UPDATE_CURRENT
        )
    )
    Log.i(TAG, "Next update scheduled at $nextUpdate")
}