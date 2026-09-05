package com.auraride.app.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auraride.app.AuraApp
import com.auraride.app.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LoginState(
    val loading: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state

    /** onSuccess receives the backend role string ("PASSENGER" / "DRIVER"). */
    fun login(email: String, password: String, onSuccess: (String) -> Unit) {
        _state.value = LoginState(loading = true)
        viewModelScope.launch {
            try {
                val me = auth.login(email.trim(), password)
                _state.value = LoginState()
                onSuccess(me.role)
            } catch (e: Exception) {
                _state.value = LoginState(error = friendly(e))
            }
        }
    }

    private fun friendly(e: Exception): String = when {
        e.message?.contains("401") == true -> "Wrong email or password."
        e.message?.contains("403") == true -> "Account not active yet — still under review."
        else -> "Couldn't reach the server. Check the backend is running and BASE_URL is right."
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AuraApp
                LoginViewModel(app.container.authRepository)
            }
        }
    }
}
