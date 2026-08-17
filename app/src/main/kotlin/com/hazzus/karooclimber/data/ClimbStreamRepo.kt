package com.hazzus.karooclimber.data

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * Streams Karoo's native CLIMB data type and exposes the live [ClimbInfo], or null
 * when the rider isn't on a climb. This is the same source the stock climber uses,
 * so "on a climb" matches it by construction: a non-null value means a climb has
 * started; null means no active climb (off a climb, or not streaming yet).
 */
class ClimbStreamRepo(private val karooSystem: KarooSystemService) {

    private val _climbInfo = MutableStateFlow<ClimbInfo?>(null)
    val climbInfo: StateFlow<ClimbInfo?> = _climbInfo

    private var consumerId: String? = null

    fun start() {
        if (consumerId != null) return
        consumerId = karooSystem.addConsumer<OnStreamState>(
            OnStreamState.StartStreaming(DataType.Type.CLIMB),
        ) { event ->
            val values = (event.state as? StreamState.Streaming)?.dataPoint?.values
            _climbInfo.value = climbInfoFromValues(values)
        }
    }

    fun stop() {
        consumerId?.let { karooSystem.removeConsumer(it) }
        consumerId = null
        _climbInfo.value = null
    }
}

/**
 * Parse a CLIMB data-point value map into [ClimbInfo], or null when it carries no
 * active-climb signal. Keys are karoo-ext FIELD_* ids and every field is optional;
 * climb number/total arrive as doubles and are rounded to ints. "On a climb" needs
 * a positive climb length (distance-from-bottom + distance-to-top) — Karoo reports
 * those only while climbing, and this also rejects a zeroed-out off-climb frame.
 *
 * NOTE (device-verify): confirm the single CLIMB stream carries all these fields and
 * how it represents "off a climb" (NotAvailable vs empty vs zeroed); adjust the
 * active-climb test here if it differs.
 */
fun climbInfoFromValues(values: Map<String, Double>?): ClimbInfo? {
    if (values.isNullOrEmpty()) return null
    val distanceToTop = values[DataType.Field.DISTANCE_TO_TOP]
    val distanceFromBottom = values[DataType.Field.DISTANCE_FROM_BOTTOM]
    // No positive climb geometry -> the rider isn't on a climb (absent or zeroed).
    if ((distanceToTop ?: 0.0) + (distanceFromBottom ?: 0.0) <= 0.0) return null
    return ClimbInfo(
        distanceToTop = distanceToTop,
        elevationToTop = values[DataType.Field.ELEVATION_TO_TOP],
        distanceFromBottom = distanceFromBottom,
        elevationFromBottom = values[DataType.Field.ELEVATION_FROM_BOTTOM],
        climbElevation = values[DataType.Field.CLIMB_ELEVATION],
        climbNumber = values[DataType.Field.CLIMB_NUMBER]?.roundToInt(),
        totalClimbs = values[DataType.Field.TOTAL_CLIMBS]?.roundToInt(),
    )
}
