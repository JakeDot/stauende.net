package net.stauende.app

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// ---------------------------------------------------------------------------
// Data classes that map to the Google Directions API JSON response
// ---------------------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class DirectionsResponse(
    val geocodedWaypoints: List<GeocodedWaypoint> = emptyList(),
    val routes: List<Route> = emptyList(),
    val status: String = ""
)

@JsonClass(generateAdapter = true)
data class GeocodedWaypoint(
    val geocoderStatus: String = "",
    val placeId: String = ""
)

@JsonClass(generateAdapter = true)
data class Route(
    val summary: String = "",
    val legs: List<Leg> = emptyList(),
    val warnings: List<String> = emptyList()
)

@JsonClass(generateAdapter = true)
data class Leg(
    val distance: TextValue = TextValue(),
    val duration: TextValue = TextValue(),
    @Json(name = "duration_in_traffic") val durationInTraffic: TextValue = TextValue(),
    @Json(name = "start_address") val startAddress: String = "",
    @Json(name = "end_address") val endAddress: String = "",
    val steps: List<Step> = emptyList()
)

@JsonClass(generateAdapter = true)
data class Step(
    val distance: TextValue = TextValue(),
    val duration: TextValue = TextValue(),
    @Json(name = "html_instructions") val htmlInstructions: String = "",
    @Json(name = "travel_mode") val travelMode: String = ""
)

@JsonClass(generateAdapter = true)
data class TextValue(
    val text: String = "",
    val value: Int = 0
)

// ---------------------------------------------------------------------------
// Domain model
// ---------------------------------------------------------------------------

/** A single calculated route option with traffic-aware metadata. */
data class RouteOption(
    val summary: String,
    val distanceMeters: Int,
    /** Travel time without traffic (seconds). */
    val durationSeconds: Int,
    /** Travel time with current traffic (seconds). */
    val durationInTrafficSeconds: Int,
    val startAddress: String,
    val endAddress: String,
    val steps: List<Step>,
    val warnings: List<String>
) {
    /** Extra delay caused by traffic compared to free-flow travel time. */
    val trafficDelaySeconds: Int
        get() = maxOf(0, durationInTrafficSeconds - durationSeconds)

    /** True when traffic adds more than 20 % to the free-flow travel time. */
    val hasSignificantTraffic: Boolean
        get() = durationSeconds > 0 && trafficDelaySeconds.toDouble() / durationSeconds > 0.20
}

/** Suggestion for an optimal departure window to avoid a known jam. */
data class DepartureWindow(
    val departureHour: Int,
    val departureMinute: Int,
    val estimatedDurationSeconds: Int,
    val trafficLevel: TrafficLevel
) {
    val label: String
        get() = "%02d:%02d".format(departureHour, departureMinute)
}

enum class TrafficLevel { LOW, MODERATE, HIGH, SEVERE }
