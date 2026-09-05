package com.auraride.app.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraride.app.ui.components.*
import com.auraride.app.ui.vm.RegisterViewModel

/**
 * Biometric key enrollment + registration submit (§17.1). "Enroll" generates the
 * hardware-backed EC key (silent, no prompt) and posts the registration with only the
 * public key. On success the account is PENDING_REVIEW and we move to the pending screen.
 */
@Composable
fun BiometricEnrollScreen(vm: RegisterViewModel, onBack: () -> Unit, onEnrolled: () -> Unit) {
    val state by vm.state.collectAsState()

    AuraScaffold(
        title = "Secure your account",
        onBack = onBack,
        showVerified = false,
        bottom = {
            if (state.loading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                PrimaryButton("Enroll fingerprint & submit", { vm.submit { onEnrolled() } })
            }
        },
    ) {
        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("🔒", fontSize = 64.sp) }
        Spacer(Modifier.height(20.dp))
        Text(
            "Aura Ride binds your account to this phone. Your fingerprint unlocks a key that " +
                "never leaves the device — we only store the public half.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        StatusBanner("You'll confirm with this fingerprint every time you request or accept a ride.", Tone.INFO)
        if (state.error != null) {
            Spacer(Modifier.height(14.dp))
            Text(state.error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(), fontSize = 13.sp)
        }
    }
}
