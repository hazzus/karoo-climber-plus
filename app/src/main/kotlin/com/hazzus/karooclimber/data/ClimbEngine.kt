package com.hazzus.karooclimber.data

/**
 * Pure logic producing the overlay state. With a route that has climbs the state
 * is always [ClimbUiState.Shown] (permanent chip); [ClimbUiState.Shown.active] is
 * set while approaching/on a climb.
 *
 * Holds per-ride state:
 *  - stickiness so trigger-boundary GPS noise doesn't flap the active climb;
 *  - the climbs the rider actually topped. Karoo climbs carry no id and a
 *    reroute re-bases their distances (and drops passed ones from the list),
 *    so completions are recorded separately, with the climb data frozen at
 *    completion time, and only for climbs the rider was observed on — climbs
 *    skipped by joining the route mid-way never count. A topped climb stays
 *    topped for the rest of the ride: completions survive a reroute, a
 *    route<->destination switch (routeKey change) and momentary null frames,
 *    and are cleared only by [reset] on a real ride end.
 */
class ClimbEngine(
    /** Extra meters past the trigger boundary required to re-activate after a drop-out. */
    private val reappearMargin: Double = 100.0,
) {
    private var routeKey: String? = null
    private var shownKey: String? = null
    private var recentlyHiddenKey: String? = null

    /** Climb currently under the rider; completion is recorded when leaving it past the top. */
    private var riding: ClimbData? = null
    private val completed = mutableListOf<ClimbData>()

    /**
     * Forget all per-ride state, completed climbs included. Only a real ride end
     * (or the demo loop wrapping) calls this — a reroute or a momentary data gap
     * must not, so a climb topped earlier in the ride is never un-counted.
     */
    fun reset() {
        routeKey = null
        shownKey = null
        recentlyHiddenKey = null
        riding = null
        completed.clear()
    }

    fun update(route: RouteData?, progress: Double?, triggerDistance: Double): ClimbUiState {
        // A momentary gap — reroute in progress, a missing distance-to-destination
        // frame — must not wipe completions; only a real ride end does (see reset()
        // callers). Hide for this frame but keep every per-ride value intact.
        if (route == null || progress == null) return ClimbUiState.Hidden

        if (route.routeKey != routeKey) {
            // New route mid-ride (reroute, or a route<->destination switch). The
            // climbs ahead are re-based, so drop the per-climb tracking — but a
            // climb already topped stays topped, so completed carries over untouched.
            routeKey = route.routeKey
            shownKey = null
            recentlyHiddenKey = null
            riding = null
        }
        if (route.climbs.isEmpty() && completed.isEmpty()) return ClimbUiState.Hidden

        val sorted = route.climbs.sortedBy { it.startDistance }
        recordCompletion(sorted, progress)

        // Climbs still to ride: skipped-behind ones (never ridden, progress already
        // past their top) drop out here — a reroute took them off the rider's path.
        val ahead = sorted.filter { progress < it.endDistance }
        val active = selectActive(route, ahead, progress, triggerDistance)

        return ClimbUiState.Shown(
            climbs = completed.map { ClimbEntry(it, done = true) } +
                ahead.map { ClimbEntry(it, done = false) },
            progress = progress,
            profile = route.profile,
            active = active,
        )
    }

    private fun recordCompletion(sorted: List<ClimbData>, progress: Double) {
        val onClimb = sorted.firstOrNull { progress >= it.startDistance && progress < it.endDistance }
        val prev = riding
        if (prev != null && prev != onClimb && progress >= prev.endDistance && prev !in completed) {
            completed += prev
        }
        riding = onClimb
    }

    private fun selectActive(
        route: RouteData,
        ahead: List<ClimbData>,
        progress: Double,
        triggerDistance: Double,
    ): ClimbUiState.ActiveClimb? {
        // First climb not yet topped whose trigger window has been entered.
        val aheadIndex = ahead.indexOfFirst { progress >= it.startDistance - triggerDistance }
        if (aheadIndex < 0) {
            if (shownKey != null) {
                recentlyHiddenKey = shownKey
                shownKey = null
            }
            return null
        }

        val climb = ahead[aheadIndex]
        val key = "${route.routeKey}:${climb.startDistance}"

        // Hysteresis: this climb just flapped off at the trigger boundary — require
        // progress a margin beyond the boundary (or actual climb start) to re-activate.
        if (shownKey != key && recentlyHiddenKey == key &&
            progress < climb.startDistance - triggerDistance + reappearMargin &&
            progress < climb.startDistance
        ) {
            return null
        }
        shownKey = key
        recentlyHiddenKey = null

        return ClimbUiState.ActiveClimb(
            climb = climb,
            index = completed.size + aheadIndex,
            distanceToStart = (climb.startDistance - progress).coerceAtLeast(0.0),
            distanceToTop = (climb.endDistance - progress).coerceAtLeast(0.0),
            elevationToTop = elevationToTop(route.profile, climb, progress),
        )
    }

    private fun elevationToTop(profile: ElevationProfile?, climb: ClimbData, progress: Double): Double {
        val onClimbProgress = progress.coerceIn(climb.startDistance, climb.endDistance)
        return if (profile != null) {
            (profile.elevationAt(climb.endDistance) - profile.elevationAt(onClimbProgress))
                .coerceAtLeast(0.0)
        } else {
            // no profile: assume constant grade
            val remainingFraction =
                ((climb.endDistance - onClimbProgress) / climb.length).coerceIn(0.0, 1.0)
            climb.totalElevation * remainingFraction
        }
    }
}
