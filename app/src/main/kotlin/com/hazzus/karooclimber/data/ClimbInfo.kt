package com.hazzus.karooclimber.data

/**
 * Minimal live climb state read straight from Karoo's native `CLIMB` /
 * `CLIMB_NUMBER` data streams — the same source the stock climber uses, so
 * detection matches it by construction. A null [ClimbInfo] (upstream) means
 * "not on a climb": the overlay hides.
 *
 * All distances/elevations in meters, as delivered by karoo-ext; any field can
 * be null if Karoo didn't include it in the data point.
 */
data class ClimbInfo(
    val distanceToTop: Double?,
    val elevationToTop: Double?,
    val distanceFromBottom: Double?,
    val elevationFromBottom: Double?,
    val climbElevation: Double?,
    val climbNumber: Int?,
    val totalClimbs: Int?,
)
