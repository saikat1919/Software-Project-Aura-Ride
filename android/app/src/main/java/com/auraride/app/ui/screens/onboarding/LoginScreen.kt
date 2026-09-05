package com.auraride.app.ui.screens.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auraride.app.data.Role
import com.auraride.app.ui.components.AuraScaffold
import com.auraride.app.ui.components.PrimaryButton
import com.auraride.app.ui.vm.LoginViewModel

@Composable
fun LoginScreen(onBack: () -> Unit, onLogin: (Role) -> Unit) {
    val vm: LoginViewModel = viewModel(factory = LoginViewModel.Factory)
    val state by vm.state.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AuraScaffold(
        title = "Log in",
        onBack = onBack,
        showVerified = false,
        bottom = {
            if (state.loading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                // Real login: role comes from the backend (/me), not a toggle.
                PrimaryButton("Log in", {
                    vm.login(email, password) { role -> onLogin(Role.valueOf(role)) }
                })
            }
        },
    ) {
        OutlinedTextField(email, { email = it }, label = { Text("Email or phone") },
            singleLine = true, enabled = !state.loading, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Password") },
            singleLine = true, enabled = !state.loading,
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(state.error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
    }
}
