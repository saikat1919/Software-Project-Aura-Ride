package com.auraride.app.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire DTOs — field names match PROJECT_SPEC §12 (snake_case via @SerialName).

@Serializable
data class Coord(val lat: Double, val lng: Double, val address: String = "")

@Serializable
data class TokenResponse(
    val status: String? = null,
    val access: String,
    val refresh: String? = null,
)

@Serializable
data class RegisterResponse(
    @SerialName("user_id") val userId: Long,
    val status: String,
    val access: String,
    val refresh: String? = null,
)

@Serializable
data class MeResponse(
    val id: Long,
    @SerialName("full_name") val fullName: String,
    val email: String,
    val role: String,
    val status: String,
)

@Serializable
data class VerificationStatusResponse(
    val status: String,
    @SerialName("account_status") val accountStatus: String? = null,
    val reason: String? = null,
    @SerialName("face_match_verdict") val faceMatchVerdict: String? = null,
)

@Serializable
data class ChallengeResponse(
    val nonce: String,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
data class EstimateRequest(val pickup: Coord, val dropoff: Coord)

@Serializable
data class EstimateResponse(
    @SerialName("estimated_fare") val estimatedFare: Int,
    val distance: Double,
    val duration: Int,
)

@Serializable
data class RideRequestBody(
    val pickup: Coord,
    val dropoff: Coord,
    @SerialName("payment_method") val paymentMethod: String,
    val nonce: String,
    val signature: String,
)

@Serializable
data class DriverDto(
    val name: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val vehicle: String = "",
    val plate: String = "",
)

@Serializable
data class LocationDto(val lat: Double = 0.0, val lng: Double = 0.0, val address: String = "")

@Serializable
data class RideDto(
    val id: Long,
    val status: String,
    val passenger: String = "",
    val pickup: LocationDto? = null,
    val dropoff: LocationDto? = null,
    @SerialName("estimated_fare") val estimatedFare: Int = 0,
    @SerialName("final_fare") val finalFare: Int? = null,
    @SerialName("payment_method") val paymentMethod: String = "BKASH",
    val driver: DriverDto? = null,
)

@Serializable
data class ActionBody(val nonce: String, val signature: String)

@Serializable
data class StatusResponse(val status: String? = null)

@Serializable
data class StepUpResponse(
    val verdict: String = "REVIEW",
    val status: String = "",
    @SerialName("face_match_score") val faceMatchScore: Double? = null,
)

@Serializable
data class DriverSummaryDto(
    @SerialName("today_earnings") val todayEarnings: Int = 0,
    @SerialName("total_earnings") val totalEarnings: Int = 0,
    @SerialName("completed_trips") val completedTrips: Int = 0,
    @SerialName("is_online") val isOnline: Boolean = false,
)
