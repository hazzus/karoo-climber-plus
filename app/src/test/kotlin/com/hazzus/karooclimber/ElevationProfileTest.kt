package com.hazzus.karooclimber

import com.hazzus.karooclimber.data.ElevationProfile
import com.hazzus.karooclimber.data.Polyline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElevationProfileTest {

    private val profile = ElevationProfile.fromPoints(
        listOf(
            ElevationProfile.Point(0.0, 100.0),
            ElevationProfile.Point(1000.0, 100.0),
            ElevationProfile.Point(2000.0, 200.0), // 10% ramp
            ElevationProfile.Point(3000.0, 150.0), // -5% descent
        ),
    )!!

    @Test
    fun `elevationAt interpolates linearly`() {
        assertEquals(100.0, profile.elevationAt(500.0), 1e-9)
        assertEquals(150.0, profile.elevationAt(1500.0), 1e-9)
        assertEquals(175.0, profile.elevationAt(2500.0), 1e-9)
    }

    @Test
    fun `elevationAt clamps out of range`() {
        assertEquals(100.0, profile.elevationAt(-50.0), 1e-9)
        assertEquals(150.0, profile.elevationAt(9999.0), 1e-9)
    }

    @Test
    fun `slice includes interpolated boundaries`() {
        val slice = profile.slice(1500.0, 2500.0)
        assertEquals(1500.0, slice.first().distance, 1e-9)
        assertEquals(150.0, slice.first().elevation, 1e-9)
        assertEquals(2500.0, slice.last().distance, 1e-9)
        assertEquals(175.0, slice.last().elevation, 1e-9)
        // interior point 2000 present
        assertTrue(slice.any { it.distance == 2000.0 })
    }

    @Test
    fun `slice with empty range returns empty`() {
        assertTrue(profile.slice(2500.0, 2500.0).isEmpty())
        assertTrue(profile.slice(2600.0, 2500.0).isEmpty())
    }

    @Test
    fun `segments compute bucket grades`() {
        val segments = profile.segments(1000.0, 2000.0, bucket = 500.0)
        assertEquals(2, segments.size)
        assertEquals(10.0, segments[0].grade, 1e-9)
        assertEquals(10.0, segments[1].grade, 1e-9)
    }

    @Test
    fun `segments absorb short remainder into last bucket`() {
        // 1000..2100 with 500 buckets -> 500, 500, and last absorbs 100 => 2 full + extended
        val segments = profile.segments(1000.0, 2100.0, bucket = 500.0)
        assertEquals(segments.last().end, 2100.0, 1e-9)
        // no segment shorter than half a bucket
        segments.forEach { assertTrue(it.end - it.start >= 250.0) }
    }

    @Test
    fun `segments across summit show sign change`() {
        val segments = profile.segments(1500.0, 2500.0, bucket = 500.0)
        assertEquals(10.0, segments[0].grade, 1e-9)
        assertEquals(-5.0, segments[1].grade, 1e-9)
    }

    @Test
    fun `fromPolyline decodes karoo-style distance-elevation pairs`() {
        val pairs = listOf(0.0 to 100.0, 500.0 to 120.0, 1000.0 to 180.0)
        val encoded = Polyline.encode(pairs, 10.0)
        val p = ElevationProfile.fromPolyline(encoded)!!
        assertEquals(1000.0, p.totalDistance, 0.05)
        assertEquals(120.0, p.elevationAt(500.0), 0.1)
    }

    @Test
    fun `fromPolyline rejects null or empty`() {
        assertNull(ElevationProfile.fromPolyline(null))
        assertNull(ElevationProfile.fromPolyline(""))
    }
}
