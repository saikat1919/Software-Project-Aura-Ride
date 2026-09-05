package com.auraride.app.ui.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraride.app.ui.theme.AuraColors

/** Standard screen shell: verified strip, header with optional back, scrolling body, sticky bottom. */
@Composable
fun AuraScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    showVerified: Boolean = true,
    bottom: @Composable (() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (showVerified) VerifiedStrip()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Text("‹", fontSize = 30.sp, fontWeight = FontWeight.Light,
                    modifier = Modifier.clickable(onClick = onBack).padding(end = 12.dp))
            }
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), content = body)
        if (bottom != null) Box(Modifier.padding(16.dp)) { bottom() }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 10.5.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** Read/tappable field row (real: opens a picker/keyboard/search). */
@Composable
fun LabeledField(label: String, value: String, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(13.dp),
    ) {
        SectionLabel(label)
        Spacer(Modifier.height(3.dp))
        Text(value, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun InfoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(14.dp),
        content = content,
    )
}

enum class Tone { INFO, WARN, VERIFIED }

@Composable
fun StatusBanner(text: String, tone: Tone, modifier: Modifier = Modifier) {
    val ext = AuraColors.current
    val (bg, fg) = when (tone) {
        Tone.INFO -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        Tone.WARN -> MaterialTheme.colorScheme.surfaceVariant to ext.warn
        Tone.VERIFIED -> ext.verifiedContainer to ext.verified
    }
    Row(
        modifier.fillMaxWidth().background(bg, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tone == Tone.VERIFIED) { ShieldCheck(color = ext.verified, sizeDp = 15); Spacer(Modifier.width(8.dp)) }
        Text(text, color = fg, fontWeight = FontWeight.Medium, fontSize = 13.5.sp)
    }
}

/** Two-chip Passenger/Driver selector used in register + login. */
@Composable
fun RoleToggle(selected: com.auraride.app.data.Role, onSelect: (com.auraride.app.data.Role) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        com.auraride.app.data.Role.entries.forEach { role ->
            val active = role == selected
            Box(
                Modifier.weight(1f)
                    .background(
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(12.dp),
                    )
                    .clickable { onSelect(role) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (role == com.auraride.app.data.Role.PASSENGER) "Passenger" else "Driver",
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Mock biometric confirmation (real: BiometricPrompt + Keystore signature over a
 * server nonce, §17.1). Stand-in AlertDialog so the ride request/accept flows demo.
 */
@Composable
fun BiometricConfirm(action: String, onDismiss: () -> Unit, onAuthenticated: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm it's you") },
        text = { Text("$action requires your fingerprint. This signs a one-time code on your device — nothing leaves the phone.") },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onAuthenticated) { Text("Authenticate") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
