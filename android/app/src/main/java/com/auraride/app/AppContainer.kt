package com.auraride.app

import android.app.Application
import android.content.Context
import com.auraride.app.data.AuthRepository
import com.auraride.app.data.RideRepository
import com.auraride.app.net.Network
import com.auraride.app.net.TokenStore

/** Tiny manual DI — one TokenStore + one ApiService shared by the repositories. */
class AppContainer(context: Context) {
    val tokenStore = TokenStore(context)   // public so the server-settings dialog can edit the override
    private val api = Network.apiService(tokenStore)

    val authRepository = AuthRepository(api, tokenStore)
    val rideRepository = RideRepository(api)
}

class AuraApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // MapLibre must be initialized once before any MapView is created.
        org.maplibre.android.MapLibre.getInstance(this)
    }
}
