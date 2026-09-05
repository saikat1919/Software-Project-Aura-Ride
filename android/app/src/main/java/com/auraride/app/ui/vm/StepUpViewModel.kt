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

data class StepUpUi(
    val loading: Boolean = false,
    val verdict: String? = null,   // PASS / REVIEW / FAIL
    val error: String? = null,
)

class StepUpViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(StepUpUi())
    val state: StateFlow<StepUpUi> = _state

    fun submit(selfiePath: String, onPass: () -> Unit) {
        _state.value = StepUpUi(loading = true)
        viewModelScope.launch {
            runCatching { auth.stepUp(selfiePath) }
                .onSuccess { r ->
                    _state.value = StepUpUi(verdict = r.verdict)
                    if (r.verdict == "PASS") onPass()
                }
                .onFailure { _state.value = StepUpUi(error = "Couldn't reach the server. Try again.") }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AuraApp
                StepUpViewModel(app.container.authRepository)
            }
        }
    }
}
