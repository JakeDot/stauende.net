package net.stauende.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek

class TrafficAnalyzerTest {

    private lateinit var analyzer: TrafficAnalyzer

    private val commute = "Wien -> Linz"
    private val airportRun = "Wien -> Flughafen"

    @Before
    fun setUp() {
        analyzer = TrafficAnalyzer()
    }

    @Test
    fun `record and commonJamWindows returns correct worst windows`() {
        // Monday 08:00 — heavy jam (+80 %)
        repeat(5) {
            analyzer.record(commute, DayOfWeek.MONDAY, 8, 0, 1800, 1000)
        }
        // Monday 12:00 — mild (+10 %)
        repeat(5) {
            analyzer.record(commute, DayOfWeek.MONDAY, 12, 0, 1100, 1000)
        }
        // Monday 17:30 — moderate jam (+40 %)
        repeat(5) {
            analyzer.record(commute, DayOfWeek.MONDAY, 17, 30, 1400, 1000)
        }

        val jams = analyzer.commonJamWindows(commute, DayOfWeek.MONDAY, topN = 3)
        assertEquals(3, jams.size)
        // Worst jam should be at 08:00
        assertEquals(8, jams.first().hour)
        assertEquals(0, jams.first().minute)
        assertEquals(TrafficLevel.SEVERE, jams.first().trafficLevel)
    }

    @Test
    fun `commonJamWindows returns only results for requested day`() {
        analyzer.record(commute, DayOfWeek.MONDAY, 8, 0, 1800, 1000)
        analyzer.record(commute, DayOfWeek.TUESDAY, 8, 0, 1800, 1000)

        val mondayJams = analyzer.commonJamWindows(commute, DayOfWeek.MONDAY)
        val tuesdayJams = analyzer.commonJamWindows(commute, DayOfWeek.TUESDAY)

        assertEquals(1, mondayJams.size)
        assertEquals(DayOfWeek.MONDAY, mondayJams.first().day)
        assertEquals(1, tuesdayJams.size)
        assertEquals(DayOfWeek.TUESDAY, tuesdayJams.first().day)
    }

    @Test
    fun `observations for different routes never mix`() {
        // Same day, same time bucket, wildly different traffic on two unrelated routes.
        analyzer.record(commute, DayOfWeek.MONDAY, 8, 0, 2000, 1000)      // +100 % SEVERE
        analyzer.record(airportRun, DayOfWeek.MONDAY, 8, 0, 1020, 1000)   // +2 % LOW

        val commuteJams = analyzer.commonJamWindows(commute, DayOfWeek.MONDAY)
        val airportJams = analyzer.commonJamWindows(airportRun, DayOfWeek.MONDAY)

        assertEquals(1, commuteJams.size)
        assertEquals(TrafficLevel.SEVERE, commuteJams.first().trafficLevel)
        assertEquals(1, airportJams.size)
        assertEquals(TrafficLevel.LOW, airportJams.first().trafficLevel)
    }

    @Test
    fun `routeId matching ignores case and surrounding whitespace`() {
        analyzer.record("  Wien -> Linz  ", DayOfWeek.MONDAY, 8, 0, 1800, 1000)
        val jams = analyzer.commonJamWindows("wien -> linz", DayOfWeek.MONDAY)
        assertEquals(1, jams.size)
    }

    @Test
    fun `unknown route returns no windows`() {
        analyzer.record(commute, DayOfWeek.MONDAY, 8, 0, 1800, 1000)
        assertTrue(analyzer.commonJamWindows("Graz -> Salzburg", DayOfWeek.MONDAY).isEmpty())
    }

    @Test
    fun `bestDepartureWindows returns windows with lowest delay first`() {
        analyzer.record(commute, DayOfWeek.WEDNESDAY, 8, 0, 2000, 1000)   // +100 % SEVERE
        analyzer.record(commute, DayOfWeek.WEDNESDAY, 10, 0, 1050, 1000)  // +5 % LOW
        analyzer.record(commute, DayOfWeek.WEDNESDAY, 13, 0, 1300, 1000)  // +30 % HIGH

        val best = analyzer.bestDepartureWindows(commute, DayOfWeek.WEDNESDAY, topN = 2)
        // SEVERE window must be excluded; LOW must come first
        assertEquals(2, best.size)
        assertEquals(10, best.first().hour)
        assertEquals(TrafficLevel.LOW, best.first().trafficLevel)
    }

    @Test
    fun `minutes are bucketed into 30-min slots`() {
        analyzer.record(commute, DayOfWeek.FRIDAY, 8, 5, 1500, 1000)
        analyzer.record(commute, DayOfWeek.FRIDAY, 8, 25, 1600, 1000)
        // Both should fall into the 08:00 bucket
        analyzer.record(commute, DayOfWeek.FRIDAY, 8, 35, 1200, 1000)
        analyzer.record(commute, DayOfWeek.FRIDAY, 8, 50, 1200, 1000)
        // Both should fall into the 08:30 bucket

        val jams = analyzer.commonJamWindows(commute, DayOfWeek.FRIDAY, topN = 5)
        assertEquals(2, jams.size) // two distinct buckets: 08:00 and 08:30
        val hours = jams.map { it.hour }.distinct()
        assertEquals(1, hours.size)
        assertEquals(8, hours.first())
        val minutes = jams.map { it.minute }.toSet()
        assertTrue(0 in minutes)
        assertTrue(30 in minutes)
    }

    @Test
    fun `sample count is accumulated correctly across multiple records`() {
        repeat(7) { analyzer.record(commute, DayOfWeek.THURSDAY, 9, 0, 1200, 1000) }

        val jams = analyzer.commonJamWindows(commute, DayOfWeek.THURSDAY)
        assertEquals(7, jams.first().sampleCount)
    }

    @Test
    fun `reset clears all observations for every route`() {
        analyzer.record(commute, DayOfWeek.MONDAY, 8, 0, 1800, 1000)
        analyzer.record(airportRun, DayOfWeek.MONDAY, 8, 0, 1800, 1000)
        analyzer.reset()
        assertTrue(analyzer.commonJamWindows(commute, DayOfWeek.MONDAY).isEmpty())
        assertTrue(analyzer.commonJamWindows(airportRun, DayOfWeek.MONDAY).isEmpty())
    }

    @Test
    fun `snapshot and restore round-trip preserves observations`() {
        repeat(3) { analyzer.record(commute, DayOfWeek.MONDAY, 8, 0, 1800, 1000) }
        analyzer.record(airportRun, DayOfWeek.TUESDAY, 6, 30, 1100, 1000)

        val snapshot = analyzer.snapshot()

        val restored = TrafficAnalyzer()
        restored.restore(snapshot)

        val commuteJams = restored.commonJamWindows(commute, DayOfWeek.MONDAY)
        assertEquals(1, commuteJams.size)
        assertEquals(3, commuteJams.first().sampleCount)
        assertEquals(TrafficLevel.SEVERE, commuteJams.first().trafficLevel)

        val airportJams = restored.commonJamWindows(airportRun, DayOfWeek.TUESDAY)
        assertEquals(1, airportJams.size)
        assertEquals(6, airportJams.first().hour)
        assertEquals(30, airportJams.first().minute)
    }

    @Test
    fun `restore replaces rather than merges existing observations`() {
        analyzer.record(commute, DayOfWeek.MONDAY, 8, 0, 1800, 1000)
        analyzer.restore(emptyMap())
        assertTrue(analyzer.commonJamWindows(commute, DayOfWeek.MONDAY).isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `record throws on blank routeId`() {
        analyzer.record("  ", DayOfWeek.MONDAY, 8, 0, 1000, 1000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `record throws on invalid hour`() {
        analyzer.record(commute, DayOfWeek.MONDAY, 25, 0, 1000, 1000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `record throws on invalid minute`() {
        analyzer.record(commute, DayOfWeek.MONDAY, 8, 61, 1000, 1000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `record throws when durationWithoutTraffic is zero`() {
        analyzer.record(commute, DayOfWeek.MONDAY, 8, 0, 1000, 0)
    }

    @Test
    fun `delayPercent is correctly computed`() {
        analyzer.record(commute, DayOfWeek.MONDAY, 7, 0, 1350, 1000)
        val jam = analyzer.commonJamWindows(commute, DayOfWeek.MONDAY).first()
        assertEquals(35, jam.delayPercent)
    }
}
