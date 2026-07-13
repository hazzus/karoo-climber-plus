package com.hazzus.karooclimber

import com.hazzus.karooclimber.data.ClimbData
import com.hazzus.karooclimber.data.ClimbEngine
import com.hazzus.karooclimber.data.ClimbUiState
import com.hazzus.karooclimber.data.ElevationProfile
import com.hazzus.karooclimber.data.RouteData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimbEngineTest {

    /**
     * Fixed test route (independent of DemoData):
     * flat to 2 km @ 100 m; climb A 2–4.5 km to 280 m; descent to 150 m at 7 km;
     * climb B 7–8.2 km to 240 m; flat finish at 10 km.
     */
    private val route = RouteData(
        routeDistance = 10000.0,
        profile = ElevationProfile.fromPoints(
            listOf(
                ElevationProfile.Point(0.0, 100.0),
                ElevationProfile.Point(2000.0, 100.0),
                ElevationProfile.Point(2500.0, 110.0),
                ElevationProfile.Point(3000.0, 145.0),
                ElevationProfile.Point(3500.0, 215.0),
                ElevationProfile.Point(4000.0, 255.0),
                ElevationProfile.Point(4500.0, 280.0),
                ElevationProfile.Point(6000.0, 160.0),
                ElevationProfile.Point(7000.0, 150.0),
                ElevationProfile.Point(8200.0, 240.0),
                ElevationProfile.Point(10000.0, 240.0),
            ),
        ),
        climbs = listOf(
            ClimbData(startDistance = 2000.0, length = 2500.0, avgGrade = 7.2, totalElevation = 180.0),
            ClimbData(startDistance = 7000.0, length = 1200.0, avgGrade = 7.5, totalElevation = 90.0),
        ),
        routeKey = "test",
    )
    private val trigger = 500.0

    private fun shownAt(engine: ClimbEngine, progress: Double): ClimbUiState.Shown {
        val state = engine.update(route, progress, trigger)
        assertTrue("expected Shown at $progress, got $state", state is ClimbUiState.Shown)
        return state as ClimbUiState.Shown
    }

    private fun activeAt(engine: ClimbEngine, progress: Double): ClimbUiState.ActiveClimb {
        val shown = shownAt(engine, progress)
        val active = shown.active
        assertTrue("expected active climb at $progress", active != null)
        return active!!
    }

    @Test
    fun `hidden with no route or progress`() {
        val engine = ClimbEngine()
        assertEquals(ClimbUiState.Hidden, engine.update(null, 100.0, trigger))
        assertEquals(ClimbUiState.Hidden, engine.update(route, null, trigger))
    }

    @Test
    fun `hidden when the route has no climbs`() {
        val engine = ClimbEngine()
        assertEquals(ClimbUiState.Hidden, engine.update(route.copy(climbs = emptyList()), 1000.0, trigger))
    }

    @Test
    fun `shown without active before trigger window`() {
        val engine = ClimbEngine()
        // climb A starts at 2000, trigger 500 -> no active until 1500
        assertNull(shownAt(engine, 1000.0).active)
        assertNull(shownAt(engine, 1499.0).active)
    }

    @Test
    fun `counts total and completed climbs`() {
        val engine = ClimbEngine()
        val early = shownAt(engine, 1000.0)
        assertEquals(2, early.totalCount)
        assertEquals(0, early.completedCount)
        assertEquals(2, early.upcoming.size)

        shownAt(engine, 3000.0) // riding climb A
        val betweenClimbs = shownAt(engine, 6000.0) // topped A
        assertEquals(1, betweenClimbs.completedCount)
        assertEquals(2, betweenClimbs.totalCount)
        assertEquals(1, betweenClimbs.upcoming.size)

        shownAt(engine, 7500.0) // riding climb B
        val afterAll = shownAt(engine, 9500.0)
        assertEquals(2, afterAll.completedCount)
        assertEquals(2, afterAll.totalCount)
        assertTrue(afterAll.upcoming.isEmpty())
        assertNull(afterAll.active)
    }

    @Test
    fun `climbs never ridden do not count as completed or total`() {
        val engine = ClimbEngine()
        // rider joins the route between the climbs without ever riding climb A
        val s = shownAt(engine, 6000.0)
        assertEquals(0, s.completedCount)
        assertEquals(1, s.totalCount)
        assertEquals(1, s.upcoming.size)
    }

    @Test
    fun `completed climbs survive reroute list adoption`() {
        val engine = ClimbEngine()
        shownAt(engine, 3000.0) // riding climb A
        shownAt(engine, 5000.0) // topped A
        // reroute: climb list re-based and climb A dropped, same route key
        val rebasedB = ClimbData(startDistance = 6500.0, length = 1200.0, avgGrade = 7.5, totalElevation = 90.0)
        val rerouted = route.copy(climbs = listOf(rebasedB))
        val s = engine.update(rerouted, 5000.0, trigger) as ClimbUiState.Shown
        assertEquals(1, s.completedCount)
        assertEquals(2, s.totalCount)
        assertTrue(s.climbs.first().done)
        assertEquals(route.climbs[0], s.climbs.first().climb)
        assertEquals(rebasedB, s.climbs.last().climb)

        // riding and topping the re-based climb counts it on top
        engine.update(rerouted, 7000.0, trigger) // riding re-based B
        val done = engine.update(rerouted, 7800.0, trigger) as ClimbUiState.Shown
        assertEquals(2, done.completedCount)
        assertEquals(2, done.totalCount)
    }

    @Test
    fun `active when approaching within trigger distance`() {
        val engine = ClimbEngine()
        val active = activeAt(engine, 1600.0)
        assertEquals(0, active.index)
        assertEquals(400.0, active.distanceToStart, 1e-9)
        assertEquals(2900.0, active.distanceToTop, 1e-9) // 4500 - 1600
        // full climb ascent remains: 280 - 100
        assertEquals(180.0, active.elevationToTop, 1e-9)
    }

    @Test
    fun `values mid-climb`() {
        val engine = ClimbEngine()
        val active = activeAt(engine, 3500.0)
        assertEquals(0.0, active.distanceToStart, 1e-9)
        assertEquals(1000.0, active.distanceToTop, 1e-9)
        // profile at 3500 = 215; top 280
        assertEquals(65.0, active.elevationToTop, 1e-9)
    }

    @Test
    fun `no active after topping, then second climb activates`() {
        val engine = ClimbEngine()
        activeAt(engine, 3000.0)
        assertNull(shownAt(engine, 4501.0).active)
        assertNull(shownAt(engine, 6000.0).active)
        // climb B triggers at 6500
        val b = activeAt(engine, 6600.0)
        assertEquals(1, b.index)
        assertEquals(400.0, b.distanceToStart, 1e-9)
        assertEquals(90.0, b.elevationToTop, 1e-9)
    }

    @Test
    fun `boundary noise does not flap the active climb`() {
        val engine = ClimbEngine(reappearMargin = 100.0)
        activeAt(engine, 1501.0) // active just inside trigger
        // GPS noise drops progress back before boundary -> inactive
        assertNull(shownAt(engine, 1495.0).active)
        // creeping back barely over the boundary must NOT re-activate yet
        assertNull(shownAt(engine, 1505.0).active)
        // but well past the margin it activates again
        activeAt(engine, 1601.0)
    }

    @Test
    fun `reappear margin does not delay first activation`() {
        val engine = ClimbEngine(reappearMargin = 100.0)
        activeAt(engine, 1500.0)
    }

    @Test
    fun `on-climb activation is immediate even after flap`() {
        val engine = ClimbEngine(reappearMargin = 100.0)
        activeAt(engine, 1501.0)
        shownAt(engine, 1495.0) // deactivate
        // jumping straight onto the climb activates regardless of margin
        activeAt(engine, 2001.0)
    }

    @Test
    fun `no profile falls back to proportional elevation`() {
        val noProfile = route.copy(profile = null)
        val engine = ClimbEngine()
        val state = engine.update(noProfile, 3250.0, trigger) as ClimbUiState.Shown
        // halfway up climb A (2000 + 2500/2) -> half of 180
        assertEquals(90.0, state.active!!.elevationToTop, 1e-9)
    }

    @Test
    fun `route change resets state`() {
        val engine = ClimbEngine()
        activeAt(engine, 3000.0)
        assertEquals(ClimbUiState.Hidden, engine.update(null, null, trigger))
        val other = route.copy(routeKey = "other")
        val state = engine.update(other, 3000.0, trigger) as ClimbUiState.Shown
        assertTrue(state.active != null)
    }
}
