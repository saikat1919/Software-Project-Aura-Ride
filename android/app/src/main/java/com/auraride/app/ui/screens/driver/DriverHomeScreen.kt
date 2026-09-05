package com.auraride.app.ui.screens.driver

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraride.app.location.LocationProvider
import com.auraride.app.ui.components.*
import com.auraride.app.ui.map.AuraMap
import com.auraride.app.ui.vm.DriverViewModel
import org.maplibre.android.geometry.LatLng

/**
 * Driver dashboard (§13.4). Online toggle posts /driver/online (with a demo location) and
 * starts offer polling; earnings come from GET /driver/summary. When an offer arrives, we
 * navigate to the offer screen.
 */
@Composable
fun DriverHomeScreen(vm: DriverViewModel, userName: String, onProfile: () -> Unit, onIncomingOffer: () -> Unit) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var loc by remember { mutableStateOf<Pair<Double, Double>?>(null) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loc = LocationProvider.lastKnown(context)
    }
    LaunchedEffect(Unit) {
        vm.loadSummary()
        vm.resumeOfferPolling()   // revive polling after returning here post-ride
        if (LocationProvider.hasPermission(context)) loc = LocationProvider.current(context)
        else permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    LaunchedEffect(state.offer) { if (state.offer != null) onIncomingOffer() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        VerifiedStrip()
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onProfile)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Text("👩‍✈️", fontSize = 22.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(userName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    VerifiedBadge()
                }
            }
            Spacer(Modifier.height(18.dp))

            InfoCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(if (state.online) "You're online" else "You're offline", fontWeight = FontWeight.Bold)
                        Text(if (state.online) "Waiting for ride offers…" else "Go online to receive offers",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Switch(checked = state.online,
                        onCheckedChange = { vm.toggleOnline(it, loc?.first, loc?.second) })
                }
            }
            Spacer(Modifier.height(16.dp))

            SectionLabel("Earnings")
            Spacer(Modifier.height(6.dp))
            val s = state.summary
            InfoCard {
                EarnRow("Today", "৳${s?.todayEarnings ?: 0}")
                EarnRow("All time", "৳${s?.totalEarnings ?: 0}")
                EarnRow("Completed trips", "${s?.completedTrips ?: 0}")
            }
            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(state.error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
            Spacer(Modifier.height(16.dp))

            // While online, show a map centered on the driver so she sees her position
            // on the map (falls back to central Dhaka with no GPS fix).
            if (state.online) {
                val here = loc?.let { LatLng(it.first, it.second) } ?: LatLng(23.7520, 90.3900)
                AuraMap(null, null, here, emptyList(),
                    Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)))
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EarnRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Bold)
    }
}
