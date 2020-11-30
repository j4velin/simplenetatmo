package de.j4velin.simple.widget.netatmo.settings

import android.app.Activity
import android.os.Bundle
import android.util.Log
import de.j4velin.simple.widget.netatmo.R
import de.j4velin.simple.widget.netatmo.api.TAG
import de.j4velin.simple.widget.netatmo.api.retrieveToken
import de.j4velin.simple.widget.netatmo.api.uuid
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService

class AuthResponseActivity : Activity() {

    private val service by lazy { AuthorizationService(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val authResponse = AuthorizationResponse.fromIntent(intent)
        val authException = AuthorizationException.fromIntent(intent)
        if (authResponse != null || authException != null) {
            val authState = AuthState(authResponse, authException)
            if (authResponse != null) {
                if (uuid.toString() == authResponse.state) {
                    retrieveToken(
                        this,
                        authState,
                        service,
                        authResponse,
                        { error ->
                            showErrorDialog(this, getString(R.string.auth_error, error))
                        },
                        this::finish
                    )
                } else {
                    val errorMsg = getString(R.string.auth_invalid_state)
                    Log.e(TAG, errorMsg)
                    showErrorDialog(this, getString(R.string.auth_error, errorMsg))
                }
            }
            if (authException != null) {
                val errorMsg = authException.error ?: authException.toJsonString()
                Log.e(TAG, errorMsg, authException)
                showErrorDialog(this, getString(R.string.auth_error, errorMsg))
            }
        } else {
            val errorMsg = getString(R.string.auth_invalid_response)
            Log.e(TAG, errorMsg, authException)
            showErrorDialog(this, getString(R.string.auth_error, errorMsg))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        service.dispose()
    }
}