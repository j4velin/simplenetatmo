package de.j4velin.simple.widget.netatmo

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

internal const val ACTION_UPDATE_WIDGETS = "UPDATE_WIDGETS"

class WidgetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context != null && intent?.action?.equals(ACTION_UPDATE_WIDGETS) == true) {
            val awm = AppWidgetManager.getInstance(context)
            Widget.updateAllWidgets(
                context, awm, awm.getAppWidgetIds(ComponentName(context, Widget::class.java))
            )
        }
    }
}