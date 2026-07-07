package com.hazzus.karooclimber.settings

import io.hammerhead.karooext.models.DataType

/** Where the overlay panel is anchored vertically. */
enum class OverlayAnchor { TOP, BOTTOM }

/** Base display mode for the climb profile. */
enum class BaseMode {
    /** Whole climb; completed part shaded. */
    FULL,

    /** Only the not-yet-completed part of the climb. */
    REMAINING,

    /** Tap cycle includes both: full -> remaining -> preview windows. */
    BOTH,
}

/**
 * Data fields available in the full-screen climb view (2x2 grid).
 *
 * Climb-derived fields have no [dataTypeId]; system fields carry the Karoo
 * data type id they stream from (values arrive via OnStreamState).
 */
enum class ClimbField(val label: String, val dataTypeId: String? = null) {
    // climb-derived
    DIST_TO_TOP("DIST TO TOP"),
    ELEV_TO_TOP("ELEV TO TOP"),
    CLIMB("CLIMB"),
    GRADE("GRADE"),
    AVG_GRADE("AVG GRADE"),
    LENGTH("LENGTH"),

    // Karoo system streams (SI units from the stream)
    SPEED("SPEED", DataType.Type.SPEED),
    AVG_SPEED("AVG SPEED", DataType.Type.AVERAGE_SPEED),
    POWER("POWER", DataType.Type.POWER),
    POWER_3S("POWER 3S", DataType.Type.SMOOTHED_3S_AVERAGE_POWER),
    HEART_RATE("HR", DataType.Type.HEART_RATE),
    CADENCE("CADENCE", DataType.Type.CADENCE),
    ASCENT("ASCENT", DataType.Type.ELEVATION_GAIN),
    DISTANCE("DISTANCE", DataType.Type.DISTANCE),
}

data class Settings(
    val overlayEnabled: Boolean = true,
    val paletteId: String = "karoo",
    val baseMode: BaseMode = BaseMode.FULL,
    /** Tap-to-cycle preview windows, kilometers. */
    val previewWindowsKm: List<Double> = listOf(2.0, 5.0, 10.0),
    /** Overlay appears this many meters before the climb starts. */
    val triggerDistanceM: Double = 500.0,
    val anchor: OverlayAnchor = OverlayAnchor.TOP,
    /** Panel height as % of screen height. */
    val heightPercent: Int = 40,
    /** Overlay opacity 75..100. */
    val opacityPercent: Int = 95,
    /** Force-show the overlay with DemoData (on-desk testing). */
    val demoMode: Boolean = false,
    /** The four data fields shown in the full-screen climb view. */
    val fullFields: List<ClimbField> = listOf(
        ClimbField.DIST_TO_TOP,
        ClimbField.ELEV_TO_TOP,
        ClimbField.CLIMB,
        ClimbField.GRADE,
    ),
)
