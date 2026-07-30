package net.stauende.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolylineDecoderTest {

    @Test
    fun `decode returns empty list for empty string`() {
        assertTrue(PolylineDecoder.decode("").isEmpty())
    }

    @Test
    fun `decode matches Google's reference example`() {
        // Reference vector from the official encoding algorithm docs:
        // https://developers.google.com/maps/documentation/utilities/polylinealgorithm
        val points = PolylineDecoder.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")

        assertEquals(3, points.size)
        assertEquals(38.5, points[0].latitude, 1e-4)
        assertEquals(-120.2, points[0].longitude, 1e-4)
        assertEquals(40.7, points[1].latitude, 1e-4)
        assertEquals(-120.95, points[1].longitude, 1e-4)
        assertEquals(43.252, points[2].latitude, 1e-4)
        assertEquals(-126.453, points[2].longitude, 1e-4)
    }
}
