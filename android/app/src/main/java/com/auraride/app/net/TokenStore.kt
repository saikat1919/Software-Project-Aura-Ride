package com.auraride.app.net

import android.content.Context

/** Persists JWTs + role. ponytail: SharedPreferences is fine for a demo; use
 *  EncryptedSharedPreferences if you ship. Tokens aren't the private key — that
 *  never leaves the Keystore (§17.1). */
class TokenStore(context: Context) {
    private val prefs = context.getSharedPreferences("aura_auth", Context.MODE_PRIVATE)

    var access: String?
        get() = prefs.getString("access", null)
        set(v) { prefs.edit().putString("access", v).apply() }

    var refresh: String?
        get() = prefs.getString("refresh", null)
        set(v) { prefs.edit().putString("refresh", v).apply() }

    var role: String?
        get() = prefs.getString("role", null)
        set(v) { prefs.edit().putString("role", v).apply() }

    var fullName: String?
        get() = prefs.getString("full_name", null)
        set(v) { prefs.edit().putString("full_name", v).apply() }

    /** Optional runtime override for the backend origin, e.g. "http://192.168.0.5:8000".
     *  When set, an OkHttp interceptor rewrites every request's host/port to this — so you
     *  can change the server IP in the app instead of rebuilding. Null = use BuildConfig. */
    var serverBaseUrl: String?
        get() = prefs.getString("server_base_url", null)
        set(v) { prefs.edit().putString("server_base_url", v?.ifBlank { null }).apply() }

    fun save(access: String?, refresh: String?, role: String? = null) {
        this.access = access
        if (refresh != null) this.refresh = refresh
        if (role != null) this.role = role
    }

    fun clear() = prefs.edit().clear().apply()
}
