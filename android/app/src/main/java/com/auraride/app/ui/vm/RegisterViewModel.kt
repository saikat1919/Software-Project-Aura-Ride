package com.auraride.app.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auraride.app.AuraApp
import com.auraride.app.data.AuthRepository
import com.auraride.app.data.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class RegisterState(
    val loading: Boolean = false,
    val error: String? = null,
    val accountStatus: String? = null,   // PENDING_REVIEW / ACTIVE / REJECTED
    val reason: String? = null,          // admin's reject reason, if any
)

/**
 * Holds the registration draft across the onboarding screens and submits it at the
 * biometric-enroll step. Shared (activity-scoped) so every onboarding screen writes
 * into the same instance. Submit generates the device key and sends only the public
 * half (§17.1); Pending polls the real /me/verification-status (eng review Issue 1).
 */
class RegisterViewModel(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(RegisterState())
    val state: StateFlow<RegisterState> = _state

    // draft
    var email = ""; var phone = ""; var fullName = ""; var password = ""
    var role: Role = Role.PASSENGER
    var ocrName = ""; var ocrGender = ""; var ocrNid = ""
    var selfiePath = ""; var nidPath = ""   // captured on the liveness + NID screens
    // driver-only
    var licensePath = ""; var licenseName = ""; var licenseNumber = ""
    var vehMake = ""; var vehModel = ""; var vehPlate = ""; var vehColor = ""

    fun setDriverExtras(
        licensePath: String, licenseName: String, licenseNumber: String,
        make: String, model: String, plate: String, color: String,
    ) {
        this.licensePath = licensePath; this.licenseName = licenseName; this.licenseNumber = licenseNumber
        this.vehMake = make; this.vehModel = model; this.vehPlate = plate; this.vehColor = color
    }

    fun setInfo(fullName: String, phone: String, email: String, password: String, role: Role) {
        this.fullName = fullName; this.phone = phone; this.email = email
        this.password = password; this.role = role
    }

    fun setOcr(name: String, gender: String, nid: String) {
        ocrName = name; ocrGender = gender; ocrNid = nid
    }

    fun submit(onSubmitted: () -> Unit) {
        _state.value = RegisterState(loading = true)
        viewModelScope.launch {
            try {
                val status = auth.register(
                    email = email, phone = phone, fullName = fullName, password = password,
                    role = role.name, ocrName = ocrName, ocrGender = ocrGender, ocrNid = ocrNid,
                    selfiePath = selfiePath.ifBlank { null }, nidPath = nidPath.ifBlank { null },
                    licensePath = licensePath.ifBlank { null },
                    licenseName = licenseName, licenseNumber = licenseNumber,
                    vehMake = vehMake, vehModel = vehModel, vehPlate = vehPlate, vehColor = vehColor,
                )
                _state.value = RegisterState(accountStatus = status)
                onSubmitted()
            } catch (e: Exception) {
                _state.value = RegisterState(error = friendly(e))
            }
        }
    }

    /** Poll once; calls onActive() when the admin has approved (account ACTIVE). */
    fun refreshStatus(onActive: () -> Unit) {
        viewModelScope.launch {
            try {
                val s = auth.verificationStatus()
                val acct = s.accountStatus ?: s.status
                _state.value = _state.value.copy(accountStatus = acct, reason = s.reason, error = null)
                if (acct == "ACTIVE") {
                    // Swap the limited pending JWT (Issue 1) for a full-scope session so
                    // ride actions pass IsFullyVerified. Uses the draft credentials.
                    runCatching { auth.login(email, password) }
                    onActive()
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = friendly(e))
            }
        }
    }

    /** Resubmit fresh selfie + NID after a rejection; account returns to PENDING_REVIEW. */
    fun resubmit(onDone: () -> Unit) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val r = auth.resubmit(
                    selfiePath.ifBlank { null }, nidPath.ifBlank { null }, ocrName, ocrGender, ocrNid)
                _state.value = RegisterState(accountStatus = r.status ?: "PENDING_REVIEW")
                onDone()
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = friendly(e))
            }
        }
    }

    private fun friendly(e: Exception): String = when {
        e.message?.contains("409") == true -> "That email or phone is already registered."
        e.message?.contains("400") == true -> "Please fill in every field."
        else -> "Couldn't reach the server. Is the backend running and BASE_URL correct?"
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AuraApp
                RegisterViewModel(app.container.authRepository)
            }
        }
    }
}
