package com.hazzus.karooclimber

import com.hazzus.karooclimber.data.ClimbData
import com.hazzus.karooclimber.data.ClimbListCache
import org.junit.Assert.assertEquals
import org.junit.Test

class ClimbListCacheTest {

    private val cache = ClimbListCache()

    private val climbA = ClimbData(startDistance = 2000.0, length = 2500.0, avgGrade = 7.2, totalElevation = 180.0)
    private val climbB = ClimbData(startDistance = 7000.0, length = 1200.0, avgGrade = 7.5, totalElevation = 90.0)

    @Test
    fun `first event for a route populates the cache`() {
        assertEquals(listOf(climbA, climbB), cache.reconcile("route:x", listOf(climbA, climbB)))
    }

    @Test
    fun `shrunk climb list from mid-ride re-emission is ignored`() {
        cache.reconcile("route:x", listOf(climbA, climbB))
        assertEquals(listOf(climbA, climbB), cache.reconcile("route:x", listOf(climbB)))
    }

    @Test
    fun `emptied climb list when rider starts the only climb is ignored`() {
        cache.reconcile("route:x", listOf(climbA))
        assertEquals(listOf(climbA), cache.reconcile("route:x", emptyList()))
    }

    @Test
    fun `larger climb list replaces the cached one`() {
        cache.reconcile("route:x", listOf(climbA))
        assertEquals(listOf(climbA, climbB), cache.reconcile("route:x", listOf(climbA, climbB)))
    }

    @Test
    fun `re-based climbs after reroute replace the cached list`() {
        // Rider joined the route past its start: Karoo re-emits the same climbs
        // with startDistance shifted by the skipped prefix. Same size, new content.
        cache.reconcile("route:x", listOf(climbA, climbB))
        val rebasedA = climbA.copy(startDistance = climbA.startDistance - 1500.0)
        val rebasedB = climbB.copy(startDistance = climbB.startDistance - 1500.0)
        assertEquals(
            listOf(rebasedA, rebasedB),
            cache.reconcile("route:x", listOf(rebasedA, rebasedB)),
        )
    }

    @Test
    fun `climbs skipped by reroute drop out of the list`() {
        cache.reconcile("route:x", listOf(climbA, climbB))
        val rebasedB = climbB.copy(startDistance = 3200.0)
        assertEquals(listOf(rebasedB), cache.reconcile("route:x", listOf(rebasedB)))
    }

    @Test
    fun `shrunk update after a reroute keeps the adopted list`() {
        cache.reconcile("route:x", listOf(climbA, climbB))
        val rebasedA = climbA.copy(startDistance = 500.0)
        val rebasedB = climbB.copy(startDistance = 5500.0)
        cache.reconcile("route:x", listOf(rebasedA, rebasedB))
        // Rider reaches the first re-based climb; Karoo drops it from the emission.
        assertEquals(
            listOf(rebasedA, rebasedB),
            cache.reconcile("route:x", listOf(rebasedB)),
        )
    }

    @Test
    fun `route change resets the cache even to a smaller list`() {
        cache.reconcile("route:x", listOf(climbA, climbB))
        assertEquals(listOf(climbB), cache.reconcile("route:y", listOf(climbB)))
    }

    @Test
    fun `route change to empty list is accepted`() {
        cache.reconcile("route:x", listOf(climbA, climbB))
        assertEquals(emptyList<ClimbData>(), cache.reconcile("route:y", emptyList()))
    }

    @Test
    fun `clear forgets the route so the same key repopulates`() {
        cache.reconcile("route:x", listOf(climbA, climbB))
        cache.clear()
        assertEquals(listOf(climbA), cache.reconcile("route:x", listOf(climbA)))
    }
}
