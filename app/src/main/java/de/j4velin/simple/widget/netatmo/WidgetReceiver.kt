package de.j4velin.simple.widget.netatmo

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

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
            Widget.updateAllWidgets(
                context, awm, awm.getAppWidgetIds(ComponentName(context, Widget::class.java))
            )
        } else if (intent?.action?.equals(ACTION_UPDATE_GRAPH_WIDGET) == true) {
            if (intent.hasExtra(EXTRA_KEY_WIDGET_ID)) {
                GraphWidget.updateWidget(context, intent.extras!!.getInt(EXTRA_KEY_WIDGET_ID))
            }
        }
    }
}