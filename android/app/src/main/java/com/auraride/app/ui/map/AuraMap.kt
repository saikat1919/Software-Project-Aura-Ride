package com.auraride.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

// OpenFreeMap "liberty" vector style — OSM tiles, no key, no billing (§17.10).
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val ROUTE_SRC = "aura-route-src"
private const val ROUTE_LAYER = "aura-route-layer"

/** MapView with its lifecycle driven eagerly (fine for a foregrounded demo screen). */
@Composable
private fun rememberMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember { MapView(context).apply { onCreate(null); onStart(); onResume() } }
    DisposableEffect(mapView) {
        onDispose { mapView.onPause(); mapView.onStop(); mapView.onDestroy() }
    }
    return mapView
}

/**
 * MapLibre map: pickup / dropoff / driver markers + an OSRM route polyline. The driver
 * marker moves as the caller feeds fresh coordinates from ride polling (§8.3); the route
 * is the geometry from GeoService.route (empty = no line drawn).
 */
@Composable
fun AuraMap(
    pickup: LatLng?,
    dropoff: LatLng?,
    driver: LatLng?,
    route: List<LatLng> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val mapView = rememberMapView()
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }

    AndroidView(factory = { mapView }, modifier = modifier)

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.setStyle(Style.Builder().fromUri(STYLE_URL)) { s ->
                s.addSource(GeoJsonSource(ROUTE_SRC))
                s.addLayer(
                    LineLayer(ROUTE_LAYER, ROUTE_SRC).withProperties(
                        PropertyFactory.lineColor("#1B8A5A"),
                        PropertyFactory.lineWidth(5f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    )
                )
                map = m
                style = s
            }
        }
    }

    LaunchedEffect(map, pickup, dropoff, driver) {
        val m = map ?: return@LaunchedEffect
        m.clear()  // clears markers only; the route is a style layer and survives
        pickup?.let { m.addMarker(MarkerOptions().position(it).title("Pickup")) }
        dropoff?.let { m.addMarker(MarkerOptions().position(it).title("Dropoff")) }
        driver?.let { m.addMarker(MarkerOptions().position(it).title("Driver")) }
        (driver ?: pickup ?: dropoff)?.let { m.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 14.0)) }
    }

    LaunchedEffect(style, route) {
        val src = style?.getSourceAs<GeoJsonSource>(ROUTE_SRC) ?: return@LaunchedEffect
        if (route.size >= 2) {
            src.setGeoJson(LineString.fromLngLats(route.map { Point.fromLngLat(it.longitude, it.latitude) }))
        }
    }
}
