package net.stauende.app

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/**
 * Fetches route options from the Google Directions API and enriches them with
 * traffic-aware metadata so the user can pick the optimal departure time.
 *
 * The API key is passed in from [BuildConfig.MAPS_API_KEY] and must never be
 * committed to source control — store it in `local.properties` as
 * `MAPS_API_KEY=<your-key>`.
 */
class RouteCalculator(
    private val apiKey: String,
    private val httpClient: OkHttpClient = defaultHttpClient()
) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val responseAdapter = moshi.adapter(DirectionsResponse::class.java)

    /**
     * Fetches up to [alternatives] route options with real-time traffic for
     * the given [origin] and [destination] at a [departureEpochSeconds].
     *
     * Pass `departureEpochSeconds = null` to use "now".
     *
     * @throws IOException if the network call fails.
     * @throws RouteCalculationException if the API returns a non-OK status.
     */
    @Throws(IOException::class, RouteCalculationException::class)
    fun calculateRoutes(
        origin: String,
        destination: String,
        departureEpochSeconds: Long? = null,
        alternatives: Boolean = true
    ): List<RouteOption> {
        require(origin.isNotBlank()) { "origin must not be blank" }
        require(destination.isNotBlank()) { "destination must not be blank" }

        val epoch = departureEpochSeconds ?: (System.currentTimeMillis() / 1000L)

        val url = DIRECTIONS_BASE_URL.toHttpUrl().newBuilder()
            .addQueryParameter("origin", origin)
            .addQueryParameter("destination", destination)
            .addQueryParameter("alternatives", alternatives.toString())
            .addQueryParameter("departure_time", epoch.toString())
            .addQueryParameter("traffic_model", "best_guess")
            .addQueryParameter("key", apiKey)
            .build()

        val request = Request.Builder().url(url).get().build()
        val bodyString = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            response.body?.string() ?: throw IOException("Empty response body")
        }

        val directions = responseAdapter.fromJson(bodyString)
            ?: throw RouteCalculationException("Failed to parse Directions API response")

        if (directions.status != "OK") {
            throw RouteCalculationException("Directions API error: ${directions.status}")
        }

        return directions.routes.map { route ->
            val leg = route.legs.firstOrNull() ?: return@map null
            RouteOption(
                summary = route.summary,
                distanceMeters = leg.distance.value,
                durationSeconds = leg.duration.value,
                durationInTrafficSeconds = leg.durationInTraffic.value
                    .takeIf { it > 0 } ?: leg.duration.value,
                startAddress = leg.startAddress,
                endAddress = leg.endAddress,
                steps = leg.steps,
                warnings = route.warnings,
                startLat = leg.startLocation.lat,
                startLng = leg.startLocation.lng,
                endLat = leg.endLocation.lat,
                endLng = leg.endLocation.lng,
                polyline = PolylineDecoder.decode(route.overviewPolyline.points)
            )
        }.filterNotNull()
    }

    /**
     * Returns a sorted list of [DepartureWindow] candidates for the next
     * [hoursAhead] hours starting now, spaced [intervalMinutes] apart.
     *
     * Each window queries the Directions API with the corresponding future
     * departure time so we get the forecast traffic duration.
     */
    @Throws(IOException::class, RouteCalculationException::class)
    fun suggestDepartureWindows(
        origin: String,
        destination: String,
        hoursAhead: Int = 3,
        intervalMinutes: Int = 30
    ): List<DepartureWindow> {
        val nowEpoch = System.currentTimeMillis() / 1000L
        val windows = mutableListOf<DepartureWindow>()

        var offsetSeconds = 0L
        val totalSeconds = hoursAhead * 3600L

        while (offsetSeconds <= totalSeconds) {
            val departureEpoch = nowEpoch + offsetSeconds
            val dt = LocalDateTime.ofEpochSecond(departureEpoch, 0, ZoneOffset.UTC)

            val routes = calculateRoutes(
                origin = origin,
                destination = destination,
                departureEpochSeconds = departureEpoch,
                alternatives = false
            )
            val best = routes.minByOrNull { it.durationInTrafficSeconds }

            if (best != null) {
                windows.add(
                    DepartureWindow(
                        departureHour = dt.hour,
                        departureMinute = dt.minute,
                        estimatedDurationSeconds = best.durationInTrafficSeconds,
                        trafficLevel = classifyTraffic(best)
                    )
                )
            }

            offsetSeconds += intervalMinutes * 60L
        }

        return windows.sortedBy { it.estimatedDurationSeconds }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private fun classifyTraffic(route: RouteOption): TrafficLevel {
        if (route.durationSeconds == 0) return TrafficLevel.LOW
        val ratio = route.durationInTrafficSeconds.toDouble() / route.durationSeconds
        return when {
            ratio < 1.10 -> TrafficLevel.LOW
            ratio < 1.30 -> TrafficLevel.MODERATE
            ratio < 1.60 -> TrafficLevel.HIGH
            else -> TrafficLevel.SEVERE
        }
    }

    companion object {
        private const val DIRECTIONS_BASE_URL =
            "https://maps.googleapis.com/maps/api/directions/json"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

/** Thrown when the Directions API returns a non-OK status or an unexpected response. */
class RouteCalculationException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
