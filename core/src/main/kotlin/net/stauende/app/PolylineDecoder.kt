package net.stauende.app

/** A single point on a decoded route path. */
data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * Decodes a Google-encoded polyline string (the `overview_polyline.points`
 * field of a Directions API route) into an ordered list of [GeoPoint]s.
 *
 * Algorithm: https://developers.google.com/maps/documentation/utilities/polylinealgorithm
 */
object PolylineDecoder {

    fun decode(encoded: String): List<GeoPoint> {
        if (encoded.isEmpty()) return emptyList()

        val points = mutableListOf<GeoPoint>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            val (deltaLat, nextIndexLat) = decodeValue(encoded, index)
            lat += deltaLat
            index = nextIndexLat

            val (deltaLng, nextIndexLng) = decodeValue(encoded, index)
            lng += deltaLng
            index = nextIndexLng

            points.add(GeoPoint(latitude = lat / 1E5, longitude = lng / 1E5))
        }

        return points
    }

    /** Decodes a single varint-encoded, zigzag delta starting at [startIndex]. */
    private fun decodeValue(encoded: String, startIndex: Int): Pair<Int, Int> {
        var index = startIndex
        var result = 0
        var shift = 0
        var b: Int
        do {
            b = encoded[index++].code - 63
            result = result or ((b and 0x1f) shl shift)
            shift += 5
        } while (b >= 0x20)
        val delta = if (result and 1 != 0) (result shr 1).inv() else (result shr 1)
        return delta to index
    }
}
