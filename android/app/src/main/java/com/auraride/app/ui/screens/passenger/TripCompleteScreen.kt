package com.auraride.app.ui.screens.passenger

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import com.auraride.app.data.PaymentMethod
import com.auraride.app.ui.components.*
import com.auraride.app.ui.vm.PassengerRideViewModel


@Composable
fun TripCompleteScreen(rideId: Long, method: String, vm: PassengerRideViewModel, onDone: () -> Unit) {
    val state by vm.state.collectAsState()
    val fare = state.ride?.finalFare ?: state.ride?.estimatedFare ?: 0
    val isCash = method == PaymentMethod.CASH.name
    var paid by remember { mutableStateOf(isCash) }
    var bkashNumber by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }

    AuraScaffold(
        title = "Trip complete",
        bottom = {
            when {
                state.loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                paid -> PrimaryButton("Done", onDone)
                else -> PrimaryButton("Pay ৳$fare with bKash", { vm.payBkash(rideId) { paid = true } })
            }
        },
    ) {
        StatusBanner("You rode with a verified driver.", Tone.VERIFIED)
        Spacer(Modifier.height(14.dp))
        InfoCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Total fare", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("৳$fare", fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
        }
        Spacer(Modifier.height(16.dp))

        if (isCash) {
            SectionLabel("Cash")
            Spacer(Modifier.height(6.dp))
            Text("Pay ৳$fare to your driver in cash. She confirms receipt in her app.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (!paid) {
            SectionLabel("bKash (demo — no real money)")
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(bkashNumber, { bkashNumber = it }, label = { Text("bKash number") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(pin, { if (it.length <= 5) pin = it }, label = { Text("PIN") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth())
        } else {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("✅", fontSize = 52.sp) }
            Spacer(Modifier.height(8.dp))
            Text("Payment complete", fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(state.error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
    }
}
