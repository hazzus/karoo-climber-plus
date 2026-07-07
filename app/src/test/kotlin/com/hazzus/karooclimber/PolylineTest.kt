package com.hazzus.karooclimber

import com.hazzus.karooclimber.data.Polyline
import org.junit.Assert.assertEquals
import org.junit.Test

class PolylineTest {

    @Test
    fun `decodes google reference vector at precision 5`() {
        // Reference example from Google's polyline algorithm docs
        val decoded = Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 1e5)
        assertEquals(3, decoded.size)
        assertEquals(38.5, decoded[0].first, 1e-9)
        assertEquals(-120.2, decoded[0].second, 1e-9)
        assertEquals(40.7, decoded[1].first, 1e-9)
        assertEquals(-120.95, decoded[1].second, 1e-9)
        assertEquals(43.252, decoded[2].first, 1e-9)
        assertEquals(-126.453, decoded[2].second, 1e-9)
    }

    @Test
    fun `encode-decode round trip at precision 1 (elevation polyline)`() {
        // (distance, elevation) pairs as Karoo encodes them, factor 10
        val original = listOf(
            0.0 to 100.0,
            250.5 to 112.3,
            1000.0 to 98.7,
            5250.1 to 432.9,
            10000.0 to 240.0,
        )
        val encoded = Polyline.encode(original, 10.0)
        val decoded = Polyline.decode(encoded, 10.0)
        assertEquals(original.size, decoded.size)
        original.zip(decoded).forEach { (o, d) ->
            assertEquals(o.first, d.first, 0.05) // precision 1 = 0.1 resolution
            assertEquals(o.second, d.second, 0.05)
        }
    }

    @Test
    fun `handles negative deltas`() {
        val original = listOf(100.0 to 500.0, 200.0 to 400.0, 300.0 to 450.0)
        val decoded = Polyline.decode(Polyline.encode(original, 10.0), 10.0)
        assertEquals(400.0, decoded[1].second, 0.05)
        assertEquals(450.0, decoded[2].second, 0.05)
    }
}
