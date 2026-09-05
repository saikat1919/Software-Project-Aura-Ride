package com.auraride.app.data

import androidx.fragment.app.FragmentActivity
import com.auraride.app.net.ActionBody
import com.auraride.app.net.ApiService
import com.auraride.app.net.Coord
import com.auraride.app.net.EstimateRequest
import com.auraride.app.net.EstimateResponse
import com.auraride.app.net.RideDto
import com.auraride.app.net.RideRequestBody
import com.auraride.app.security.BiometricKeyManager
import com.auraride.app.security.BiometricSigner

/**
 * Ride flows. Biometric-gated actions fetch a nonce, sign the canonical
 * "<ACTION>:<nonce>:<ride_id>" with BiometricPrompt (§17.1 / Issue 3), then call the API.
 * Actions that sign take a FragmentActivity so BiometricPrompt has a host.
 */
class RideRepository(private val api: ApiService) {

    private fun message(action: String, nonce: String, rideId: Long?): ByteArray =
        "$action:$nonce:${rideId ?: ""}".toByteArray(Charsets.UTF_8)

    /**
     * Sign a ride action, recovering from a lost/invalidated device key (§17.1). If the
     * Keystore has no usable key (app reinstall, or a fingerprint change destroyed it), we
     * generate a fresh key, re-enroll its public half with the server, and sign once more —
     * so the user isn't forced to re-register.
     */
    private suspend fun signAction(activity: FragmentActivity, message: ByteArray): String =
        try {
            BiometricSigner.signMessage(activity, message)
        } catch (e: BiometricKeyManager.NoDeviceKeyException) {
            BiometricKeyManager.generateKeyPair()
            api.enrollKey(BiometricKeyManager.publicKeyPem())
            BiometricSigner.signMessage(activity, message)
        }

    suspend fun estimate(pickup: Coord, dropoff: Coord): EstimateResponse =
        api.estimate(EstimateRequest(pickup, dropoff))

    suspend fun requestRide(
        activity: FragmentActivity, pickup: Coord, dropoff: Coord, paymentMethod: String,
    ): RideDto {
        val nonce = api.challenge("REQUEST_RIDE").nonce
        val sig = signAction(activity, message("REQUEST_RIDE", nonce, null))
        return api.requestRide(RideRequestBody(pickup, dropoff, paymentMethod, nonce, sig))
    }

    suspend fun pollRide(id: Long): RideDto = api.ride(id)

    suspend fun cancelRide(id: Long) = api.cancelRide(id)

    // driver
    suspend fun goOnline(online: Boolean, lat: Double?, lng: Double?) = api.driverOnline(online, lat, lng)

    suspend fun postLocation(lat: Double, lng: Double) = api.driverLocation(lat, lng)

    suspend fun offers(): RideDto? = api.driverOffers()

    suspend fun acceptRide(activity: FragmentActivity, rideId: Long): RideDto {
        val nonce = api.challenge("ACCEPT_RIDE", rideId).nonce
        val sig = signAction(activity, message("ACCEPT_RIDE", nonce, rideId))
        return api.acceptRide(rideId, ActionBody(nonce, sig))
    }

    suspend fun arrive(id: Long) = api.arrive(id)
    suspend fun startTrip(id: Long) = api.startTrip(id)
    suspend fun completeTrip(id: Long) = api.completeTrip(id)
    suspend fun cashPaid(id: Long) = api.cashPaid(id)
    suspend fun payBkash(id: Long) = api.payBkash(id)
    suspend fun driverSummary() = api.driverSummary()
}
