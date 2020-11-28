package de.j4velin.simple.widget.netatmo.api

import android.content.Context
import android.util.Log
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationService
import net.openid.appauth.ClientSecretPost
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

const val TAG = "SimpleNetatmo"

interface NetatmoWeatherApi {

    companion object {
        fun getApi(
            context: Context,
            onError: (String) -> Unit,
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

        private fun getTokenAndApi(
            authState: AuthState,
            service: AuthorizationService,
            onError: (String) -> Unit,
            onSuccess: (NetatmoWeatherApi) -> Unit
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
            val okhttp = OkHttpClient.Builder().addInterceptor { chain ->
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

    @GET("getstationsdata?get_favorites=false")
    suspend fun getStations(): StationResponse

    data class StationResponse(val body: StationBody, val status: String) {
        fun getModule(id: String): Module? =
            body.devices.flatMap { s -> s.allModules }.find { r -> r._id == id }
    }

    data class StationBody(val devices: List<Station>, val user: User)
    data class Station(
        val home_name: String,
        val modules: List<Module>,
        private val _id: String,
        private val module_name: String,
        private val data_type: List<String>,
        private val dashboard_data: Data
    ) {
        val stationModule
            get() = Module(
                _id,
                module_name,
                data_type,
                dashboard_data
            )
        val allModules
            get() = modules + stationModule
    }

    data class Module(
        val _id: String,
        val module_name: String,
        val data_type: List<String>,
        val dashboard_data: Data
    )

    data class User(val administrative: UserSettings)
    data class UserSettings(val unit: Int, val windunit: Int, val pressureunit: Int)
    data class Data(val Temperature: Float, val CO2: Int, val Humidity: Int, val time_utc: Long)
}