package com.hazzus.karooclimber.debug

import com.hazzus.karooclimber.data.ClimbData
import com.hazzus.karooclimber.data.ElevationProfile
import com.hazzus.karooclimber.data.RouteData
import kotlin.math.PI
import kotlin.math.sin

/**
 * Canned route with two climbs for on-desk testing (no ride/navigation needed)
 * and as a shared fixture for unit tests.
 *
 * Points are generated every 100 m with a smooth grade "wiggle" between anchor
 * points so 100 m color chunks actually vary; the wiggle is zero at every anchor,
 * keeping the exact elevations unit tests assert on.
 */
object DemoData {

    private data class Leg(
        val endDistance: Double,
        val endElevation: Double,
        /** Wiggle amplitude in meters (0 = straight line). */
        val amp: Double = 0.0,
        /** Number of wiggle half-waves along the leg. */
        val waves: Int = 2,
    )

    /**
     * 5 km route where the action starts immediately:
     *  - climb 1: 0.3–2.3 km, 200 m → 350 m (7.5% avg, varying grade) — inside the
     *    default 500 m trigger right from the start
     *  - short downhill: 2.3–3.1 km, 350 m → 310 m
     *  - climb 2: 3.1–4.4 km, 310 m → 415 m (8.1% avg)
     *  - short flat to finish
     */
    fun route(): RouteData {
        var d = 0.0
        var e = 200.0
        val points = mutableListOf(ElevationProfile.Point(d, e))
        val legs = listOf(
            Leg(300.0, 200.0), // rolling to the base
            Leg(1300.0, 265.0, amp = 9.0, waves = 4), // climb 1 lower: 2–14% variation
            Leg(2300.0, 350.0, amp = 6.0, waves = 3), // climb 1 upper
            Leg(3100.0, 310.0, amp = 4.0, waves = 2), // short downhill
            Leg(4400.0, 415.0, amp = 6.0, waves = 4), // climb 2
            Leg(5000.0, 415.0), // flat finish
        )
        for (leg in legs) {
            val len = leg.endDistance - d
            val startE = e
            var x = STEP
            while (x < len - 1e-6) {
                val t = x / len
                val wiggle = leg.amp * sin(PI * leg.waves * t)
                points.add(ElevationProfile.Point(d + x, startE + (leg.endElevation - startE) * t + wiggle))
                x += STEP
            }
            d = leg.endDistance
            e = leg.endElevation
            points.add(ElevationProfile.Point(d, e))
        }
        return RouteData(
            routeDistance = 5000.0,
            profile = ElevationProfile.fromPoints(points),
            climbs = listOf(
                ClimbData(startDistance = 300.0, length = 2000.0, avgGrade = 7.5, totalElevation = 150.0),
                ClimbData(startDistance = 3100.0, length = 1300.0, avgGrade = 8.1, totalElevation = 105.0),
            ),
            routeKey = "demo",
        )
    }

    private const val STEP = 100.0
}
