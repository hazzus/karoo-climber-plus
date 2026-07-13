package com.hazzus.karooclimber.data

/** Normalized climb from Karoo's navigation state. All distances in meters. */
data class ClimbData(
    val startDistance: Double,
    val length: Double,
    val avgGrade: Double,
    val totalElevation: Double,
) {
    val endDistance: Double get() = startDistance + length
}

/** Normalized route from either NavigatingRoute or NavigatingToDestination. */
data class RouteData(
    /** Total route length in meters; null for destination navigation (use profile). */
    val routeDistance: Double?,
    val profile: ElevationProfile?,
    val climbs: List<ClimbData>,
    /** Identity key to detect route changes. */
    val routeKey: String,
) {
    /** Best-known total distance for progress computation. */
    val totalDistance: Double? get() = routeDistance ?: profile?.totalDistance
}

/**
 * A climb on this ride's list. Done entries keep the data recorded when the
 * rider topped them — a reroute may have re-based route coordinates since, so
 * their distances must not be compared against current progress.
 */
data class ClimbEntry(
    val climb: ClimbData,
    val done: Boolean,
)

/** What the overlay should show right now. */
sealed interface ClimbUiState {
    /** No route -> no chip, nothing on screen. */
    data object Hidden : ClimbUiState

    /** A route is loaded; the chip is always present. */
    data class Shown(
        /** Topped climbs first (frozen data), then climbs still ahead, route order. */
        val climbs: List<ClimbEntry>,
        /** Rider's distance along the route, meters. */
        val progress: Double,
        /** Whole-route profile (may be null when Karoo didn't send one). */
        val profile: ElevationProfile?,
        /** Set while approaching or on a climb (within the trigger window). */
        val active: ActiveClimb?,
    ) : ClimbUiState {
        val completedCount: Int get() = climbs.count { it.done }
        val totalCount: Int get() = climbs.size

        /** Climbs not yet started, in route order. */
        val upcoming: List<ClimbData>
            get() = climbs.filter { !it.done && progress < it.climb.startDistance }.map { it.climb }
    }

    data class ActiveClimb(
        val climb: ClimbData,
        /** Position in [Shown.climbs] (0-based), i.e. counts completed climbs too. */
        val index: Int,
        /** Meters until the climb starts; 0 while on the climb. */
        val distanceToStart: Double,
        /** Meters until the top of the climb (includes approach). */
        val distanceToTop: Double,
        /** Remaining vertical meters to the top from the rider's position on the climb. */
        val elevationToTop: Double,
    )
}
