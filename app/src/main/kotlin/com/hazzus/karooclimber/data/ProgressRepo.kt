package com.hazzus.karooclimber.data

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Streams DISTANCE_TO_DESTINATION and exposes it (meters, null while not
 * streaming). Rider progress along the route = totalDistance − distanceToDestination,
 * computed by the caller which also knows the [RouteData].
 */
class ProgressRepo(private val karooSystem: KarooSystemService) {

    private val _distanceToDestination = MutableStateFlow<Double?>(null)
    val distanceToDestination: StateFlow<Double?> = _distanceToDestination

    private var consumerId: String? = null

    fun start() {
        if (consumerId != null) return
        consumerId = karooSystem.addConsumer<OnStreamState>(
            OnStreamState.StartStreaming(DataType.Type.DISTANCE_TO_DESTINATION),
        ) { event ->
            // The data point carries several fields (distance, NAVIGATION_STATE,
            // ON_ROUTE, ...) — singleValue grabs an arbitrary one, so key explicitly.
            val values = (event.state as? StreamState.Streaming)?.dataPoint?.values
            _distanceToDestination.value =
                values?.get(DataType.Field.DISTANCE_TO_DESTINATION)
                    ?: values?.get(DataType.Field.SINGLE)
        }
    }

    fun stop() {
        consumerId?.let { karooSystem.removeConsumer(it) }
        consumerId = null
        _distanceToDestination.value = null
    }
}

/** Rider progress along the route in meters, or null when unknown. */
fun progressAlongRoute(route: RouteData?, distanceToDestination: Double?): Double? {
    val total = route?.totalDistance ?: return null
    val dtd = distanceToDestination ?: return null
    return (total - dtd).coerceAtLeast(0.0)
}
