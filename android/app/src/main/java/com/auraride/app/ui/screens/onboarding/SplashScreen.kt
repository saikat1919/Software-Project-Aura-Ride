package com.auraride.app.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.auraride.app.AuraApp
import com.auraride.app.BuildConfig
import com.auraride.app.ui.components.PrimaryButton
import com.auraride.app.ui.components.SecondaryButton
import com.auraride.app.ui.components.ShieldCheck

@Composable
fun SplashScreen(onCreateAccount: () -> Unit, onLogin: () -> Unit) {
    val store = (LocalContext.current.applicationContext as AuraApp).container.tokenStore
    var showServer by remember { mutableStateOf(false) }
    var currentServer by remember { mutableStateOf(store.serverBaseUrl ?: defaultOrigin()) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        ShieldCheck(color = MaterialTheme.colorScheme.primary, sizeDp = 72)
        Spacer(Modifier.height(20.dp))
        Text("Aura Ride", fontWeight = FontWeight.Bold, fontSize = 34.sp,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Rides for women, by women.\nEveryone here is identity-verified.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton("Create account", onCreateAccount)
        Spacer(Modifier.height(10.dp))
        SecondaryButton("I already have an account", onLogin)
        Spacer(Modifier.height(12.dp))
        Text("Server: $currentServer  ·  change", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable { showServer = true })
    }

    if (showServer) {
        var input by remember { mutableStateOf(store.serverBaseUrl ?: defaultOrigin()) }
        AlertDialog(
            onDismissRequest = { showServer = false },
            title = { Text("Backend server") },
            text = {
                Column {
                    Text("Enter the backend address (e.g. http://192.168.0.5:8000).", fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(input, { input = it }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    store.serverBaseUrl = normalize(input)
                    currentServer = store.serverBaseUrl ?: defaultOrigin()
                    showServer = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    store.serverBaseUrl = null
                    currentServer = defaultOrigin()
                    showServer = false
                }) { Text("Use built-in") }
            },
        )
    }
}

/** Origin (scheme://host:port) baked into BuildConfig.BASE_URL. */
private fun defaultOrigin(): String =
    BuildConfig.BASE_URL.removeSuffix("/api/v1/").removeSuffix("/")

/** Accept "ip:8000" or "http://ip:8000"; default port 8000, scheme http. */
private fun normalize(raw: String): String? {
    val t = raw.trim().ifBlank { return null }
    val withScheme = if (t.startsWith("http://") || t.startsWith("https://")) t else "http://$t"
    return if (Regex(":\\d+").containsMatchIn(withScheme.substringAfter("://"))) withScheme
    else "$withScheme:8000"
}
