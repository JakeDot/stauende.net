package net.stauende.app

import java.time.DayOfWeek

/**
 * Analyses a history of [DepartureWindow] observations, scoped per route,
 * sampled over multiple days and surfaces recurring traffic-jam windows so
 * the user can plan their commute around them.
 *
 * Typical usage:
 * ```
 * val analyzer = TrafficAnalyzer()
 * // Feed historical samples (e.g. loaded from a local DB or a JSON file).
 * historicalWindows.forEach { analyzer.record(routeId, it) }
 * // Ask for the worst slots on a Monday, for that specific route.
 * val jams = analyzer.commonJamWindows(routeId, DayOfWeek.MONDAY, topN = 3)
 * ```
 *
 * Observations from different routes are kept separate — a jam on the
 * commute to work must never bleed into the analysis for an unrelated trip
 * to the airport, even if both happen to be sampled at the same time of day.
 */
class TrafficAnalyzer {

    // routeId -> (dayOfWeek_HH_MM bucket key) -> observed delay ratios (x1000, as ints)
    private val observations: MutableMap<String, MutableMap<String, MutableList<Int>>> = mutableMapOf()

    /**
     * Records the [durationInTrafficSeconds] observed for [routeId] at a
     * departure of [departureHour]:[departureMinute] on [day].
     *
     * Time is rounded down to the nearest 30-minute bucket so that adjacent
     * samples aggregate naturally. [routeId] should identify the route
     * (e.g. `"$origin -> $destination"`) — it is matched case- and
     * whitespace-insensitively.
     */
    fun record(
        routeId: String,
        day: DayOfWeek,
        departureHour: Int,
        departureMinute: Int,
        durationInTrafficSeconds: Int,
        durationWithoutTrafficSeconds: Int
    ) {
        require(routeId.isNotBlank()) { "routeId must not be blank" }
        require(departureHour in 0..23) { "departureHour must be 0-23" }
        require(departureMinute in 0..59) { "departureMinute must be 0-59" }
        require(durationInTrafficSeconds >= 0) { "durationInTrafficSeconds must be >= 0" }
        require(durationWithoutTrafficSeconds > 0) { "durationWithoutTrafficSeconds must be > 0" }

        val bucket = bucketMinute(departureMinute)
        val timeKey = buildTimeKey(day, departureHour, bucket)
        val delayRatio = durationInTrafficSeconds.toDouble() / durationWithoutTrafficSeconds
        // Store the delay ratio multiplied by 1000 as an integer to avoid
        // floating-point serialisation issues while keeping precision.
        observations.getOrPut(normalizeRouteId(routeId)) { mutableMapOf() }
            .getOrPut(timeKey) { mutableListOf() }
            .add((delayRatio * 1000).toInt())
    }

    /**
     * Convenience overload that accepts a [DepartureWindow] directly.
     *
     * [day] is required because [DepartureWindow] does not carry a date.
     */
    fun record(
        routeId: String,
        day: DayOfWeek,
        window: DepartureWindow,
        durationWithoutTrafficSeconds: Int
    ) = record(
        routeId = routeId,
        day = day,
        departureHour = window.departureHour,
        departureMinute = window.departureMinute,
        durationInTrafficSeconds = window.estimatedDurationSeconds,
        durationWithoutTrafficSeconds = durationWithoutTrafficSeconds
    )

    /**
     * Returns the [topN] 30-minute departure buckets on [day] for [routeId]
     * that historically have the worst (highest) average traffic-delay
     * ratio, sorted from worst to best.
     */
    fun commonJamWindows(routeId: String, day: DayOfWeek, topN: Int = 5): List<JamWindow> {
        require(topN > 0) { "topN must be > 0" }

        return jamWindowsForRoute(routeId, day)
            .sortedByDescending { it.averageDelayRatio }
            .take(topN)
    }

    /**
     * Returns the [topN] departure windows on [day] for [routeId] that are
     * expected to have the least traffic (lowest average delay ratio), i.e.
     * the best times to leave.
     */
    fun bestDepartureWindows(routeId: String, day: DayOfWeek, topN: Int = 5): List<JamWindow> {
        require(topN > 0) { "topN must be > 0" }

        return jamWindowsForRoute(routeId, day)
            .filter { it.trafficLevel != TrafficLevel.SEVERE }
            .sortedBy { it.averageDelayRatio }
            .take(topN)
    }

    /** Clears all recorded observations, for every route. */
    fun reset() = observations.clear()

    /**
     * Exports all recorded observations as plain nested maps
     * (`routeId -> timeBucketKey -> delayRatios×1000`) suitable for
     * serialisation, e.g. to a JSON file for persistence across app
     * launches. See [restore] for the inverse operation.
     */
    fun snapshot(): Map<String, Map<String, List<Int>>> =
        observations.mapValues { (_, buckets) -> buckets.mapValues { it.value.toList() } }

    /**
     * Replaces all current observations with [data], as previously produced
     * by [snapshot]. Existing observations are discarded.
     */
    fun restore(data: Map<String, Map<String, List<Int>>>) {
        observations.clear()
        data.forEach { (routeId, buckets) ->
            observations[routeId] = buckets.mapValuesTo(mutableMapOf()) { it.value.toMutableList() }
        }
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private fun jamWindowsForRoute(routeId: String, day: DayOfWeek): List<JamWindow> {
        val routeObservations = observations[normalizeRouteId(routeId)] ?: return emptyList()
        return routeObservations
            .filter { (timeKey, _) -> timeKey.startsWith(day.name) }
            .map { (timeKey, ratios) ->
                val avgRatio = ratios.average() / 1000.0
                val (hour, minute) = parseHourMinute(timeKey)
                JamWindow(
                    day = day,
                    hour = hour,
                    minute = minute,
                    averageDelayRatio = avgRatio,
                    sampleCount = ratios.size,
                    trafficLevel = ratioToLevel(avgRatio)
                )
            }
    }

    private fun normalizeRouteId(routeId: String): String = routeId.trim().lowercase()

    private fun bucketMinute(minute: Int): Int = if (minute < 30) 0 else 30

    private fun buildTimeKey(day: DayOfWeek, hour: Int, bucketMinute: Int): String =
        "${day.name}_%02d_%02d".format(hour, bucketMinute)

    private fun parseHourMinute(key: String): Pair<Int, Int> {
        val parts = key.split("_")
        // key format: DAYOFWEEK_HH_MM  (e.g. "MONDAY_08_00")
        return Pair(parts[parts.size - 2].toInt(), parts[parts.size - 1].toInt())
    }

    private fun ratioToLevel(ratio: Double): TrafficLevel = when {
        ratio < 1.10 -> TrafficLevel.LOW
        ratio < 1.30 -> TrafficLevel.MODERATE
        ratio < 1.60 -> TrafficLevel.HIGH
        else -> TrafficLevel.SEVERE
    }
}

/** Summary of a historical traffic-jam window for a specific route. */
data class JamWindow(
    val day: DayOfWeek,
    val hour: Int,
    val minute: Int,
    val averageDelayRatio: Double,
    val sampleCount: Int,
    val trafficLevel: TrafficLevel
) {
    val label: String get() = "%02d:%02d".format(hour, minute)

    /** Extra travel time as a human-readable percentage, e.g. "+35 %". */
    val delayPercent: Int get() = ((averageDelayRatio - 1.0) * 100).toInt()
}
