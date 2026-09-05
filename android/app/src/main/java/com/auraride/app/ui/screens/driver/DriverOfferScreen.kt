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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.auraride.app.net.GeoService
import com.auraride.app.ui.components.*
import com.auraride.app.ui.map.AuraMap
import com.auraride.app.ui.vm.DriverViewModel
import org.maplibre.android.geometry.LatLng

/**
 * Incoming ride offer (§8.2, §17.9). Accept is biometric-signed and bound to the ride
 * (§17.1 / Issue 3); the backend enforces first-accept-wins (§17.8) — a lost race returns
 * "offer expired".
 */
@Composable
fun DriverOfferScreen(
    vm: DriverViewModel,
    activity: FragmentActivity?,
    onDecline: () -> Unit,
    onAccept: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val offer = state.offer

    AuraScaffold(
        title = "New ride offer",
        bottom = {
            when {
                state.loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                offer == null -> SecondaryButton("Back", onDecline)
                else -> Column(Modifier.fillMaxWidth()) {
                    PrimaryButton("Accept with fingerprint", {
                        if (activity != null) vm.accept(activity, offer.id) { onAccept() }
                    })
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("Decline", { vm.declineOffer(); onDecline() })
                }
            }
        },
    ) {
        if (offer == null) {
            Text("No active offer right now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@AuraScaffold
        }
        StatusBanner("Offer expires soon — respond quickly.", Tone.WARN)
        Spacer(Modifier.height(14.dp))
        InfoCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(offer.passenger.ifBlank { "Passenger" }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.width(6.dp))
                VerifiedBadge()
                Spacer(Modifier.weight(1f))
                Text("৳${offer.estimatedFare}", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Spacer(Modifier.height(12.dp))
            OfferLine("Pickup", offer.pickup?.address ?: "—")
            Spacer(Modifier.height(8.dp))
            OfferLine("Dropoff", offer.dropoff?.address ?: "—")
        }
        Spacer(Modifier.height(12.dp))

        // Map of the requested trip so the driver sees where it goes before accepting.
        val pickup = offer.pickup?.let { LatLng(it.lat, it.lng) }
        val dropoff = offer.dropoff?.let { LatLng(it.lat, it.lng) }
        var route by remember { mutableStateOf<List<LatLng>>(emptyList()) }
        LaunchedEffect(pickup, dropoff) {
            if (pickup != null && dropoff != null) route = GeoService.route(pickup, dropoff)
        }
        AuraMap(pickup, dropoff, null, route,
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)))

        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(state.error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
    }
}

@Composable
private fun OfferLine(label: String, value: String) {
    Column {
        SectionLabel(label)
        Spacer(Modifier.height(2.dp))
        Text(value, fontWeight = FontWeight.Medium)
    }
}
