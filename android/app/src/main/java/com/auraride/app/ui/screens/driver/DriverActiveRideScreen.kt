package com.auraride.app.ui.screens.driver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraride.app.data.PaymentMethod
import com.auraride.app.location.LocationProvider
import com.auraride.app.net.GeoService
import com.auraride.app.ui.components.*
import com.auraride.app.ui.map.AuraMap
import com.auraride.app.ui.vm.DriverViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.maplibre.android.geometry.LatLng

/**
 * Driver active-ride state machine (§8.1). Each button drives the real transition endpoint
 * (guarded server-side, §17.8); cash is confirmed with POST /driver/rides/{id}/cash-paid (§9.2).
 */
@Composable
fun DriverActiveRideScreen(vm: DriverViewModel, rideId: Long, onFinished: () -> Unit) {
    val state by vm.state.collectAsState()
    val ride = state.activeRide
    val status = ride?.status ?: "ACCEPTED"
    val isCash = (ride?.paymentMethod ?: "BKASH") == PaymentMethod.CASH.name
    val fare = ride?.finalFare ?: ride?.estimatedFare ?: 0

    val context = LocalContext.current
    var driverLoc by remember { mutableStateOf<LatLng?>(null) }
    // Push the driver's live GPS every 4s: powers the passenger's live tracking and the
    // backend geofence (arrive-at-pickup). Loop dies when the screen leaves.
    LaunchedEffect(rideId) {
        while (isActive) {
            LocationProvider.current(context)?.let { (lat, lng) ->
                driverLoc = LatLng(lat, lng)
                vm.pushLocation(lat, lng)
            }
            delay(4000)
        }
    }

    AuraScaffold(
        title = "Current ride",
        bottom = {
            if (state.loading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val (label, action) = ctaFor(status, isCash, fare, rideId, vm, onFinished)
                PrimaryButton(label, action)
            }
        },
    ) {
        StatusBanner("Riding with ${ride?.passenger?.ifBlank { "your passenger" } ?: "your passenger"} · verified", Tone.VERIFIED)
        Spacer(Modifier.height(12.dp))

        // Live map: pickup, dropoff, road route + the driver's own position.
        val pickup = ride?.pickup?.let { LatLng(it.lat, it.lng) }
        val dropoff = ride?.dropoff?.let { LatLng(it.lat, it.lng) }
        // Prefer this phone's live fix; fall back to the server's last-known driver point.
        val driverLatLng = driverLoc ?: ride?.driver?.let { d ->
            if (d.lat != null && d.lng != null) LatLng(d.lat, d.lng) else null
        }
        var route by remember { mutableStateOf<List<LatLng>>(emptyList()) }
        LaunchedEffect(pickup, dropoff) {
            if (pickup != null && dropoff != null) route = GeoService.route(pickup, dropoff)
        }
        AuraMap(pickup, dropoff, driverLatLng, route,
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)))
        Spacer(Modifier.height(10.dp))

        Text(titleFor(status), style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        InfoCard {
            InfoRow("Pickup", ride?.pickup?.address ?: "—")
            Spacer(Modifier.height(8.dp))
            InfoRow("Dropoff", ride?.dropoff?.address ?: "—")
            Spacer(Modifier.height(8.dp))
            InfoRow("Fare", "৳$fare · ${if (isCash) "Cash" else "bKash"}")
        }
        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(state.error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
    }
}

private fun ctaFor(
    status: String, isCash: Boolean, fare: Int, rideId: Long, vm: DriverViewModel, onFinished: () -> Unit,
): Pair<String, () -> Unit> = when (status) {
    "ACCEPTED" -> "I've arrived" to { vm.arrive(rideId) }
    "ARRIVING" -> "Start trip" to { vm.start(rideId) }
    "IN_PROGRESS" -> "Complete trip" to { vm.complete(rideId) }
    "COMPLETED" -> if (isCash) "Confirm ৳$fare cash received" to { vm.cashPaid(rideId, onFinished) }
                   else "Done" to onFinished
    else -> "Done" to onFinished
}

private fun titleFor(status: String) = when (status) {
    "ACCEPTED" -> "Head to pickup"
    "ARRIVING" -> "Arriving at pickup"
    "IN_PROGRESS" -> "Trip in progress"
    "COMPLETED" -> "Collect payment"
    else -> status
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(72.dp), fontSize = 13.sp)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
