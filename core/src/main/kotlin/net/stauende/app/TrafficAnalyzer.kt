package net.stauende.app

import java.time.DayOfWeek

/**
 * Analyses a history of [DepartureWindow] observations sampled over multiple
 * days and surfaces recurring traffic-jam windows so the user can plan their
 * commute around them.
 *
 * Typical usage:
 * ```
 * val analyzer = TrafficAnalyzer()
 * // Feed historical samples (e.g. loaded from a local DB or a JSON file).
 * historicalWindows.forEach { analyzer.record(it) }
 * // Ask for the worst slots on a Monday.
 * val jams = analyzer.commonJamWindows(DayOfWeek.MONDAY, topN = 3)
 * ```
 */
class TrafficAnalyzer {

    // key = (dayOfWeek, slotKey) where slotKey = HH:MM rounded to 30-min bucket
    private val observations: MutableMap<String, MutableList<Int>> = mutableMapOf()

    /**
     * Records the [durationInTrafficSeconds] observed for a departure at
     * [departureHour]:[departureMinute] on [day].
     *
     * Time is rounded down to the nearest 30-minute bucket so that adjacent
     * samples aggregate naturally.
     */
    fun record(
        day: DayOfWeek,
        departureHour: Int,
        departureMinute: Int,
        durationInTrafficSeconds: Int,
        durationWithoutTrafficSeconds: Int
    ) {
        require(departureHour in 0..23) { "departureHour must be 0-23" }
        require(departureMinute in 0..59) { "departureMinute must be 0-59" }
        require(durationInTrafficSeconds >= 0) { "durationInTrafficSeconds must be >= 0" }
        require(durationWithoutTrafficSeconds > 0) { "durationWithoutTrafficSeconds must be > 0" }

        val bucket = bucketMinute(departureMinute)
        val key = buildKey(day, departureHour, bucket)
        val delayRatio = durationInTrafficSeconds.toDouble() / durationWithoutTrafficSeconds
        // Store the delay ratio multiplied by 1000 as an integer to avoid
        // floating-point serialisation issues while keeping precision.
        observations.getOrPut(key) { mutableListOf() }
            .add((delayRatio * 1000).toInt())
    }

    /**
     * Convenience overload that accepts a [DepartureWindow] directly.
     *
     * [day] is required because [DepartureWindow] does not carry a date.
     */
    fun record(
        day: DayOfWeek,
        window: DepartureWindow,
        durationWithoutTrafficSeconds: Int
    ) = record(
        day = day,
        departureHour = window.departureHour,
        departureMinute = window.departureMinute,
        durationInTrafficSeconds = window.estimatedDurationSeconds,
        durationWithoutTrafficSeconds = durationWithoutTrafficSeconds
    )

    /**
     * Returns the [topN] 30-minute departure buckets on [day] that historically
     * have the worst (highest) average traffic-delay ratio, sorted from worst
     * to best.
     */
    fun commonJamWindows(day: DayOfWeek, topN: Int = 5): List<JamWindow> {
        require(topN > 0) { "topN must be > 0" }

        return observations
            .filter { (k, _) -> k.startsWith(day.name) }
            .map { (key, ratios) ->
                val avgRatio = ratios.average() / 1000.0
                val (hour, minute) = parseHourMinute(key)
                JamWindow(
                    day = day,
                    hour = hour,
                    minute = minute,
                    averageDelayRatio = avgRatio,
                    sampleCount = ratios.size,
                    trafficLevel = ratioToLevel(avgRatio)
                )
            }
            .sortedByDescending { it.averageDelayRatio }
            .take(topN)
    }

    /**
     * Returns the [topN] departure windows on [day] that are expected to have
     * the least traffic (lowest average delay ratio), i.e. the best times to leave.
     */
    fun bestDepartureWindows(day: DayOfWeek, topN: Int = 5): List<JamWindow> {
        require(topN > 0) { "topN must be > 0" }

        return observations
            .filter { (k, _) -> k.startsWith(day.name) }
            .map { (key, ratios) ->
                val avgRatio = ratios.average() / 1000.0
                val (hour, minute) = parseHourMinute(key)
                JamWindow(
                    day = day,
                    hour = hour,
                    minute = minute,
                    averageDelayRatio = avgRatio,
                    sampleCount = ratios.size,
                    trafficLevel = ratioToLevel(avgRatio)
                )
            }
            .filter { it.trafficLevel != TrafficLevel.SEVERE }
            .sortedBy { it.averageDelayRatio }
            .take(topN)
    }

    /** Clears all recorded observations. */
    fun reset() = observations.clear()

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private fun bucketMinute(minute: Int): Int = if (minute < 30) 0 else 30

    private fun buildKey(day: DayOfWeek, hour: Int, bucketMinute: Int): String =
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

/** Summary of a historical traffic-jam window. */
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
