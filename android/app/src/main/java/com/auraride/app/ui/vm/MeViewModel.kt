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

data class MeState(
    val name: String = "",
    val role: String = "PASSENGER",
    val status: String = "",
)

/** The signed-in user. Seeds from cached values (instant), then refreshes from /me. */
class MeViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(
        MeState(name = auth.cachedName().orEmpty(), role = auth.cachedRole() ?: "PASSENGER")
    )
    val state: StateFlow<MeState> = _state

    fun refresh() {
        viewModelScope.launch {
            runCatching { auth.me() }.onSuccess { me ->
                _state.value = MeState(me.fullName, me.role, me.status)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AuraApp
                MeViewModel(app.container.authRepository)
            }
        }
    }
}
