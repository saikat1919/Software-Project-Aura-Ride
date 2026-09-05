package com.auraride.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraride.app.ui.components.PrimaryButton
import com.auraride.app.ui.components.SecondaryButton
import com.auraride.app.ui.components.StatusBanner
import com.auraride.app.ui.components.Tone
import com.auraride.app.ui.components.VerifiedBadge
import com.auraride.app.ui.components.VerifiedStrip
import com.auraride.app.net.GeoService
import com.auraride.app.ui.map.AuraMap
import com.auraride.app.ui.vm.PassengerRideViewModel
import org.maplibre.android.geometry.LatLng

/**
 * Live tracking — polls GET /rides/{id} (§17.3). Shows the matching state, then the
 * verified-driver card once accepted. Completes when the driver ends the trip. The real
 * MapLibre map (OpenFreeMap tiles) renders pickup/dropoff/driver markers.
 */
@Composable
fun RideTrackingScreen(
    rideId: Long,
    vm: PassengerRideViewModel,
    onCancel: () -> Unit,
    onCompleted: (String) -> Unit,
) {
    val state by vm.state.collectAsState()
    val ride = state.ride

    DisposableEffect(rideId) {
        vm.startPolling(rideId, onCompleted)
        onDispose { vm.stopPolling() }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        VerifiedStrip()
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            // Prominent status header — the rider always sees what's happening.
            when (ride?.status) {
                null, "REQUESTED", "MATCHING" -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Finding you a verified driver nearby…", fontWeight = FontWeight.Medium)
                }
                "CANCELLED" -> StatusBanner("No verified driver available right now. Try again in a moment.", Tone.WARN)
                else -> Text(statusLine(ride?.status), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            val pickup = ride?.pickup?.let { LatLng(it.lat, it.lng) }
            val dropoff = ride?.dropoff?.let { LatLng(it.lat, it.lng) }
            val driverLatLng = ride?.driver?.let { d ->
                if (d.lat != null && d.lng != null) LatLng(d.lat, d.lng) else null
            }
            var route by remember { mutableStateOf<List<LatLng>>(emptyList()) }
            LaunchedEffect(pickup, dropoff) {
                if (pickup != null && dropoff != null) route = GeoService.route(pickup, dropoff)
            }
            AuraMap(pickup, dropoff, driverLatLng, route,
                Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)))
            Spacer(Modifier.height(12.dp))

            ride?.driver?.let { DriverCard(it) }

            Spacer(Modifier.height(12.dp))
            if (state.error != null) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
            }
            if (ride?.status == "CANCELLED") {
                Text("No verified driver was available. Make sure a driver is online, then try again.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Back", onCancel)
            } else {
                SecondaryButton("Cancel ride", { vm.cancel(rideId, onCancel) })
            }
        }
    }
}

private fun statusLine(status: String?): String = when (status) {
    null, "REQUESTED", "MATCHING" -> "Finding you a verified driver…"
    "ACCEPTED" -> "Driver assigned"
    "ARRIVING" -> "Your driver is on the way"
    "IN_PROGRESS" -> "On the trip"
    "COMPLETED" -> "Trip complete"
    "CANCELLED" -> "Ride cancelled"
    else -> status
}

@Composable
private fun DriverCard(driver: com.auraride.app.net.DriverDto) {
    Row(
        Modifier.fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text("👩‍✈️", fontSize = 22.sp)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(driver.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.width(6.dp))
                VerifiedBadge()
            }
            Text("${driver.vehicle} · ${driver.plate}",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

