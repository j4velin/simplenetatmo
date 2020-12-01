package de.j4velin.simple.widget.netatmo.api

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import de.j4velin.simple.widget.netatmo.BuildConfig
import de.j4velin.simple.widget.netatmo.settings.AuthResponseActivity
import net.openid.appauth.*
import java.util.*

private const val CLIENT_ID = BuildConfig.clientId
internal const val CLIENT_SECRET = BuildConfig.clientSecret
internal val uuid = UUID.randomUUID()

internal fun getAuthState(context: Context): AuthState {
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val stateJson = prefs.getString("stateJson", null)
    return if (stateJson != null) {
        AuthState.jsonDeserialize(stateJson)
    } else {
        AuthState()
    }
}

internal fun peristAuthState(authState: AuthState, context: Context) =
    context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
        .putString("stateJson", authState.jsonSerializeString())
        .apply()

internal fun performAuthentication(context: Context, service: AuthorizationService) {
    val authRequest = AuthorizationRequest.Builder(
        AuthorizationServiceConfiguration(
            Uri.parse("https://api.netatmo.com/oauth2/authorize"),  // Authorization endpoint
            Uri.parse("https://api.netatmo.com/oauth2/token") // Token endpoint
        ),
        CLIENT_ID,
        ResponseTypeValues.CODE,
        Uri.parse("de.j4velin.simple.widget.netatmo://oauth-callback") // Redirect URI
    ).setScope("read_station").setState(uuid.toString()).build()

    val pi = PendingIntent.getActivity(
        context,
        0,
        Intent(context, AuthResponseActivity::class.java),
        0
    )
    service.performAuthorizationRequest(authRequest, pi, pi)
}

internal fun retrieveToken(
    context: Context,
    authState: AuthState,
    service: AuthorizationService,
    response: AuthorizationResponse,
    onError: (String) -> Unit,
    onSuccess: () -> Unit
) {
    service.performTokenRequest(
        response.createTokenExchangeRequest(),
        ClientSecretPost(CLIENT_SECRET)
    ) { tokenResponse, tokenException ->
        authState.update(tokenResponse, tokenException)
        if (tokenException != null) {
            val errorMsg = tokenException.error ?: tokenException.toJsonString()
            Log.e(TAG, errorMsg, tokenException)
            onError(errorMsg)
        }
        if (tokenResponse != null) {
            peristAuthState(authState, context)
            onSuccess()
        }
    }
}
