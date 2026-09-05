package com.auraride.app.ui.screens.onboarding

import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.delay

/**
 * Verification pending. Polls /me/verification-status with the limited pending JWT
 * (Issue 1). ACTIVE → proceed; REJECTED → show the reason + let the user resubmit (§6.1).
 */
@Composable
fun PendingScreen(vm: RegisterViewModel, onApproved: () -> Unit, onResubmit: () -> Unit) {
    val state by vm.state.collectAsState()
    val rejected = state.accountStatus == "REJECTED"

    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshStatus(onApproved)
            delay(4000)
        }
    }

    if (rejected) {
        AuraScaffold(
            title = "Not approved",
            showVerified = false,
            bottom = { PrimaryButton("Resubmit verification", onResubmit) },
        ) {
            Spacer(Modifier.height(28.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("⚠️", fontSize = 56.sp) }
            Spacer(Modifier.height(18.dp))
            Text("Your verification wasn't approved", style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            StatusBanner(state.reason?.takeIf { it.isNotBlank() }
                ?: "The reviewer couldn't confirm your identity. Retake a clear selfie and NID and try again.",
                Tone.WARN)
            Spacer(Modifier.height(14.dp))
            Text("Tip: good lighting, face straight to the camera, and a sharp, glare-free NID photo.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        return
    }

    AuraScaffold(
        title = "Almost there",
        showVerified = false,
        bottom = { SecondaryButton("Check status now", { vm.refreshStatus(onApproved) }) },
    ) {
        Spacer(Modifier.height(28.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text("⏳", fontSize = 60.sp) }
        Spacer(Modifier.height(20.dp))
        Text("Verifying your identity", style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(
            "A human reviewer checks your selfie against your NID. We'll unlock your account " +
                "the moment you're approved.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        StatusBanner("Status: ${state.accountStatus ?: "PENDING_REVIEW"}", Tone.INFO)
        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(state.error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}
