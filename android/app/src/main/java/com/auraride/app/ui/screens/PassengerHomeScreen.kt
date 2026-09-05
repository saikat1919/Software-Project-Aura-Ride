package com.auraride.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraride.app.data.Mock
import com.auraride.app.data.Trip
import com.auraride.app.ui.components.PrimaryButton
import com.auraride.app.ui.components.VerifiedBadge
import com.auraride.app.ui.components.VerifiedStrip

/**
 * Passenger home — request-a-ride dominant (design decision 3).
 * One obvious job in the 3-second scan; big bottom-anchored CTA; verified strip
 * up top; trips + payment demoted. Not a card mosaic.
 */
@Composable
fun PassengerHomeScreen(
    userName: String,
    onRequestRide: () -> Unit,
    onProfile: () -> Unit = {},
    onStepUp: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        VerifiedStrip()
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            // profile row (tap → profile)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onProfile),
            ) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(percent = 50))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) { Text("👩", fontSize = 22.sp) }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Good evening, $userName",
                        fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    VerifiedBadge()
                }
            }
            Spacer(Modifier.height(18.dp))

            // "Where to?" — taps through to the ride-request screen (address search there).
            Column(
                Modifier.fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                    .clickable(onClick = onRequestRide)
                    .padding(13.dp),
            ) {
                Text("WHERE TO?", fontSize = 10.5.sp, letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(3.dp))
                Text("Search destination", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(18.dp))

            Text("RECENT", fontSize = 10.5.sp, letterSpacing = 0.8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Mock.recentTrips.forEach { TripRow(it) }

            Spacer(Modifier.weight(1f))

            Text("Payment · bKash  ·  switch",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(4.dp))
            Text("Trigger security check (demo)",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.5.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).clickable(onClick = onStepUp))
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Request a ride", onRequestRide)
        }
    }
}

@Composable
private fun TripRow(trip: Trip) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(trip.destination, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Text("৳${trip.fareBdt} · ${trip.status}",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}
