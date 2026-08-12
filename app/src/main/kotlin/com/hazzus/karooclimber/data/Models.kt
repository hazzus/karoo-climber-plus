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

/** What the overlay should show right now. */
sealed interface ClimbUiState {
    /** No route -> no chip, nothing on screen. */
    data object Hidden : ClimbUiState

    /** A route is loaded; the chip is always present. */
    data class Shown(
        /** Climbs still ahead of the rider, route order. */
        val climbs: List<ClimbData>,
        /** Rider's distance along the route, meters. */
        val progress: Double,
        /** Whole-route profile (may be null when Karoo didn't send one). */
        val profile: ElevationProfile?,
        /** Set while approaching or on a climb. */
        val active: ActiveClimb?,
        /** Climbs already completed this ride, in completion order. */
        val completed: List<CompletedClimb> = emptyList(),
    ) : ClimbUiState {
        /** Climbs not yet started, in route order. */
        val upcoming: List<ClimbData>
            get() = climbs.filter { progress < it.startDistance }
    }

    data class ActiveClimb(
        val climb: ClimbData,
        /** 0-based climb position for the "n/total" field (number = index + 1). */
        val index: Int,
        /** Total climbs on the route (from the CLIMB stream); null when only the ahead list is known. */
        val total: Int?,
        /** Meters until the climb starts; 0 while on the climb. */
        val distanceToStart: Double,
        /** Meters until the top of the climb (includes approach). */
        val distanceToTop: Double,
        /** Remaining vertical meters to the top from the rider's position on the climb. */
        val elevationToTop: Double,
    )
}
