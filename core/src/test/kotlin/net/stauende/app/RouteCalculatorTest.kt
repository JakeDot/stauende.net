package net.stauende.app

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RouteCalculatorTest {

    private lateinit var mockHttpClient: OkHttpClient
    private lateinit var mockCall: Call
    private lateinit var calculator: RouteCalculator

    @Before
    fun setUp() {
        mockHttpClient = mockk()
        mockCall = mockk()
        calculator = RouteCalculator(apiKey = "TEST_KEY", httpClient = mockHttpClient)
    }

    private fun mockResponse(body: String) {
        val response = Response.Builder()
            .request(Request.Builder().url("https://maps.googleapis.com/").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody())
            .build()
        every { mockCall.execute() } returns response
        every { mockHttpClient.newCall(any()) } returns mockCall
    }

    @Test
    fun `calculateRoutes returns parsed route options on OK response`() {
        mockResponse(SAMPLE_DIRECTIONS_RESPONSE)

        val routes = calculator.calculateRoutes("Berlin", "Hamburg")

        assertEquals(1, routes.size)
        val route = routes.first()
        assertEquals("A24", route.summary)
        assertEquals(289_000, route.distanceMeters)
        assertEquals(7200, route.durationSeconds)
        assertEquals(9000, route.durationInTrafficSeconds)
        assertEquals(1800, route.trafficDelaySeconds)
        assertTrue(route.hasSignificantTraffic)
    }

    @Test(expected = RouteCalculationException::class)
    fun `calculateRoutes throws on non-OK API status`() {
        mockResponse("""{"status":"ZERO_RESULTS","geocoded_waypoints":[],"routes":[]}""")
        calculator.calculateRoutes("Nowhere", "Elsewhere")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `calculateRoutes throws on blank origin`() {
        calculator.calculateRoutes("", "Destination")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `calculateRoutes throws on blank destination`() {
        calculator.calculateRoutes("Origin", "  ")
    }

    @Test
    fun `hasSignificantTraffic is false when delay is less than 20 percent`() {
        val route = RouteOption(
            summary = "Test",
            distanceMeters = 10_000,
            durationSeconds = 600,
            durationInTrafficSeconds = 650, // +8 %
            startAddress = "A",
            endAddress = "B",
            steps = emptyList(),
            warnings = emptyList()
        )
        assertFalse(route.hasSignificantTraffic)
        assertEquals(50, route.trafficDelaySeconds)
    }

    @Test
    fun `hasSignificantTraffic is true when delay exceeds 20 percent`() {
        val route = RouteOption(
            summary = "Test",
            distanceMeters = 10_000,
            durationSeconds = 600,
            durationInTrafficSeconds = 750, // +25 %
            startAddress = "A",
            endAddress = "B",
            steps = emptyList(),
            warnings = emptyList()
        )
        assertTrue(route.hasSignificantTraffic)
        assertEquals(150, route.trafficDelaySeconds)
    }

    companion object {
        private val SAMPLE_DIRECTIONS_RESPONSE = """
            {
              "status": "OK",
              "geocoded_waypoints": [
                {"geocoder_status": "OK", "place_id": "ChIJAVkDPzdOqEcRcDteW0YgIQQ"},
                {"geocoder_status": "OK", "place_id": "ChIJuRMYfoNhsUcRoDrWe_I9JgQ"}
              ],
              "routes": [
                {
                  "summary": "A24",
                  "warnings": [],
                  "legs": [
                    {
                      "distance": {"text": "289 km", "value": 289000},
                      "duration": {"text": "2 hours", "value": 7200},
                      "duration_in_traffic": {"text": "2 hours 30 min", "value": 9000},
                      "start_address": "Berlin, Germany",
                      "end_address": "Hamburg, Germany",
                      "steps": []
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
    }
}
