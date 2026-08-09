package com.hazzus.karooclimber.data

/**
 * Builds the overlay [ClimbUiState] from the fresh navigation route (upcoming climbs)
 * and Karoo's native CLIMB stream ([ClimbInfo], the live active climb).
 *
 * Detection is the stream: a non-null [ClimbInfo] means a climb has started (matches
 * the stock climber by construction). On that transition we snapshot the climb's route
 * span **once** from the current DTD [progress] (climbStart = progress − distanceFromBottom)
 * and freeze it — so the silhouette is stable and no longer trusts per-frame DTD.
 * Thereafter the rider is placed from the stream's distanceFromBottom/distanceToTop.
 * Upcoming climbs and the approach state come from the navigation climbs list + progress.
 * No completed-climb tracking: only climbs ahead.
 *
 * With no route loaded, a live CLIMB stream is still surfaced as a "predicted" climb
 * (see [routelessClimb]): coordinates relative to the climb, no upcoming list, and a
 * null profile so the view draws a constant-grade wedge.
 */
class ClimbStateBuilder {

    private var routeKey: String? = null
    private var frozen: ClimbData? = null
    private var frozenNumber: Int? = null

    /** True when [frozen] was built route-less (relative coords), so onClimb re-snapshots. */
    private var frozenRouteless = false

    /** Drop the frozen active climb (e.g. a new ride). Route changes reset it inline. */
    fun reset() {
        routeKey = null
        clearFrozen()
    }

    private fun clearFrozen() {
        frozen = null
        frozenNumber = null
        frozenRouteless = false
    }

    fun build(
        route: RouteData?,
        progress: Double?,
        info: ClimbInfo?,
        triggerDistance: Double,
    ): ClimbUiState {
        // No navigation: still surface a predicted climb straight from the CLIMB stream.
        if (route == null) {
            if (info == null) {
                clearFrozen()
                return ClimbUiState.Hidden
            }
            return routelessClimb(info)?.let { (active, riderPos) ->
                ClimbUiState.Shown(
                    climbs = emptyList(),
                    progress = riderPos,
                    profile = null,
                    active = active,
                )
            } ?: ClimbUiState.Hidden
        }

        if (route.routeKey != routeKey) {
            routeKey = route.routeKey
            clearFrozen()
        }

        val sorted = route.climbs.sortedBy { it.startDistance }

        // On a climb: the stream drives everything once anchored.
        if (info != null) {
            onClimb(route, progress, info)?.let { (active, riderPos) ->
                val ahead = sorted.filter { riderPos < it.endDistance }
                return ClimbUiState.Shown(
                    climbs = ahead,
                    progress = riderPos,
                    profile = route.profile,
                    active = active,
                )
            }
            // Couldn't anchor yet (no DTD frame) -> fall through and wait.
        } else {
            clearFrozen()
        }

        // Not on a climb: show the climbs ahead (+ approach), or hide if none.
        if (progress == null) return ClimbUiState.Hidden
        val ahead = sorted.filter { progress < it.endDistance }
        if (ahead.isEmpty()) return ClimbUiState.Hidden
        return ClimbUiState.Shown(
            climbs = ahead,
            progress = progress,
            profile = route.profile,
            active = approaching(route, ahead, progress, triggerDistance),
        )
    }

    /**
     * Build the active climb from the stream, snapshotting its frozen route span on the
     * first frame (or when the stream moves to a new climb number). Returns null while we
     * still lack a DTD [progress] to anchor the very first snapshot.
     */
    private fun onClimb(
        route: RouteData,
        progress: Double?,
        info: ClimbInfo,
    ): Pair<ClimbUiState.ActiveClimb, Double>? {
        val distToTop = info.distanceToTop
        val distFromBottom = info.distanceFromBottom
        val number = info.climbNumber

        if (frozen == null || frozenRouteless || (number != null && number != frozenNumber)) {
            val pos = progress ?: return null // need DTD once to anchor the snapshot
            val length = (distFromBottom ?: 0.0) + (distToTop ?: 0.0)
            if (length <= 0.0) return null
            val climbStart = (pos - (distFromBottom ?: 0.0)).coerceAtLeast(0.0)
            val totalElevation = info.climbElevation
                ?: ((info.elevationFromBottom ?: 0.0) + (info.elevationToTop ?: 0.0))
            frozen = ClimbData(
                startDistance = climbStart,
                length = length,
                avgGrade = totalElevation / length * 100.0,
                totalElevation = totalElevation,
            )
            frozenNumber = number
            frozenRouteless = false
        }
        val climb = frozen!!

        // Rider position along the route, stream-driven (DTD not trusted per-frame).
        val riderPos = when {
            distFromBottom != null -> climb.startDistance + distFromBottom
            distToTop != null -> climb.endDistance - distToTop
            else -> climb.startDistance
        }.coerceIn(climb.startDistance, climb.endDistance)

        val active = ClimbUiState.ActiveClimb(
            climb = climb,
            index = (number?.minus(1))?.coerceAtLeast(0) ?: 0,
            total = info.totalClimbs,
            distanceToStart = 0.0,
            distanceToTop = distToTop ?: (climb.endDistance - riderPos).coerceAtLeast(0.0),
            elevationToTop = info.elevationToTop
                ?: elevationToTop(route.profile, climb, riderPos),
        )
        return active to riderPos
    }

    /**
     * A predicted climb with no route loaded: built straight from the CLIMB stream in
     * climb-relative coordinates (start = 0), no upcoming list, null profile (the view
     * draws a constant-grade wedge). Frozen like [onClimb] for a stable silhouette.
     */
    private fun routelessClimb(info: ClimbInfo): Pair<ClimbUiState.ActiveClimb, Double>? {
        val distToTop = info.distanceToTop
        val distFromBottom = info.distanceFromBottom
        val number = info.climbNumber

        if (frozen == null || !frozenRouteless || (number != null && number != frozenNumber)) {
            val length = (distFromBottom ?: 0.0) + (distToTop ?: 0.0)
            if (length <= 0.0) return null
            val totalElevation = info.climbElevation
                ?: ((info.elevationFromBottom ?: 0.0) + (info.elevationToTop ?: 0.0))
            frozen = ClimbData(
                startDistance = 0.0,
                length = length,
                avgGrade = totalElevation / length * 100.0,
                totalElevation = totalElevation,
            )
            frozenNumber = number
            frozenRouteless = true
        }
        val climb = frozen!!

        val riderPos = when {
            distFromBottom != null -> distFromBottom
            distToTop != null -> climb.length - distToTop
            else -> 0.0
        }.coerceIn(0.0, climb.length)

        val active = ClimbUiState.ActiveClimb(
            climb = climb,
            index = (number?.minus(1))?.coerceAtLeast(0) ?: 0,
            total = info.totalClimbs,
            distanceToStart = 0.0,
            distanceToTop = distToTop ?: (climb.length - riderPos).coerceAtLeast(0.0),
            elevationToTop = info.elevationToTop
                ?: elevationToTop(null, climb, riderPos),
        )
        return active to riderPos
    }

    /** Approach state: within the trigger window of the next climb but not yet on it. */
    private fun approaching(
        route: RouteData,
        ahead: List<ClimbData>,
        progress: Double,
        triggerDistance: Double,
    ): ClimbUiState.ActiveClimb? {
        val next = ahead.firstOrNull() ?: return null
        if (progress < next.startDistance - triggerDistance || progress >= next.startDistance) {
            return null
        }
        return ClimbUiState.ActiveClimb(
            climb = next,
            index = 0,
            total = ahead.size,
            distanceToStart = (next.startDistance - progress).coerceAtLeast(0.0),
            distanceToTop = (next.endDistance - progress).coerceAtLeast(0.0),
            elevationToTop = elevationToTop(route.profile, next, progress),
        )
    }

    private fun elevationToTop(profile: ElevationProfile?, climb: ClimbData, pos: Double): Double {
        val onClimbPos = pos.coerceIn(climb.startDistance, climb.endDistance)
        return if (profile != null) {
            (profile.elevationAt(climb.endDistance) - profile.elevationAt(onClimbPos))
                .coerceAtLeast(0.0)
        } else {
            val remainingFraction =
                ((climb.endDistance - onClimbPos) / climb.length).coerceIn(0.0, 1.0)
            climb.totalElevation * remainingFraction
        }
    }
}
