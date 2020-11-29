package de.j4velin.simple.widget.netatmo.api

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import de.j4velin.simple.widget.netatmo.R
import de.j4velin.simple.widget.netatmo.settings.MainActivity
import de.j4velin.simple.widget.netatmo.settings.NOTIFICATION_CHANNEL_ERRORS
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationService
import net.openid.appauth.ClientSecretPost
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query


const val TAG = "SimpleNetatmo"

interface NetatmoWeatherApi {

    companion object {
        /**
         * Gets the api
         */
        internal fun getApi(
            context: Context, onError: (String) -> Unit,
            onSuccess: (NetatmoWeatherApi) -> Unit
        ): Boolean {
            val service = AuthorizationService(context, false)
            val authState = getAuthState(context)
            return if (authState.isAuthorized) {
                getTokenAndApi(authState, service, { error: String ->
                    onError(error)
                    service.dispose()
                }) { api: NetatmoWeatherApi ->
                    onSuccess(api)
                    service.dispose()
                }
                true
            } else {
                service.dispose()
                false
            }
        }

        /**
         * Gets the api or shows any error notification, if not authenticated
         */
        internal fun tryGetApi(context: Context, action: (NetatmoWeatherApi) -> Unit) {
            val authorized = getApi(context, { error ->
                Log.e(TAG, "Error getting API: $error")
                // TODO: also show error notification?
            }, action)
            if (!authorized) {
                Log.e(TAG, "Not authorized!")
                val nm = context.getSystemService(NotificationManager::class.java)
                val notification =
                    Notification.Builder(context, NOTIFICATION_CHANNEL_ERRORS).setContentTitle(
                        context.getString(R.string.not_authorized)
                    ).setContentText(context.getString(R.string.not_authorized_long))
                        .setSmallIcon(R.mipmap.ic_launcher_round).setContentIntent(
                            PendingIntent.getActivity(
                                context, 1,
                                Intent(context, MainActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0
                            )
                        ).setAutoCancel(true).build()
                nm?.notify(1, notification)
            }
        }

        private fun getTokenAndApi(
            authState: AuthState, service: AuthorizationService,
            onError: (String) -> Unit, onSuccess: (NetatmoWeatherApi) -> Unit
        ) {
            authState.performActionWithFreshTokens(service, ClientSecretPost(CLIENT_SECRET))
            { accessToken, _, exception ->
                if (exception != null) {
                    val errorMsg = exception.error ?: exception.toJsonString()
                    Log.e(TAG, errorMsg, exception)
                    onError(errorMsg)
                }
                if (accessToken != null) {
                    onSuccess(getApiWithToken(accessToken))
                }
            }
        }

        private fun getApiWithToken(token: String): NetatmoWeatherApi {
            // val logging = HttpLoggingInterceptor()
            // logging.level = HttpLoggingInterceptor.Level.BODY
            val okhttp = OkHttpClient.Builder()//.addInterceptor(logging)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .addHeader("Authorization", "Bearer $token")
                            .build()
                    )
                }.build()
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.netatmo.com/api/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(okhttp)
                .build()
            return retrofit.create(NetatmoWeatherApi::class.java)
        }
    }

    @GET("getstationsdata")
    suspend fun getStations(): StationResponse

    data class ErrorResponse(val message: String, val code: Int)
    data class StationResponse(
        val body: StationBody, val status: String, val error: ErrorResponse
    ) {
        fun getModule(id: String): Module? =
            body.devices.flatMap { s -> s.allModules }.find { r -> r._id == id }
    }

    data class StationBody(val devices: List<Station>, val user: User)
    data class Station(
        val home_name: String,
        val modules: List<Module>,
        val _id: String,
        private val module_name: String,
        private val data_type: List<String>,
        private val dashboard_data: Data
    ) {
        private val stationModule
            get() = Module(_id, module_name, data_type, dashboard_data)
        val allModules
            get() = modules + stationModule
    }

    data class Module(
        val _id: String, val module_name: String, val data_type: List<String>,
        val dashboard_data: Data
    )

    data class User(val administrative: UserSettings)
    data class UserSettings(val unit: Int, val windunit: Int, val pressureunit: Int)
    data class Data(val Temperature: Float, val CO2: Int, val Humidity: Int, val time_utc: Long)

    @GET("getmeasure?optimize=true")
    suspend fun getMeasurements(
        @Query("device_id") station_id: String,
        @Query("module_id") module_id: String,
        @Query("scale") scale: String,
        @Query("type") types: String,
        @Query("date_begin") date_begin: Int
    ): MeasurementsResponse

    data class MeasurementsResponse(
        val body: List<Measurements>, val status: String, val error: ErrorResponse
    )

    data class Measurements(val value: List<List<Number>>)
}