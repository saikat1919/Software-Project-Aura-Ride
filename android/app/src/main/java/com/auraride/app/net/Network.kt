package com.auraride.app.net

import com.auraride.app.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit + OkHttp wiring. BASE_URL comes from BuildConfig (per-developer, §android
 * CLAUDE.md). Adds the access token to every request and transparently refreshes on 401.
 */
object Network {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    fun apiService(store: TokenStore): ApiService {
        val client = OkHttpClient.Builder()
            // Registration/step-up run DeepFace synchronously on the server (face match + gender);
            // the first call also builds the models. That far exceeds OkHttp's 10s default, which
            // surfaced as a false "can't reach the server". Give the AI requests room to finish.
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .callTimeout(150, TimeUnit.SECONDS)
            .addInterceptor(ServerOverrideInterceptor(store))
            .addInterceptor(AuthInterceptor(store))
            .authenticator(RefreshAuthenticator(store, json))
            .apply {
                if (BuildConfig.DEBUG) addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}

/** Rewrites the host/port to store.serverBaseUrl when set — lets the user change the
 *  backend IP in the app (no rebuild). Path + query are preserved. */
private class ServerOverrideInterceptor(private val store: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val override = store.serverBaseUrl?.toHttpUrlOrNull()
        val req = if (override != null) {
            val url = chain.request().url.newBuilder()
                .scheme(override.scheme).host(override.host).port(override.port).build()
            chain.request().newBuilder().url(url).build()
        } else chain.request()
        return chain.proceed(req)
    }
}

/** Attaches "Authorization: Bearer <access>" when we have one. */
private class AuthInterceptor(private val store: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val access = store.access
        val req = if (access != null && chain.request().header("Authorization") == null) {
            chain.request().newBuilder().header("Authorization", "Bearer $access").build()
        } else chain.request()
        return chain.proceed(req)
    }
}

/** On 401, refresh once with the refresh token and retry; on failure, clear + give up. */
private class RefreshAuthenticator(
    private val store: TokenStore,
    private val json: Json,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null       // already retried
        val refresh = store.refresh ?: return null
        val newAccess = synchronized(this) { blockingRefresh(refresh) } ?: run {
            store.clear(); return null
        }
        store.access = newAccess
        return response.request.newBuilder()
            .header("Authorization", "Bearer $newAccess").build()
    }

    /** Refresh must hit the SAME server the app is actually talking to. If the user set a
     *  runtime server override (splash dialog), other requests go there but BuildConfig.BASE_URL
     *  may be a stale IP — refresh would silently fail and log the user out. Honor the override. */
    private fun refreshUrl(): String {
        val base = BuildConfig.BASE_URL
        val override = store.serverBaseUrl?.toHttpUrlOrNull() ?: return base + "auth/refresh"
        val baseUrl = base.toHttpUrlOrNull() ?: return base + "auth/refresh"
        return baseUrl.newBuilder()
            .scheme(override.scheme).host(override.host).port(override.port)
            .build().toString() + "auth/refresh"
    }

    private fun blockingRefresh(refresh: String): String? {
        // Bare client (no interceptor/authenticator) to avoid recursion.
        val client = OkHttpClient()
        val body = FormBody.Builder().add("refresh", refresh).build()
        val req = Request.Builder().url(refreshUrl()).post(body).build()
        return try {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val text = resp.body?.string() ?: return null
                json.decodeFromString(TokenResponse.serializer(), text).access
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun responseCount(response: Response): Int {
        var r: Response? = response; var count = 1
        while (r?.priorResponse != null) { count++; r = r.priorResponse }
        return count
    }
}
