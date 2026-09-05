package com.auraride.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.maplibre.android.geometry.LatLng
import java.net.URLEncoder

/**
 * Free OpenStreetMap geo services (§17.10), no key:
 *  - Nominatim for address → coordinates (respect its policy: identifying User-Agent, low rate).
 *  - OSRM for the driving route geometry (public demo server; swap OSRM_BASE for a self-hosted
 *    Docker instance for anything you actually present — it's rate-limited).
 * Both calls are best-effort and never throw; callers fall back gracefully.
 */
object GeoService {
    // ponytail: point OSRM_BASE at your local Docker OSRM for the demo; the public server
    // is rate-limited and has no SLA (eng review P5).
    private const val NOMINATIM = "https://nominatim.openstreetmap.org"
    private const val OSRM_BASE = "https://router.project-osrm.org"
    private const val USER_AGENT = "AuraRide-demo/0.1 (university project)"

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /** One address suggestion: a short label + its coordinates. */
    data class Place(val label: String, val lat: Double, val lng: Double)

    /** Autocomplete: up to 5 matches for a partial query, boxed near the rider (§17.10).
     *  Empty on blank/short query or failure. Callers debounce to respect Nominatim's rate limit. */
    suspend fun suggest(query: String, near: LatLng? = null): List<Place> = withContext(Dispatchers.IO) {
        if (query.trim().length < 2) return@withContext emptyList()
        val url = "$NOMINATIM/search?format=json&limit=5&countrycodes=bd${boxParam(near)}&q=" +
            URLEncoder.encode(query, "UTF-8")
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                json.parseToJsonElement(resp.body?.string().orEmpty()).jsonArray.mapNotNull { el ->
                    val o = el.jsonObject
                    val name = o["display_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    Place(
                        name.split(",").take(3).joinToString(",").trim(),
                        o["lat"]!!.jsonPrimitive.content.toDouble(),
                        o["lon"]!!.jsonPrimitive.content.toDouble(),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    // ±0.75° ≈ 80km box around the rider (bounded), so ambiguous names resolve locally.
    private fun boxParam(near: LatLng?): String = near?.let {
        val d = 0.75
        "&bounded=1&viewbox=${it.longitude - d},${it.latitude - d},${it.longitude + d},${it.latitude + d}"
    } ?: ""

    suspend fun geocode(address: String, near: LatLng? = null): LatLng? = withContext(Dispatchers.IO) {
        if (address.isBlank()) return@withContext null
        // countrycodes=bd + a box around the pickup so an ambiguous name ("Mirpur", of which
        // Bangladesh has several) resolves to the one near the rider, not the highest-importance
        // one countrywide (see boxParam).
        val url = "$NOMINATIM/search?format=json&limit=1&countrycodes=bd${boxParam(near)}&q=" +
            URLEncoder.encode(address, "UTF-8")
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val first = json.parseToJsonElement(body).jsonArray.firstOrNull()?.jsonObject
                    ?: return@use null
                LatLng(
                    first["lat"]!!.jsonPrimitive.content.toDouble(),
                    first["lon"]!!.jsonPrimitive.content.toDouble(),
                )
            }
        }.getOrNull()
    }

    /** Coordinates → a human address (Nominatim /reverse). Null on failure. */
    suspend fun reverseGeocode(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        val url = "$NOMINATIM/reverse?format=json&zoom=18&lat=$lat&lon=$lng"
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                json.parseToJsonElement(body).jsonObject["display_name"]?.jsonPrimitive?.content
            }
        }.getOrNull()
    }

    /** Route geometry pickup → dropoff. Empty list on failure (caller draws a straight line). */
    suspend fun route(pickup: LatLng, dropoff: LatLng): List<LatLng> = withContext(Dispatchers.IO) {
        val url = "$OSRM_BASE/route/v1/driving/" +
            "${pickup.longitude},${pickup.latitude};${dropoff.longitude},${dropoff.latitude}" +
            "?overview=full&geometries=geojson"
        runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                val coords = json.parseToJsonElement(body).jsonObject["routes"]!!.jsonArray[0]
                    .jsonObject["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
                coords.map { c ->
                    val p = c.jsonArray
                    LatLng(p[1].jsonPrimitive.double, p[0].jsonPrimitive.double)  // geojson is [lon,lat]
                }
            }
        }.getOrDefault(emptyList())
    }
}
