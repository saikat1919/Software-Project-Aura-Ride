package com.auraride.app.ui.vm

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auraride.app.AuraApp
import com.auraride.app.data.RideRepository
import com.auraride.app.net.Coord
import com.auraride.app.net.EstimateResponse
import com.auraride.app.net.RideDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class RideUiState(
    val estimate: EstimateResponse? = null,
    val ride: RideDto? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/** Passenger ride flow against the live backend. Request is biometric-signed (§17.1);
 *  tracking polls GET /rides/{id} on a timer (no WebSocket, §17.3). */
class PassengerRideViewModel(private val repo: RideRepository) : ViewModel() {
    private val _state = MutableStateFlow(RideUiState())
    val state: StateFlow<RideUiState> = _state
    private var pollJob: Job? = null

    fun estimate(pickup: Coord, dropoff: Coord) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(loading = false, estimate = repo.estimate(pickup, dropoff))
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = serverError(e))
            }
        }
    }

    fun request(activity: FragmentActivity, pickup: Coord, dropoff: Coord, method: String, onRequested: (Long) -> Unit) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val ride = repo.requestRide(activity, pickup, dropoff, method)
                _state.value = _state.value.copy(loading = false, ride = ride)
                onRequested(ride.id)
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = biometricError(e))
            }
        }
    }

    /** Poll the ride until a terminal state; calls onCompleted with the paymentMethod on COMPLETED. */
    fun startPolling(rideId: Long, onCompleted: (String) -> Unit) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val ride = repo.pollRide(rideId)
                    _state.value = _state.value.copy(ride = ride)
                    if (ride.status == "COMPLETED") { onCompleted(ride.paymentMethod); break }
                    if (ride.status == "CANCELLED") break
                } catch (_: Exception) { /* transient — keep polling */ }
                delay(3000)
            }
        }
    }

    fun stopPolling() { pollJob?.cancel() }

    /** Clear estimate/ride/error so a new request starts fresh (the VM is shared). */
    fun reset() {
        pollJob?.cancel()
        _state.value = RideUiState()
    }

    fun cancel(rideId: Long, onDone: () -> Unit) {
        // Best-effort: a 409 just means the ride already ended (e.g. auto-cancelled with
        // no driver). Either way the user wants to leave, so always navigate back.
        viewModelScope.launch {
            runCatching { repo.cancelRide(rideId) }
            onDone()
        }
    }

    fun payBkash(rideId: Long, onPaid: () -> Unit) {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            try { repo.payBkash(rideId); _state.value = _state.value.copy(loading = false); onPaid() }
            catch (e: Exception) { _state.value = _state.value.copy(loading = false, error = serverError(e)) }
        }
    }

    override fun onCleared() { pollJob?.cancel() }

    private fun serverError(e: Exception) =
        "Couldn't reach the server. Is the backend running and a driver online?"

    private fun biometricError(e: Exception) = when {
        e.message?.contains("401") == true -> "Signature rejected — try the fingerprint again."
        e.message?.contains("no_enrolled_key") == true -> "No device key. Re-register on this phone."
        else -> "Couldn't request the ride. ${e.message ?: ""}".trim()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AuraApp
                PassengerRideViewModel(app.container.rideRepository)
            }
        }
    }
}
