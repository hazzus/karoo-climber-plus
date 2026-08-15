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
    /**
     * Karoo's `CLIMB_ELEVATION` field. NOT the climb's total gain — observed on real rides to
     * be the rider's current absolute altitude (e.g. 446 m at the foot of a ~68 m climb). Do
     * not use it as gain; the climb's gain is [elevationFromBottom] + [elevationToTop].
     */
    val climbElevation: Double?,
    val climbNumber: Int?,
    val totalClimbs: Int?,
)
