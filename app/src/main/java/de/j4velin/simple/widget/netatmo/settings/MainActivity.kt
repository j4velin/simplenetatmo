package de.j4velin.simple.widget.netatmo.settings

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import de.j4velin.simple.widget.netatmo.GraphWidget
import de.j4velin.simple.widget.netatmo.R
import de.j4velin.simple.widget.netatmo.Widget
import de.j4velin.simple.widget.netatmo.api.getAuthState
import de.j4velin.simple.widget.netatmo.api.performAuthentication
import kotlinx.android.synthetic.main.main.*
import net.openid.appauth.AuthorizationService

internal const val NOTIFICATION_CHANNEL_ERRORS = "errors"

internal fun showErrorDialog(activity: Activity, msg: String) {
    AlertDialog.Builder(activity)
        .setMessage(msg)
        .setPositiveButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }
        .setOnCancelListener { activity.finish() }.create().show()
}

class MainActivity : Activity() {

    private val service by lazy { AuthorizationService(this) }
    private lateinit var widgetIds: IntArray
    private lateinit var graphWidgetIds: IntArray

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ERRORS, getString(R.string.channel_name_errors),
            NotificationManager.IMPORTANCE_LOW
        )
        nm?.createNotificationChannel(channel)
    }

    override fun onResume() {
        super.onResume()
        if (getAuthState(this).isAuthorized) {
            text.text = getString(R.string.setup_done)
            authbutton.visibility = View.GONE
            val widgetPrefs = getSharedPreferences("widgets", Context.MODE_PRIVATE)
            val graphPrefs = getSharedPreferences("graphwidgets", Context.MODE_PRIVATE)
            val awm = AppWidgetManager.getInstance(this)
            widgetIds = awm.getAppWidgetIds(ComponentName(this, Widget::class.java))
            graphWidgetIds = awm.getAppWidgetIds(ComponentName(this, GraphWidget::class.java))
            val widgetsNames =
                widgetIds.map { "#$it - ${widgetPrefs.getString(it.toString() + "_name", "")}" }
            val graphWidgetsNames = graphWidgetIds.map {
                "#$it - ${graphPrefs.getString(it.toString() + "_name", "")}"
            }
            val adapter =
                ArrayAdapter(
                    this, android.R.layout.simple_spinner_dropdown_item,
                    widgetsNames + graphWidgetsNames
                )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            editwidgets.adapter = adapter
            editbutton.isEnabled = !widgetIds.isEmpty()

        } else {
            text.text = getString(R.string.setup_need_auth)
            authbutton.visibility = View.VISIBLE
            editlayout.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        service.dispose()
    }

    fun startAuthFlow(view: View) = performAuthentication(this, service)

    fun editWidget(view: View) {
        if (editwidgets.selectedItemPosition >= widgetIds.size) {
            startActivity(
                Intent(this, GraphWidgetConfig::class.java).putExtra(
                    "editId", graphWidgetIds[editwidgets.selectedItemPosition - widgetIds.size]
                )
            )
        } else {
            startActivity(
                Intent(this, WidgetConfig::class.java).putExtra(
                    "editId", widgetIds[editwidgets.selectedItemPosition]
                )
            )
        }
    }
}