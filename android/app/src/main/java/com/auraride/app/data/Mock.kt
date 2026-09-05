package com.auraride.app.data

// Mock models + data for the frontend prototype. NO backend calls yet.
// ponytail: these mirror the shapes the real Retrofit layer will return
// (PROJECT_SPEC §12). Swap this object for a repository backed by the API later.

enum class Role { PASSENGER, DRIVER }

enum class PaymentMethod(val label: String) { BKASH("bKash"), CASH("Cash") }

data class Trip(
    val destination: String,
    val fareBdt: Int,
    val status: String,
)

data class MatchedDriver(
    val name: String,
    val vehicle: String,
    val plate: String,
    val etaMin: Int,
    val avatar: String, // emoji stand-in for a real photo
)

data class RideEstimate(
    val distanceKm: Double,
    val durationMin: Int,
    val fareBdt: Int,
)

data class DriverOffer(
    val passengerName: String,
    val pickup: String,
    val dropoff: String,
    val fareBdt: Int,
    val pickupEtaMin: Int,
)

data class Earnings(
    val todayBdt: Int,
    val totalBdt: Int,
    val completedTrips: Int,
)

/** Liveness challenge is randomized per session (anti-replay, §7.1). */
enum class LivenessStep(val prompt: String, val hint: String) {
    BLINK("Blink slowly", "Keep your face inside the circle"),
    TURN_LEFT("Turn left", "Slowly, then hold"),
    TURN_RIGHT("Turn right", "Slowly, then hold"),
}

object Mock {
    const val passengerName = "Nusrat"
    const val driverName = "Farhana"

    val recentTrips = listOf(
        Trip("Dhanmondi 27", 180, "Completed"),
        Trip("Banani 11", 240, "Completed"),
    )

    val matchedDriver = MatchedDriver(
        name = "Farhana",
        vehicle = "Toyota Axio",
        plate = "DHA-14-5521",
        etaMin = 3,
        avatar = "👩‍✈️",
    )

    val estimate = RideEstimate(distanceKm = 4.2, durationMin = 14, fareBdt = 190)

    val incomingOffer = DriverOffer(
        passengerName = "Nusrat",
        pickup = "House 12, Road 5, Dhanmondi",
        dropoff = "Banani 11",
        fareBdt = 240,
        pickupEtaMin = 4,
    )

    val earnings = Earnings(todayBdt = 1240, totalBdt = 18650, completedTrips = 96)

    /** A shuffled challenge sequence, as the real liveness state machine will produce. */
    fun randomizedLiveness(): List<LivenessStep> = LivenessStep.entries.shuffled()
}
