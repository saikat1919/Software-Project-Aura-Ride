package com.auraride.app.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.auraride.app.ui.components.AuraScaffold
import com.auraride.app.ui.components.PrimaryButton

@Composable
fun OtpScreen(onBack: () -> Unit, onVerified: () -> Unit) {
    var code by remember { mutableStateOf("") }
    AuraScaffold(
        title = "Verify phone",
        onBack = onBack,
        showVerified = false,
        bottom = { PrimaryButton("Verify", onVerified) },
    ) {
        Text(
            "We sent a 4-digit code to your phone.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            code, { if (it.length <= 4) code = it },
            label = { Text("Code") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
    }
}
