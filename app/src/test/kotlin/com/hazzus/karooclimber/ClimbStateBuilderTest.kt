package com.hazzus.karooclimber

import com.hazzus.karooclimber.data.ClimbData
import com.hazzus.karooclimber.data.ClimbInfo
import com.hazzus.karooclimber.data.ClimbStateBuilder
import com.hazzus.karooclimber.data.ClimbUiState
import com.hazzus.karooclimber.data.ElevationProfile
import com.hazzus.karooclimber.data.RouteData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimbStateBuilderTest {

    private val trigger = 500.0

    // Own fixture (decoupled): 5 km route, climbs at 1000–1600 and 3000–3800.
    private val profile = ElevationProfile.fromPoints(
        listOf(
            ElevationProfile.Point(0.0, 100.0),
            ElevationProfile.Point(1000.0, 100.0),
            ElevationProfile.Point(1600.0, 160.0), // 10% climb
            ElevationProfile.Point(3000.0, 160.0),
            ElevationProfile.Point(3800.0, 240.0), // 10% climb
            ElevationProfile.Point(5000.0, 200.0),
        ),
    )!!
    private val route = RouteData(
        routeDistance = 5000.0,
        profile = profile,
        climbs = listOf(
            ClimbData(startDistance = 1000.0, length = 600.0, avgGrade = 10.0, totalElevation = 60.0),
            ClimbData(startDistance = 3000.0, length = 800.0, avgGrade = 10.0, totalElevation = 80.0),
        ),
        routeKey = "route:test:false",
    )

    private fun shown(state: ClimbUiState): ClimbUiState.Shown {
        assertTrue("expected Shown, was $state", state is ClimbUiState.Shown)
        return state as ClimbUiState.Shown
    }

    @Test
    fun `no route hides`() {
        assertEquals(ClimbUiState.Hidden, ClimbStateBuilder().build(null, 500.0, null, trigger))
    }

    @Test
    fun `far from any climb shows list with no active`() {
        val s = shown(ClimbStateBuilder().build(route, 200.0, null, trigger))
        assertNull(s.active)
        assertEquals(2, s.climbs.size)
    }

    @Test
    fun `no climbs ahead hides when not on a climb`() {
        val s = ClimbStateBuilder().build(route, 4900.0, null, trigger)
        assertEquals(ClimbUiState.Hidden, s)
    }

    @Test
    fun `within trigger window approaches the next climb`() {
        val s = shown(ClimbStateBuilder().build(route, 700.0, null, trigger)) // 300 m before start
        val active = s.active
        assertNotNull(active)
        assertEquals(300.0, active!!.distanceToStart, 1e-9)
        assertEquals(900.0, active.distanceToTop, 1e-9) // 1600 − 700
    }

    @Test
    fun `on climb snapshots the span from progress and stream distances`() {
        val info = ClimbInfo(
            distanceToTop = 400.0,
            elevationToTop = 40.0,
            distanceFromBottom = 200.0,
            elevationFromBottom = 20.0,
            climbElevation = 60.0,
            climbNumber = 1,
            totalClimbs = 2,
        )
        // Rider 200 m into the first climb => route position 1200.
        val s = shown(ClimbStateBuilder().build(route, 1200.0, info, trigger))
        val active = s.active!!
        assertEquals(1000.0, active.climb.startDistance, 1e-9) // 1200 − 200 anchors the bottom
        assertEquals(600.0, active.climb.length, 1e-9) // 200 + 400
        assertEquals(0.0, active.distanceToStart, 1e-9)
        assertEquals(400.0, active.distanceToTop, 1e-9) // straight from the stream
        assertEquals(40.0, active.elevationToTop, 1e-9) // straight from the stream
        assertEquals(1200.0, s.progress, 1e-9) // startDistance + distanceFromBottom
    }

    @Test
    fun `mid-climb start still captures the whole climb bottom`() {
        // Stream first fires when already 150 m up: bottom must be pos − 150.
        val info = ClimbInfo(
            distanceToTop = 450.0, distanceFromBottom = 150.0, elevationToTop = null,
            elevationFromBottom = null, climbElevation = 60.0, climbNumber = 1, totalClimbs = 2,
        )
        val s = shown(ClimbStateBuilder().build(route, 1150.0, info, trigger))
        assertEquals(1000.0, s.active!!.climb.startDistance, 1e-9)
        assertEquals(600.0, s.active!!.climb.length, 1e-9)
    }

    @Test
    fun `frozen span survives and rider glides without DTD`() {
        val builder = ClimbStateBuilder()
        val first = ClimbInfo(400.0, 40.0, 200.0, 20.0, 60.0, 1, 2)
        shown(builder.build(route, 1200.0, first, trigger))
        // Next frame: no DTD at all, rider advanced 100 m per the stream.
        val next = ClimbInfo(300.0, 30.0, 300.0, 30.0, 60.0, 1, 2)
        val s = shown(builder.build(route, null, next, trigger))
        assertEquals(1000.0, s.active!!.climb.startDistance, 1e-9) // unchanged frozen
        assertEquals(1300.0, s.progress, 1e-9) // 1000 + 300 (stream)
    }

    @Test
    fun `climb end drops the snapshot and returns to the list`() {
        val builder = ClimbStateBuilder()
        builder.build(route, 1200.0, ClimbInfo(400.0, 40.0, 200.0, 20.0, 60.0, 1, 2), trigger)
        val s = shown(builder.build(route, 1700.0, null, trigger)) // past the first climb
        assertNull(s.active)
        assertEquals(1, s.climbs.size) // only the second climb remains ahead
    }

    @Test
    fun `moving to a new climb number re-snapshots`() {
        val builder = ClimbStateBuilder()
        builder.build(route, 1200.0, ClimbInfo(400.0, 40.0, 200.0, 20.0, 60.0, 1, 2), trigger)
        // Straight onto climb 2 (no null frame between), 100 m in => route position 3100.
        val info2 = ClimbInfo(700.0, 70.0, 100.0, 10.0, 80.0, 2, 2)
        val s = shown(builder.build(route, 3100.0, info2, trigger))
        assertEquals(3000.0, s.active!!.climb.startDistance, 1e-9)
        assertEquals(800.0, s.active!!.climb.length, 1e-9)
    }

    @Test
    fun `route change resets the frozen span`() {
        val builder = ClimbStateBuilder()
        builder.build(route, 1200.0, ClimbInfo(400.0, 40.0, 200.0, 20.0, 60.0, 1, 2), trigger)
        val rerouted = route.copy(routeKey = "route:other:false")
        // On the new route the same stream frame must re-anchor from the new progress.
        val s = shown(builder.build(rerouted, 2500.0, ClimbInfo(400.0, 40.0, 200.0, 20.0, 60.0, 1, 2), trigger))
        assertEquals(2300.0, s.active!!.climb.startDistance, 1e-9) // 2500 − 200
    }

    @Test
    fun `no route and no climb hides`() {
        assertEquals(ClimbUiState.Hidden, ClimbStateBuilder().build(null, null, null, trigger))
    }

    @Test
    fun `no route with an active climb shows a predicted climb from the stream`() {
        val info = ClimbInfo(400.0, 40.0, 200.0, 20.0, 60.0, 2, 5)
        // No route and no DTD: everything comes from the CLIMB stream, relative coords.
        val s = shown(ClimbStateBuilder().build(null, null, info, trigger))
        val active = s.active!!
        assertEquals(0.0, active.climb.startDistance, 1e-9)
        assertEquals(600.0, active.climb.length, 1e-9) // 200 + 400
        assertEquals(60.0, active.climb.totalElevation, 1e-9)
        assertEquals(400.0, active.distanceToTop, 1e-9)
        assertEquals(5, active.total)
        assertEquals(1, active.index) // climbNumber (2) − 1
        assertEquals(200.0, s.progress, 1e-9) // distanceFromBottom
        assertTrue(s.climbs.isEmpty()) // no route -> no upcoming list
        assertNull(s.profile) // view draws a constant-grade wedge
    }

    @Test
    fun `route-less span is frozen while the rider glides`() {
        val builder = ClimbStateBuilder()
        shown(builder.build(null, null, ClimbInfo(400.0, 40.0, 200.0, 20.0, 60.0, 2, 5), trigger))
        val s = shown(builder.build(null, null, ClimbInfo(300.0, 30.0, 300.0, 30.0, 60.0, 2, 5), trigger))
        assertEquals(0.0, s.active!!.climb.startDistance, 1e-9)
        assertEquals(600.0, s.active!!.climb.length, 1e-9) // unchanged
        assertEquals(300.0, s.progress, 1e-9) // distanceFromBottom advanced
    }

    @Test
    fun `route-less climb re-anchors when a route appears`() {
        val builder = ClimbStateBuilder()
        shown(builder.build(null, null, ClimbInfo(400.0, 40.0, 200.0, 20.0, 60.0, 1, 2), trigger))
        // Same climb, now with a route + DTD: must re-snapshot to route coordinates.
        val s = shown(builder.build(route, 1200.0, ClimbInfo(400.0, 40.0, 200.0, 20.0, 60.0, 1, 2), trigger))
        assertEquals(1000.0, s.active!!.climb.startDistance, 1e-9) // 1200 − 200, not 0
    }
}
