package de.j4velin.simple.widget.netatmo.settings

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import de.j4velin.simple.widget.netatmo.R
import de.j4velin.simple.widget.netatmo.Widget
import de.j4velin.simple.widget.netatmo.api.getAuthState
import de.j4velin.simple.widget.netatmo.api.performAuthentication
import kotlinx.android.synthetic.main.main.*
import net.openid.appauth.AuthorizationService

internal const val NOTIFICATION_CHANNEL_ERRORS = "errors"

class MainActivity : Activity() {

    private val service by lazy { AuthorizationService(this) }
    private lateinit var widgetIds: IntArray

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ERRORS,
            getString(R.string.channel_name_errors),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        nm?.createNotificationChannel(channel)
    }

    override fun onResume() {
        super.onResume()
        if (getAuthState(this).isAuthorized) {
            text.text = getString(R.string.setup_done)
            authbutton.visibility = View.GONE
            val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
            val awm = AppWidgetManager.getInstance(this)
            widgetIds = awm.getAppWidgetIds(ComponentName(this, Widget::class.java))
            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                widgetIds.map { "#$it - ${prefs.getString(it.toString() + "_name", "")}" }
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            editwidgets.adapter = adapter

        } else {
            text.text =
                getString(R.string.setup_need_auth)
            authbutton.visibility = View.VISIBLE
            editlayout.visibility = View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        service.dispose()
    }

    fun startAuthFlow(view: View) = performAuthentication(this, service)

    fun editWidget(view: View) = startActivity(
        Intent(this, WidgetConfig::class.java).putExtra(
            "editId",
            widgetIds[editwidgets.selectedItemPosition]
        )
    )
}