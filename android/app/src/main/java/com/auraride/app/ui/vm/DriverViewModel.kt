package com.auraride.app.ui.vm

import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.auraride.app.AuraApp
import com.auraride.app.data.RideRepository
import com.auraride.app.net.DriverSummaryDto
import com.auraride.app.net.RideDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Demo driver location — near the passenger's DEMO_PICKUP so matching succeeds.
private const val DEMO_LAT = 23.7520
private const val DEMO_LNG = 90.3900

data class DriverUiState(
    val online: Boolean = false,
    val offer: RideDto? = null,
    val activeRide: RideDto? = null,
    val summary: DriverSummaryDto? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/** Driver flow. Offers arrive by polling GET /driver/offers (no push, §17.9); accept is
 *  biometric-signed and bound to the ride (§17.1 / Issue 3). */
class DriverViewModel(private val repo: RideRepository) : ViewModel() {
    private val _state = MutableStateFlow(DriverUiState())
    val state: StateFlow<DriverUiState> = _state
    private var offerJob: Job? = null

    fun toggleOnline(on: Boolean, lat: Double? = null, lng: Double? = null) {
        viewModelScope.launch {
            try {
                // Use the driver's real GPS if available, else fall back to the demo location
                // so a passenger's real pickup and the driver land within the match radius.
                repo.goOnline(on, if (on) (lat ?: DEMO_LAT) else null, if (on) (lng ?: DEMO_LNG) else null)
                _state.value = _state.value.copy(online = on, error = null)
                if (on) startOfferPolling() else { offerJob?.cancel(); _state.value = _state.value.copy(offer = null) }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = serverError())
            }
        }
    }

    /** Restart offer polling when the driver returns to Home after a ride (the poll loop
     *  breaks once an offer is found, so it must be revived). No-op if offline or already polling. */
    fun resumeOfferPolling() {
        if (_state.value.online && offerJob?.isActive != true) startOfferPolling()
    }

    private fun startOfferPolling() {
        offerJob?.cancel()
        offerJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val offer = repo.offers()
                    if (offer != null) { _state.value = _state.value.copy(offer = offer); break }
                } catch (_: Exception) { /* transient */ }
                delay(3000)
            }
        }
    }

    fun declineOffer() {
        // Server offer expires and advances to the next driver (Issue 2); clear locally + resume polling.
        _state.value = _state.value.copy(offer = null)
        if (_state.value.online) startOfferPolling()
    }

    fun accept(activity: FragmentActivity, rideId: Long, onAccepted: () -> Unit) {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val ride = repo.acceptRide(activity, rideId)
                _state.value = _state.value.copy(loading = false, activeRide = ride, offer = null)
                onAccepted()
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = acceptError(e))
            }
        }
    }

    fun arrive(id: Long) = transition { repo.arrive(id); refreshActive(id) }
    fun start(id: Long) = transition { repo.startTrip(id); refreshActive(id) }
    fun complete(id: Long) = transition { repo.completeTrip(id); refreshActive(id) }
    fun cashPaid(id: Long, onDone: () -> Unit) = transition { repo.cashPaid(id); onDone() }

    /** Push the driver's live GPS during a ride so the passenger's map tracks her (§8.3).
     *  Best-effort: a dropped location update just means one stale tick. */
    fun pushLocation(lat: Double, lng: Double) {
        viewModelScope.launch { runCatching { repo.postLocation(lat, lng) } }
    }

    private suspend fun refreshActive(id: Long) {
        // Re-read so the UI shows the new status (complete also sets final_fare).
        try { _state.value = _state.value.copy(activeRide = repo.pollRide(id)) } catch (_: Exception) {}
    }

    private fun transition(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { _state.value = _state.value.copy(error = null); block() }
            catch (e: Exception) { _state.value = _state.value.copy(error = transitionError(e)) }
        }
    }

    private fun transitionError(e: Exception) = when {
        // 409 = too_far (not at pickup/dropoff) or bad step order.
        e.message?.contains("409") == true ->
            "Can't do that yet — make sure you're at the pickup/dropoff and following the trip steps."
        else -> serverError()
    }

    fun loadSummary() {
        viewModelScope.launch {
            try { _state.value = _state.value.copy(summary = repo.driverSummary()) } catch (_: Exception) {}
        }
    }

    override fun onCleared() { offerJob?.cancel() }

    private fun serverError() = "Couldn't reach the server. Is the backend running?"
    private fun acceptError(e: Exception) = when {
        e.message?.contains("409") == true -> "Offer expired — another driver took it."
        e.message?.contains("401") == true -> "Signature rejected — try the fingerprint again."
        else -> "Couldn't accept. ${e.message ?: ""}".trim()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AuraApp
                DriverViewModel(app.container.rideRepository)
            }
        }
    }
}
