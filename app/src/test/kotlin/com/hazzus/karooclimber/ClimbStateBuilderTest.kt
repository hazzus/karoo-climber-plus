package com.hazzus.karooclimber

import com.hazzus.karooclimber.data.ClimbData
import com.hazzus.karooclimber.data.ClimbInfo
import com.hazzus.karooclimber.data.CompletedClimb
import com.hazzus.karooclimber.data.ClimbStateBuilder
import com.hazzus.karooclimber.data.ClimbUiState
import com.hazzus.karooclimber.data.ElevationProfile
import com.hazzus.karooclimber.data.RouteData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

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
    fun `rerouting shows climbs at rider-relative distances with a wedge`() {
        // Off-route: climbs are distances AHEAD of the rider, and the profile no longer aligns.
        val relClimbs = listOf(
            ClimbData(startDistance = 300.0, length = 600.0, avgGrade = 10.0, totalElevation = 60.0),
            ClimbData(startDistance = 2000.0, length = 800.0, avgGrade = 10.0, totalElevation = 80.0),
        )
        val rr = route.copy(rerouting = true, climbs = relClimbs)
        // First reroute frame: progress (42000) just seeds the anchor, so moved = 0.
        val s = shown(ClimbStateBuilder().build(rr, 42_000.0, null, trigger))
        assertEquals(0.0, s.progress, 1e-9) // moved since anchor
        assertNull(s.profile) // wedge silhouettes, not a wrong profile slice
        assertEquals(2, s.upcoming.size) // both climbs are ahead
        val active = s.active!!
        assertEquals(300.0, active.distanceToStart, 1e-9) // straight from startDistance
        assertEquals(900.0, active.distanceToTop, 1e-9) // 300 + 600
        assertEquals(60.0, active.elevationToTop, 1e-9) // full climb ahead, from the wedge
    }

    @Test
    fun `rerouting with the next climb beyond the trigger shows the list without active`() {
        val rr = route.copy(rerouting = true, climbs = listOf(ClimbData(3000.0, 600.0, 10.0, 60.0)))
        val s = shown(ClimbStateBuilder().build(rr, 0.0, null, trigger))
        assertNull(s.active) // 3000 m ahead > 500 m trigger
        assertEquals(1, s.upcoming.size)
        assertNull(s.profile)
    }

    @Test
    fun `rejoining resumes the absolute frame and the real profile`() {
        val builder = ClimbStateBuilder()
        val rr = route.copy(rerouting = true, climbs = listOf(ClimbData(300.0, 600.0, 10.0, 60.0)))
        val off = shown(builder.build(rr, 999.0, null, trigger))
        assertNull(off.profile)
        assertEquals(300.0, off.active!!.distanceToStart, 1e-9) // rider-relative
        // Rejoin (rerouting=false, absolute climbs): usual logic + real profile.
        val on = shown(builder.build(route, 700.0, null, trigger))
        assertNotNull(on.profile)
        assertEquals(300.0, on.active!!.distanceToStart, 1e-9) // 1000 − 700, absolute
    }

    @Test
    fun `rerouting surfaces the on-climb state from the stream instead of skipping it`() {
        val builder = ClimbStateBuilder()
        val rr = route.copy(rerouting = true, climbs = listOf(ClimbData(0.0, 600.0, 10.0, 60.0)))
        // Rider is on a climb during the reroute; the CLIMB stream is live (native shows it).
        val info = ClimbInfo(
            distanceToTop = 400.0,
            elevationToTop = 40.0,
            distanceFromBottom = 200.0,
            elevationFromBottom = 20.0,
            climbElevation = 60.0,
            climbNumber = 3,
            totalClimbs = 5,
        )
        val s = shown(builder.build(rr, 12_345.0, info, trigger))
        val active = s.active!!
        assertEquals(0.0, active.distanceToStart, 1e-9) // on the climb, not merely approaching
        assertEquals(400.0, active.distanceToTop, 1e-9) // straight from the stream
        assertEquals(40.0, active.elevationToTop, 1e-9)
        assertNull(s.profile) // wedge
        assertEquals(200.0, s.progress, 1e-9) // distanceFromBottom, relative to the climb
    }

    @Test
    fun `rerouting decreases distance to the climb as the rider moves`() {
        val builder = ClimbStateBuilder()
        val rr = route.copy(rerouting = true, climbs = listOf(ClimbData(1000.0, 600.0, 10.0, 60.0)))
        val a = shown(builder.build(rr, 50_000.0, null, trigger)) // anchor, moved = 0
        assertEquals(0.0, a.progress, 1e-9)
        assertEquals(1000.0, a.climbs.first().startDistance - a.progress, 1e-9) // 1000 m ahead
        // Rider moves 300 m; Karoo re-emits the SAME (still anchor-relative) list.
        val b = shown(builder.build(rr, 50_300.0, null, trigger))
        assertEquals(300.0, b.progress, 1e-9) // moved since the anchor
        assertEquals(700.0, b.climbs.first().startDistance - b.progress, 1e-9) // now 700 m ahead
    }

    @Test
    fun `a fresh reroute computation re-anchors the progression`() {
        val builder = ClimbStateBuilder()
        val rr1 = route.copy(rerouting = true, climbs = listOf(ClimbData(1000.0, 600.0, 10.0, 60.0)))
        builder.build(rr1, 50_000.0, null, trigger)
        builder.build(rr1, 50_400.0, null, trigger) // moved 400
        // Karoo recomputes: brand-new re-based starts -> moved resets to 0.
        val rr2 = route.copy(rerouting = true, climbs = listOf(ClimbData(800.0, 600.0, 10.0, 60.0)))
        val c = shown(builder.build(rr2, 50_400.0, null, trigger))
        assertEquals(0.0, c.progress, 1e-9)
        assertEquals(800.0, c.climbs.first().startDistance - c.progress, 1e-9)
    }

    @Test
    fun `passing a climb during reroute does not re-anchor the progression`() {
        val builder = ClimbStateBuilder()
        val two = listOf(ClimbData(400.0, 300.0, 10.0, 30.0), ClimbData(2000.0, 600.0, 10.0, 60.0))
        builder.build(route.copy(rerouting = true, climbs = two), 50_000.0, null, trigger)
        // Rider moved 500 m; nearest climb passed, Karoo drops it, far one unchanged.
        val dropped = route.copy(rerouting = true, climbs = listOf(ClimbData(2000.0, 600.0, 10.0, 60.0)))
        val s = shown(builder.build(dropped, 50_500.0, null, trigger))
        assertEquals(500.0, s.progress, 1e-9) // same anchor kept, progression continuous
        assertEquals(1500.0, s.climbs.first().startDistance - s.progress, 1e-9) // 2000 − 500
    }

    @Test
    fun `topping out records the ridden distance and real grade`() {
        val builder = ClimbStateBuilder()
        builder.build(route, 1200.0, ClimbInfo(400.0, 40.0, 200.0, 20.0, 60.0, 1, 2), trigger)
        // Ride on up to the summit; the last on-climb frame is at the top.
        builder.build(route, 1600.0, ClimbInfo(0.0, 0.0, 600.0, 60.0, 60.0, 1, 2), trigger)
        val s = shown(builder.build(route, 1700.0, null, trigger)) // stream null, route present
        assertEquals(1, s.completed.size)
        assertEquals(600.0, s.completed[0].length, 1e-9) // ridden from bottom at the summit
        assertEquals(10.0, s.completed[0].avgGrade, 1e-9) // 60 m gained over 600 m
        assertEquals(1, s.completed[0].climbNumber)
        assertEquals(listOf(600.0), builder.completedClimbs.map { it.length })
    }

    @Test
    fun `bailing out mid-climb records only the ridden portion at its real grade`() {
        val builder = ClimbStateBuilder()
        builder.build(route, 1200.0, ClimbInfo(400.0, 40.0, 200.0, 20.0, 60.0, 1, 2), trigger)
        // Rider gets 300 m up, gaining a steep 45 m, then leaves the climb (stream null).
        builder.build(route, 1300.0, ClimbInfo(300.0, 15.0, 300.0, 45.0, 60.0, 1, 2), trigger)
        val s = shown(builder.build(route, 1350.0, null, trigger))
        assertEquals(1, s.completed.size)
        assertEquals(300.0, s.completed[0].length, 1e-9) // only what was ridden, not the 600 m span
        assertEquals(15.0, s.completed[0].avgGrade, 1e-9) // real grade over the ridden part, not planned 10
    }

    @Test
    fun `climb-number advance records the previous climb's ridden distance`() {
        val builder = ClimbStateBuilder()
        builder.build(route, 1200.0, ClimbInfo(400.0, 40.0, 200.0, 20.0, 60.0, 1, 2), trigger)
        builder.build(route, 1600.0, ClimbInfo(0.0, 0.0, 600.0, 60.0, 60.0, 1, 2), trigger) // top of climb 1
        // Straight onto climb 2 (no null frame between).
        val s = shown(builder.build(route, 3100.0, ClimbInfo(700.0, 70.0, 100.0, 10.0, 80.0, 2, 2), trigger))
        assertEquals(1, s.completed.size)
        assertEquals(1, s.completed[0].climbNumber)
        assertEquals(600.0, s.completed[0].length, 1e-9)
    }

    @Test
    fun `re-detecting the same climb does not record it twice`() {
        val builder = ClimbStateBuilder()
        builder.build(route, 1200.0, ClimbInfo(400.0, 40.0, 200.0, 20.0, 60.0, 1, 2), trigger)
        builder.build(route, 1700.0, null, trigger) // topped out -> recorded once
        // GPS flap re-detects the SAME climb (same number), then it ends again.
        builder.build(route, 1300.0, ClimbInfo(300.0, 30.0, 300.0, 30.0, 60.0, 1, 2), trigger)
        val s = shown(builder.build(route, 1700.0, null, trigger))
        assertEquals(1, s.completed.size)
    }

    @Test
    fun `reset clears completed climbs`() {
        val builder = ClimbStateBuilder()
        builder.build(route, 1200.0, ClimbInfo(400.0, 40.0, 200.0, 20.0, 60.0, 1, 2), trigger)
        builder.build(route, 1700.0, null, trigger)
        assertEquals(1, builder.completedClimbs.size)
        builder.reset()
        assertTrue(builder.completedClimbs.isEmpty())
    }

    @Test
    fun `restore seeds completed and prevents re-recording that climb`() {
        val builder = ClimbStateBuilder()
        builder.restore(listOf(CompletedClimb(length = 600.0, avgGrade = 10.0, climbNumber = 1)))
        val s0 = shown(builder.build(route, 200.0, null, trigger))
        assertEquals(1, s0.completed.size)
        // Climb 1 is re-detected then ends: must NOT record a second time.
        builder.build(route, 1200.0, ClimbInfo(400.0, 40.0, 200.0, 20.0, 60.0, 1, 2), trigger)
        val s = shown(builder.build(route, 1700.0, null, trigger))
        assertEquals(1, s.completed.size)
    }

    @Test
    fun `route change does not record a completion`() {
        val builder = ClimbStateBuilder()
        builder.build(route, 1200.0, ClimbInfo(400.0, 40.0, 200.0, 20.0, 60.0, 1, 2), trigger)
        val rerouted = route.copy(routeKey = "route:other:false")
        val s = shown(builder.build(rerouted, 2500.0, ClimbInfo(400.0, 40.0, 200.0, 20.0, 60.0, 1, 2), trigger))
        assertTrue(s.completed.isEmpty())
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

    // --- Live-traced silhouette of the ridden part of a wedge (null-profile) climb ---

    @Test
    fun `traced profile grows monotonically and spans the whole climb`() {
        val builder = ClimbStateBuilder()
        // Route-less wedge climb, 600 m / +60 m, fed real from-bottom samples ~1 Hz.
        builder.build(null, null, ClimbInfo(500.0, 50.0, 100.0, 10.0, 60.0, 1, 1), trigger)
        builder.build(null, null, ClimbInfo(400.0, 40.0, 200.0, 20.0, 60.0, 1, 1), trigger)
        val s = shown(builder.build(null, null, ClimbInfo(300.0, 30.0, 300.0, 30.0, 60.0, 1, 1), trigger))
        assertNull(s.profile) // wedge climb: the trace supplements the wedge
        val traced = s.tracedProfile!!
        // Spans the climb frame: bottom anchor at 0, straight line to the known top at length.
        assertEquals(0.0, traced.points.first().distance, 1e-9)
        assertEquals(600.0, traced.points.last().distance, 1e-9)
        // Strictly increasing in distance.
        assertTrue(traced.points.zipWithNext().all { (a, b) -> a.distance < b.distance })
        // Last real sample sits at the rider's distanceFromBottom (just before the top anchor).
        assertEquals(300.0, traced.points[traced.points.size - 2].distance, 1e-9)
        assertEquals(30.0, traced.elevationAt(300.0), 1e-9)
    }

    @Test
    fun `traced profile preserves a non-linear ridden shape`() {
        val builder = ClimbStateBuilder()
        // Steep start: elevation-from-bottom is NOT linear in distance-from-bottom.
        builder.build(null, null, ClimbInfo(500.0, 20.0, 100.0, 40.0, 60.0, 1, 1), trigger)
        val s = shown(builder.build(null, null, ClimbInfo(300.0, 10.0, 300.0, 50.0, 60.0, 1, 1), trigger))
        val traced = s.tracedProfile!!
        val wedge = 60.0 * 300.0 / 600.0 // constant-grade wedge value at the midpoint = 30
        assertEquals(50.0, traced.elevationAt(300.0), 1e-9) // real observation, not the wedge
        assertTrue(abs(traced.elevationAt(300.0) - wedge) > 1.0)
    }

    @Test
    fun `traced profile resets on a new climb number`() {
        val builder = ClimbStateBuilder()
        builder.build(null, null, ClimbInfo(500.0, 50.0, 100.0, 10.0, 60.0, 1, 2), trigger)
        builder.build(null, null, ClimbInfo(400.0, 40.0, 200.0, 20.0, 60.0, 1, 2), trigger)
        val s1 = shown(builder.build(null, null, ClimbInfo(300.0, 30.0, 300.0, 30.0, 60.0, 1, 2), trigger))
        assertEquals(5, s1.tracedProfile!!.points.size) // bottom + 3 samples + top
        // Straight onto climb 2 (new number, no null frame): the previous trace is dropped.
        val s2 = shown(builder.build(null, null, ClimbInfo(550.0, 55.0, 50.0, 5.0, 60.0, 2, 2), trigger))
        assertEquals(3, s2.tracedProfile!!.points.size) // bottom + 1 fresh sample + top
    }

    @Test
    fun `traced profile clears when the stream ends and starts fresh`() {
        val builder = ClimbStateBuilder()
        builder.build(null, null, ClimbInfo(500.0, 50.0, 100.0, 10.0, 60.0, 1, 1), trigger)
        builder.build(null, null, ClimbInfo(400.0, 40.0, 200.0, 20.0, 60.0, 1, 1), trigger)
        // Stream ends (no route, no climb) -> Hidden and the trace is dropped.
        assertEquals(ClimbUiState.Hidden, builder.build(null, null, null, trigger))
        // A fresh climb: the trace does not carry the previous climb's samples.
        val s = shown(builder.build(null, null, ClimbInfo(500.0, 50.0, 100.0, 10.0, 60.0, 1, 1), trigger))
        assertEquals(3, s.tracedProfile!!.points.size) // bottom + 1 fresh sample + top
    }

    @Test
    fun `climb gain uses from-bottom plus to-top, not the stream CLIMB_ELEVATION`() {
        // Real-ride reroute frame: CLIMB_ELEVATION (446) is the rider's altitude, not the
        // climb's gain. The climb gains efb + ett = ~68 m over 1440 m => ~4.7%, not 31%.
        val info = ClimbInfo(
            distanceToTop = 1438.0, elevationToTop = 68.0,
            distanceFromBottom = 2.0, elevationFromBottom = 0.0,
            climbElevation = 446.0, climbNumber = null, totalClimbs = null,
        )
        val climb = shown(ClimbStateBuilder().build(null, null, info, trigger)).active!!.climb
        assertEquals(1440.0, climb.length, 1e-9)
        assertEquals(68.0, climb.totalElevation, 1e-9) // efb + ett, NOT the 446 altitude
        assertEquals(68.0 / 1440.0 * 100.0, climb.avgGrade, 1e-9) // ~4.7%, not 31%
    }

    @Test
    fun `traced profile ignores frames without elevationFromBottom`() {
        val builder = ClimbStateBuilder()
        // No elevationFromBottom: we no longer reconstruct it from CLIMB_ELEVATION (unreliable),
        // so there's nothing to trace this frame.
        val s = shown(builder.build(null, null, ClimbInfo(400.0, 40.0, 200.0, null, 60.0, 1, 1), trigger))
        assertNull(s.tracedProfile)
    }

    @Test
    fun `traced profile stays null without any elevation signal`() {
        val builder = ClimbStateBuilder()
        // No elevationFromBottom, no climbElevation, no elevationToTop -> nothing to trace.
        val s = shown(builder.build(null, null, ClimbInfo(400.0, null, 200.0, null, null, 1, 1), trigger))
        assertNull(s.tracedProfile)
        assertNull(s.profile)
    }

    @Test
    fun `traced profile downsamples samples closer than the trace step`() {
        val builder = ClimbStateBuilder()
        val s1 = shown(builder.build(null, null, ClimbInfo(500.0, 50.0, 100.0, 10.0, 60.0, 1, 1), trigger))
        val before = s1.tracedProfile!!.points.size
        // Second frame only 5 m further (< TRACE_STEP_M) -> no new point, same object.
        val s2 = shown(builder.build(null, null, ClimbInfo(495.0, 12.0, 105.0, 12.0, 60.0, 1, 1), trigger))
        assertEquals(before, s2.tracedProfile!!.points.size)
        assertSame(s1.tracedProfile, s2.tracedProfile) // identity stable (no rebuild)
    }

    @Test
    fun `traced profile rejects an elevation spike above the climb total`() {
        val builder = ClimbStateBuilder()
        val s1 = shown(builder.build(null, null, ClimbInfo(500.0, 50.0, 100.0, 10.0, 60.0, 1, 1), trigger))
        val n1 = s1.tracedProfile!!.points.size
        // Glitchy frame: elevationFromBottom 500 >> climbElevation 60 -> dropped, no spike.
        val s2 = shown(builder.build(null, null, ClimbInfo(400.0, 40.0, 200.0, 500.0, 60.0, 1, 1), trigger))
        val traced = s2.tracedProfile!!
        assertEquals(n1, traced.points.size) // the glitch was not recorded
        assertTrue(traced.points.all { it.elevation <= 60.0 + 1e-9 }) // never above the climb total
    }

    @Test
    fun `traced profile clamps a downward elevation dip to non-decreasing`() {
        val builder = ClimbStateBuilder()
        builder.build(null, null, ClimbInfo(400.0, 40.0, 200.0, 30.0, 60.0, 1, 1), trigger) // (200, 30)
        // Next frame dips to 20 (noise); a climb only rises from the bottom -> clamped up to 30.
        val s = shown(builder.build(null, null, ClimbInfo(300.0, 30.0, 300.0, 20.0, 60.0, 1, 1), trigger))
        val traced = s.tracedProfile!!
        assertTrue(traced.points.zipWithNext().all { (a, b) -> b.elevation >= a.elevation - 1e-9 })
        assertEquals(30.0, traced.elevationAt(300.0), 1e-9) // dip clamped to the prior 30
    }

    // --- Recording climbs ridden off the route / during a reroute ---

    @Test
    fun `a climb ridden during a reroute is recorded on top-out`() {
        val builder = ClimbStateBuilder()
        val rr = route.copy(rerouting = true, climbs = listOf(ClimbData(5000.0, 600.0, 10.0, 60.0)))
        // On the climb from the live stream: 500 m up a 600 m / 60 m climb, no climb number.
        builder.build(rr, 12_000.0, ClimbInfo(100.0, 10.0, 500.0, 50.0, 60.0, null, null), trigger)
        // Stream goes null -> recorded, even though the climb is off the route.
        val s = shown(builder.build(rr, 12_100.0, null, trigger))
        assertEquals(1, s.completed.size)
        assertEquals(500.0, s.completed[0].length, 1e-9) // the ridden portion
        assertEquals(10.0, s.completed[0].avgGrade, 1e-9) // 50 m over 500 m
        assertNull(s.completed[0].climbNumber) // off-route: no number
    }

    @Test
    fun `two off-route climbs of equal length are both recorded (distinct absolute spans)`() {
        val builder = ClimbStateBuilder()
        val rr = route.copy(rerouting = true, climbs = emptyList())
        // Climb 1 near route position 10 km.
        builder.build(rr, 10_000.0, ClimbInfo(100.0, 10.0, 500.0, 50.0, 60.0, null, null), trigger)
        builder.build(rr, 10_100.0, null, trigger)
        // Climb 2: identical length, far along the route -> distinct span, not deduped away.
        builder.build(rr, 20_000.0, ClimbInfo(100.0, 10.0, 500.0, 50.0, 60.0, null, null), trigger)
        builder.build(rr, 20_100.0, null, trigger)
        assertEquals(2, builder.completedClimbs.size)
    }

    @Test
    fun `a pure route-less climb is recorded on top-out`() {
        val builder = ClimbStateBuilder()
        builder.build(null, null, ClimbInfo(100.0, 10.0, 500.0, 50.0, 60.0, null, null), trigger)
        builder.build(null, null, null, trigger) // no route, no climb -> Hidden + recorded
        assertEquals(1, builder.completedClimbs.size)
    }

    @Test
    fun `a reroute climb re-detected after a flap is recorded once`() {
        val builder = ClimbStateBuilder()
        val rr = route.copy(rerouting = true, climbs = emptyList())
        builder.build(rr, 10_000.0, ClimbInfo(100.0, 10.0, 500.0, 50.0, 60.0, null, null), trigger)
        builder.build(rr, 10_050.0, null, trigger) // brief flap -> recorded (absStart ~9500)
        // Same climb re-detected (rider advanced 50 m; the bottom stays put), then ends again.
        builder.build(rr, 10_050.0, ClimbInfo(50.0, 5.0, 550.0, 55.0, 60.0, null, null), trigger)
        builder.build(rr, 10_100.0, null, trigger)
        assertEquals(1, builder.completedClimbs.size) // same bucketed absolute span -> deduped
    }
}
