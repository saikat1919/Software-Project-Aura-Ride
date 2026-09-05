package com.auraride.app.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.auraride.app.data.Role
import com.auraride.app.ui.components.AuraScaffold
import com.auraride.app.ui.components.PrimaryButton
import com.auraride.app.ui.components.RoleToggle
import com.auraride.app.ui.components.SectionLabel
import com.auraride.app.ui.vm.RegisterViewModel

@Composable
fun RegisterInfoScreen(vm: RegisterViewModel, onBack: () -> Unit, onContinue: (Role) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(Role.PASSENGER) }

    AuraScaffold(
        title = "Create account",
        onBack = onBack,
        showVerified = false,
        bottom = {
            PrimaryButton("Continue", {
                vm.setInfo(name.trim(), phone.trim(), email.trim(), password, role)
                onContinue(role)
            })
        },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionLabel("I am a")
            Spacer(Modifier.height(6.dp))
            RoleToggle(selected = role, onSelect = { role = it })
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(name, { name = it }, label = { Text("Full name") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(email, { email = it }, label = { Text("Email") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(password, { password = it }, label = { Text("Password") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Text(
                "Next: verify your phone, prove you're a live person, and scan your NID." +
                    if (role == Role.DRIVER) " Drivers also add a license + vehicle." else "",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
