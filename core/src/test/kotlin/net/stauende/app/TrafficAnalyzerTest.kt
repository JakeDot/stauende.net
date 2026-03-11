package net.stauende.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek

class TrafficAnalyzerTest {

    private lateinit var analyzer: TrafficAnalyzer

    @Before
    fun setUp() {
        analyzer = TrafficAnalyzer()
    }

    @Test
    fun `record and commonJamWindows returns correct worst windows`() {
        // Monday 08:00 — heavy jam (+80 %)
        repeat(5) {
            analyzer.record(DayOfWeek.MONDAY, 8, 0, 1800, 1000)
        }
        // Monday 12:00 — mild (+10 %)
        repeat(5) {
            analyzer.record(DayOfWeek.MONDAY, 12, 0, 1100, 1000)
        }
        // Monday 17:30 — moderate jam (+40 %)
        repeat(5) {
            analyzer.record(DayOfWeek.MONDAY, 17, 30, 1400, 1000)
        }

        val jams = analyzer.commonJamWindows(DayOfWeek.MONDAY, topN = 3)
        assertEquals(3, jams.size)
        // Worst jam should be at 08:00
        assertEquals(8, jams.first().hour)
        assertEquals(0, jams.first().minute)
        assertEquals(TrafficLevel.SEVERE, jams.first().trafficLevel)
    }

    @Test
    fun `commonJamWindows returns only results for requested day`() {
        analyzer.record(DayOfWeek.MONDAY, 8, 0, 1800, 1000)
        analyzer.record(DayOfWeek.TUESDAY, 8, 0, 1800, 1000)

        val mondayJams = analyzer.commonJamWindows(DayOfWeek.MONDAY)
        val tuesdayJams = analyzer.commonJamWindows(DayOfWeek.TUESDAY)

        assertEquals(1, mondayJams.size)
        assertEquals(DayOfWeek.MONDAY, mondayJams.first().day)
        assertEquals(1, tuesdayJams.size)
        assertEquals(DayOfWeek.TUESDAY, tuesdayJams.first().day)
    }

    @Test
    fun `bestDepartureWindows returns windows with lowest delay first`() {
        analyzer.record(DayOfWeek.WEDNESDAY, 8, 0, 2000, 1000)   // +100 % SEVERE
        analyzer.record(DayOfWeek.WEDNESDAY, 10, 0, 1050, 1000)  // +5 % LOW
        analyzer.record(DayOfWeek.WEDNESDAY, 13, 0, 1300, 1000)  // +30 % HIGH

        val best = analyzer.bestDepartureWindows(DayOfWeek.WEDNESDAY, topN = 2)
        // SEVERE window must be excluded; LOW must come first
        assertEquals(2, best.size)
        assertEquals(10, best.first().hour)
        assertEquals(TrafficLevel.LOW, best.first().trafficLevel)
    }

    @Test
    fun `minutes are bucketed into 30-min slots`() {
        analyzer.record(DayOfWeek.FRIDAY, 8, 5, 1500, 1000)
        analyzer.record(DayOfWeek.FRIDAY, 8, 25, 1600, 1000)
        // Both should fall into the 08:00 bucket
        analyzer.record(DayOfWeek.FRIDAY, 8, 35, 1200, 1000)
        analyzer.record(DayOfWeek.FRIDAY, 8, 50, 1200, 1000)
        // Both should fall into the 08:30 bucket

        val jams = analyzer.commonJamWindows(DayOfWeek.FRIDAY, topN = 5)
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
        repeat(7) { analyzer.record(DayOfWeek.THURSDAY, 9, 0, 1200, 1000) }

        val jams = analyzer.commonJamWindows(DayOfWeek.THURSDAY)
        assertEquals(7, jams.first().sampleCount)
    }

    @Test
    fun `reset clears all observations`() {
        analyzer.record(DayOfWeek.MONDAY, 8, 0, 1800, 1000)
        analyzer.reset()
        val jams = analyzer.commonJamWindows(DayOfWeek.MONDAY)
        assertTrue(jams.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `record throws on invalid hour`() {
        analyzer.record(DayOfWeek.MONDAY, 25, 0, 1000, 1000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `record throws on invalid minute`() {
        analyzer.record(DayOfWeek.MONDAY, 8, 61, 1000, 1000)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `record throws when durationWithoutTraffic is zero`() {
        analyzer.record(DayOfWeek.MONDAY, 8, 0, 1000, 0)
    }

    @Test
    fun `delayPercent is correctly computed`() {
        analyzer.record(DayOfWeek.MONDAY, 7, 0, 1350, 1000)
        val jam = analyzer.commonJamWindows(DayOfWeek.MONDAY).first()
        assertEquals(35, jam.delayPercent)
    }
}
