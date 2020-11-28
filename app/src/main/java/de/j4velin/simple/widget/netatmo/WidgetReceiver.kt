package de.j4velin.simple.widget.netatmo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WidgetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        val widgetId = intent?.extras?.getInt("widgetId") ?: -1
        if (context != null && widgetId != -1) {
            updateWidget(context, widgetId)
        }
    }
}