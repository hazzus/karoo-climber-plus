package com.hazzus.karooclimber.data

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Streams Karoo's native POWER data type and exposes the live instantaneous power in
 * watts, or null when there's no value (no power meter, or not streaming yet). Feeds
 * the climb state builder so completed climbs record an average power.
 */
class PowerStreamRepo(private val karooSystem: KarooSystemService) {

    private val _power = MutableStateFlow<Double?>(null)
    val power: StateFlow<Double?> = _power

    private var consumerId: String? = null

    fun start() {
        if (consumerId != null) return
        consumerId = karooSystem.addConsumer<OnStreamState>(
            OnStreamState.StartStreaming(DataType.Type.POWER),
        ) { event ->
            val values = (event.state as? StreamState.Streaming)?.dataPoint?.values
            _power.value = powerFromValues(values)
        }
    }

    fun stop() {
        consumerId?.let { karooSystem.removeConsumer(it) }
        consumerId = null
        _power.value = null
    }
}

/**
 * Extract the instantaneous power (watts) from a POWER data-point value map, or null
 * when the stream carries no value (no power meter, or a NotAvailable frame). POWER is a
 * single-value stream: prefer the SINGLE field, else the first value present.
 */
fun powerFromValues(values: Map<String, Double>?): Double? {
    if (values.isNullOrEmpty()) return null
    return values[DataType.Field.SINGLE] ?: values.values.firstOrNull()
}
