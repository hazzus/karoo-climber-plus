package com.hazzus.karooclimber.data

/**
 * Route elevation profile: monotonically increasing distance (m) → elevation (m).
 * Built from Karoo's `routeElevationPolyline` (precision 1, pairs of distance, elevation).
 */
class ElevationProfile private constructor(
    val points: List<Point>,
) {
    data class Point(val distance: Double, val elevation: Double)

    /** A constant-grade stretch of the profile; grade in percent. */
    data class Segment(val start: Double, val end: Double, val grade: Double)

    val totalDistance: Double get() = points.last().distance

    /** Linear interpolation, clamped to the profile's range. */
    fun elevationAt(distance: Double): Double {
        if (distance <= points.first().distance) return points.first().elevation
        if (distance >= points.last().distance) return points.last().elevation
        val idx = points.binarySearchBy(distance) { it.distance }
        if (idx >= 0) return points[idx].elevation
        val after = -idx - 1
        val p0 = points[after - 1]
        val p1 = points[after]
        val t = (distance - p0.distance) / (p1.distance - p0.distance)
        return p0.elevation + t * (p1.elevation - p0.elevation)
    }

    /** Profile points within [from, to], with interpolated boundary points included. */
    fun slice(from: Double, to: Double): List<Point> {
        val lo = from.coerceIn(points.first().distance, points.last().distance)
        val hi = to.coerceIn(points.first().distance, points.last().distance)
        if (hi <= lo) return emptyList()
        val result = ArrayList<Point>()
        result.add(Point(lo, elevationAt(lo)))
        for (p in points) {
            if (p.distance > lo && p.distance < hi) result.add(p)
        }
        result.add(Point(hi, elevationAt(hi)))
        return result
    }

    /**
     * Resample [from, to] into ~[bucket]-meter segments with average grade each.
     * The final segment absorbs any remainder shorter than half a bucket.
     */
    fun segments(from: Double, to: Double, bucket: Double = 100.0): List<Segment> {
        val lo = from.coerceIn(points.first().distance, points.last().distance)
        val hi = to.coerceIn(points.first().distance, points.last().distance)
        if (hi <= lo) return emptyList()
        val result = ArrayList<Segment>()
        var start = lo
        while (start < hi) {
            var end = start + bucket
            // absorb tiny remainder into the last bucket
            if (hi - end < bucket / 2) end = hi
            val grade = (elevationAt(end) - elevationAt(start)) / (end - start) * 100.0
            result.add(Segment(start, end, grade))
            start = end
        }
        return result
    }

    companion object {
        private const val ELEVATION_POLYLINE_FACTOR = 10.0 // precision 1

        fun fromPolyline(encoded: String?): ElevationProfile? {
            if (encoded.isNullOrEmpty()) return null
            val pairs = Polyline.decode(encoded, ELEVATION_POLYLINE_FACTOR)
            return fromPoints(pairs.map { Point(it.first, it.second) })
        }

        fun fromPoints(points: List<Point>): ElevationProfile? {
            if (points.size < 2) return null
            // guard against non-monotonic distances (defensive; Karoo data should be sorted)
            val sorted = points.sortedBy { it.distance }
            return ElevationProfile(sorted)
        }
    }
}
