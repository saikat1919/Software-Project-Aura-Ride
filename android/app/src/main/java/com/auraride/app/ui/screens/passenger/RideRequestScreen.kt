package com.auraride.app.ui.screens.passenger

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.auraride.app.data.PaymentMethod
import com.auraride.app.location.LocationProvider
import com.auraride.app.net.Coord
import com.auraride.app.net.GeoService
import com.auraride.app.ui.components.*
import com.auraride.app.ui.vm.PassengerRideViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

// Fallback pickup (central Dhaka) if the device has no location fix / permission.
private val DEFAULT_PICKUP = Coord(23.7500, 90.3900, "Dhaka")

/**
 * Pickup from device GPS, dropoff geocoded via Nominatim (§17.10), estimate + biometric
 * request against the backend. "Confirm" fires BiometricPrompt over the Keystore key.
 */
@Composable
fun RideRequestScreen(
    vm: PassengerRideViewModel,
    activity: FragmentActivity?,
    onBack: () -> Unit,
    onConfirmed: (Long, String) -> Unit,
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pickup by remember { mutableStateOf(DEFAULT_PICKUP) }
    var pickupLabel by remember { mutableStateOf("Locating…") }
    var dropoffText by remember { mutableStateOf("") }
    var dropoff by remember { mutableStateOf<Coord?>(null) }
    var suggestions by remember { mutableStateOf<List<GeoService.Place>>(emptyList()) }
    var method by remember { mutableStateOf(PaymentMethod.BKASH) }
    var geocoding by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    // A resolved dropoff (from a suggestion or a prior geocode) gates the estimate/confirm UI;
    // editing the text clears it, flipping the button back to "Get estimate".
    val estimated = state.estimate != null && dropoff != null

    // Debounced address autocomplete. Re-runs on each keystroke; the 300ms delay is cancelled
    // when the text changes again, so only a typing pause hits Nominatim (rate-limit friendly).
    LaunchedEffect(dropoffText) {
        if (dropoff != null || dropoffText.trim().length < 2) { suggestions = emptyList(); return@LaunchedEffect }
        delay(300)
        suggestions = GeoService.suggest(dropoffText, LatLng(pickup.lat, pickup.lng))
    }

    fun useLocation() {
        scope.launch {
            // Fresh GPS fix, not the stale cache — otherwise the pin lands a sector off.
            val fix = LocationProvider.current(context)
            if (fix == null) { pickupLabel = "Current location unavailable — using Dhaka"; return@launch }
            pickup = Coord(fix.first, fix.second, "Current location")
            pickupLabel = "Current location"
            // Reverse-geocode to a readable street address (best-effort).
            GeoService.reverseGeocode(fix.first, fix.second)?.let { addr ->
                val short = addr.split(",").take(3).joinToString(",").trim()
                pickupLabel = short
                pickup = pickup.copy(address = short)
            }
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) useLocation() else pickupLabel = "Location off — using Dhaka"
    }
    LaunchedEffect(Unit) {
        vm.reset()   // clear any stale estimate/ride from a previous request
        if (LocationProvider.hasPermission(context)) useLocation()
        else permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun getEstimate() {
        localError = null
        scope.launch {
            // Prefer a coordinate the rider already picked from suggestions; else geocode the text.
            val d = dropoff ?: run {
                geocoding = true
                val geo = GeoService.geocode(dropoffText, LatLng(pickup.lat, pickup.lng))
                geocoding = false
                if (geo == null) { localError = "Couldn't find that address. Try a more specific one."; return@launch }
                Coord(geo.latitude, geo.longitude, dropoffText).also { dropoff = it }
            }
            vm.estimate(pickup, d)
        }
    }

    AuraScaffold(
        title = "Where to?",
        onBack = onBack,
        bottom = {
            when {
                state.loading || geocoding ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                estimated -> PrimaryButton("Confirm with fingerprint", {
                    val d = dropoff ?: return@PrimaryButton
                    if (activity != null) vm.request(activity, pickup, d, method.name) { id -> onConfirmed(id, method.name) }
                })
                else -> PrimaryButton("Get estimate", { getEstimate() })
            }
        },
    ) {
        LabeledField("PICKUP", pickupLabel)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            dropoffText,
            { dropoffText = it; dropoff = null },   // editing invalidates a prior selection
            label = { Text("Dropoff address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Autocomplete dropdown — tap a match to lock in its coordinates.
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            InfoCard {
                suggestions.forEachIndexed { i, place ->
                    if (i > 0) Spacer(Modifier.height(2.dp))
                    Text(
                        place.label,
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                dropoffText = place.label
                                dropoff = Coord(place.lat, place.lng, place.label)
                                suggestions = emptyList()
                            }
                            .padding(vertical = 10.dp),
                        fontSize = 14.sp,
                    )
                }
            }
        }

        if (estimated) state.estimate?.let { est ->
            Spacer(Modifier.height(18.dp))
            InfoCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Estimated fare", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        Text("৳${est.estimatedFare}", fontWeight = FontWeight.Bold, fontSize = 26.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${est.distance} km · ${est.duration} min",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            SectionLabel("Pay with")
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PaymentMethod.entries.forEach { m ->
                    val active = m == method
                    Box(
                        Modifier.weight(1f)
                            .background(
                                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(12.dp))
                            .clickable { method = m }
                            .padding(vertical = 13.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(m.label, fontWeight = FontWeight.Bold,
                            color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        (localError ?: state.error)?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
    }
}
