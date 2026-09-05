package com.auraride.app.net

import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * REST contract — PROJECT_SPEC §12. All live updates come from polling these GETs
 * on a timer (no WebSocket).
 *
 * ponytail: register here is form-encoded for the no-image demo path. Real
 * registration adds a @Multipart variant with @Part selfie/nid_front/license.
 */
interface ApiService {

    // --- auth & registration (§12.1) ---
    // Multipart so the selfie + NID images ride along with the fields. The repository
    // builds the MultipartBody (text parts + optional image parts).
    @POST("auth/register")
    suspend fun register(@Body body: okhttp3.MultipartBody): RegisterResponse

    @FormUrlEncoded
    @POST("auth/verify-phone")
    suspend fun verifyPhone(@Field("phone") phone: String, @Field("otp") otp: String): StatusResponse

    @FormUrlEncoded
    @POST("auth/login")
    suspend fun login(@Field("email") email: String, @Field("password") password: String): TokenResponse

    @FormUrlEncoded
    @POST("auth/refresh")
    suspend fun refresh(@Field("refresh") refresh: String): TokenResponse

    @FormUrlEncoded
    @POST("auth/challenge")
    suspend fun challenge(
        @Field("action_type") actionType: String,
        @Field("ride_id") rideId: Long? = null,
    ): ChallengeResponse

    // Multipart (selfie[/nid_front] + fields) — repository builds the body.
    @POST("auth/step-up")
    suspend fun stepUp(@Body body: okhttp3.MultipartBody): StepUpResponse

    @POST("auth/resubmit")
    suspend fun resubmit(@Body body: okhttp3.MultipartBody): StatusResponse

    // --- profile & status (§12.2) ---
    @GET("me")
    suspend fun me(): MeResponse

    @GET("me/verification-status")
    suspend fun verificationStatus(): VerificationStatusResponse

    // --- rides (§12.4) ---
    @POST("rides/estimate")
    suspend fun estimate(@Body body: EstimateRequest): EstimateResponse

    @POST("rides/request")
    suspend fun requestRide(@Body body: RideRequestBody): RideDto

    @GET("rides/{id}")
    suspend fun ride(@Path("id") id: Long): RideDto

    @POST("rides/{id}/cancel")
    suspend fun cancelRide(@Path("id") id: Long): StatusResponse

    // --- driver (§12.3) ---
    @FormUrlEncoded
    @POST("driver/online")
    suspend fun driverOnline(
        @Field("is_online") isOnline: Boolean,
        @Field("lat") lat: Double? = null,
        @Field("lng") lng: Double? = null,
    ): StatusResponse

    @FormUrlEncoded
    @POST("driver/location")
    suspend fun driverLocation(
        @Field("lat") lat: Double,
        @Field("lng") lng: Double,
    ): StatusResponse

    @FormUrlEncoded
    @POST("auth/enroll-key")
    suspend fun enrollKey(
        @Field("public_key") publicKey: String,
        @Field("device_id") deviceId: String = "",
    ): StatusResponse

    @GET("driver/offers")
    suspend fun driverOffers(): RideDto?

    @POST("driver/rides/{id}/accept")
    suspend fun acceptRide(@Path("id") id: Long, @Body body: ActionBody): RideDto

    @POST("driver/rides/{id}/arrive")
    suspend fun arrive(@Path("id") id: Long): StatusResponse

    @POST("driver/rides/{id}/start")
    suspend fun startTrip(@Path("id") id: Long): StatusResponse

    @POST("driver/rides/{id}/complete")
    suspend fun completeTrip(@Path("id") id: Long): StatusResponse

    @POST("driver/rides/{id}/cash-paid")
    suspend fun cashPaid(@Path("id") id: Long): StatusResponse

    @GET("driver/summary")
    suspend fun driverSummary(): DriverSummaryDto

    // --- payment (§12.5) ---
    @POST("payments/{id}/bkash")
    suspend fun payBkash(@Path("id") id: Long): StatusResponse
}
