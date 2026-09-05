package com.auraride.app.ui.screens

import androidx.compose.foundation.background
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
import com.auraride.app.data.Role
import com.auraride.app.ui.components.*

@Composable
fun ProfileScreen(role: Role, userName: String, onBack: () -> Unit, onLogout: () -> Unit) {
    val name = userName
    AuraScaffold(
        title = "Profile",
        onBack = onBack,
        bottom = { SecondaryButton("Log out", onLogout) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Text(if (role == Role.DRIVER) "👩‍✈️" else "👩", fontSize = 28.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(if (role == Role.DRIVER) "Driver" else "Passenger",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                VerifiedBadge()
            }
        }
        Spacer(Modifier.height(18.dp))
        StatusBanner("Your identity is verified and active.", Tone.VERIFIED)
        Spacer(Modifier.height(16.dp))

        SectionLabel("Account")
        Spacer(Modifier.height(6.dp))
        InfoCard {
            Line("Verification", "Active")
            Line("Default payment", "bKash")
            if (role == Role.DRIVER) Line("Vehicle", "Toyota Axio · DHA-14-5521")
            Line("Device", "This phone (biometric key enrolled)")
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Medium)
    }
}
