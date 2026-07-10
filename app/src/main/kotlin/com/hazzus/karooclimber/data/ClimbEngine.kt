package com.hazzus.karooclimber.data

/**
 * Pure logic producing the overlay state. With a route that has climbs the state
 * is always [ClimbUiState.Shown] (permanent chip); [ClimbUiState.Shown.active] is
 * set while approaching/on a climb. Holds only stickiness state so
 * trigger-boundary GPS noise doesn't flap the active climb on and off.
 */
class ClimbEngine(
    /** Extra meters past the trigger boundary required to re-activate after a drop-out. */
    private val reappearMargin: Double = 100.0,
) {
    private var shownKey: String? = null
    private var recentlyHiddenKey: String? = null

    fun update(route: RouteData?, progress: Double?, triggerDistance: Double): ClimbUiState {
        if (route == null || progress == null || route.climbs.isEmpty()) {
            shownKey = null
            recentlyHiddenKey = null
            return ClimbUiState.Hidden
        }

        val sorted = route.climbs.sortedBy { it.startDistance }
        val completed = sorted.count { progress >= it.endDistance }
        val active = selectActive(route, sorted, progress, triggerDistance)

        return ClimbUiState.Shown(
            climbs = sorted,
            progress = progress,
            profile = route.profile,
            completedCount = completed,
            active = active,
        )
    }

    private fun selectActive(
        route: RouteData,
        sorted: List<ClimbData>,
        progress: Double,
        triggerDistance: Double,
    ): ClimbUiState.ActiveClimb? {
        // First climb not yet topped whose trigger window has been entered.
        val candidateIndex = sorted.indexOfFirst { climb ->
            progress < climb.endDistance && progress >= climb.startDistance - triggerDistance
        }
        if (candidateIndex < 0) {
            if (shownKey != null) {
                recentlyHiddenKey = shownKey
                shownKey = null
            }
            return null
        }

        val climb = sorted[candidateIndex]
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
            index = candidateIndex,
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
