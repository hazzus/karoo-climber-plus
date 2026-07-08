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

/** Grouping for the field-picker menu. */
enum class FieldCategory(val label: String) {
    CLIMB("Climb"),
    SPEED("Speed & distance"),
    POWER("Power"),
    HEART_RATE("Heart rate"),
    CADENCE("Cadence"),
    ELEVATION("Elevation"),
    TIME("Time"),
    NAVIGATION("Navigation"),
    LAP("Lap"),
    LAST_LAP("Last lap"),
    SHIFTING("Shifting"),
    SEGMENT("Live segments"),
    WORKOUT("Workout"),
    BODY("Body temp"),
    SUN("Sun & weather"),
    DEVICE("Device"),
    SUSPENSION("Suspension"),
    EBIKE("E-bike"),
    KI2("Ki2 (Di2)"),
}

/** Ki2 extension stream prefix (karoo-ext TYPE_EXT::<extension>::<typeId>). */
private const val KI2 = "TYPE_EXT::ki2::"

/**
 * How a system stream's SI value is rendered in a grid cell
 * (unitless by design — see native-Climber parity note in CLAUDE.md).
 */
enum class FieldFormat {
    /** Computed from the climb engine, not a system stream. */
    DERIVED,

    /** m/s -> km/h or mph, one decimal. */
    SPEED,

    /** m/s -> m/h (VAM) or ft/h, integer. */
    VERTICAL_SPEED,

    /** meters -> km/m or mi/ft via the shared distance formatter. */
    DISTANCE,

    /** meters -> m or ft. */
    ELEVATION,

    /** rounded integer (W, bpm, rpm, counts, zones, degrees). */
    INTEGER,

    /** one decimal. */
    NUMBER,

    /** two decimals (IF, VI, W/kg). */
    NUMBER2,

    /** percentage as rounded integer. */
    PERCENT,

    /** seconds -> m:ss / h:mm:ss (negative-aware for segment deltas). */
    DURATION,

    /** epoch timestamp -> local HH:mm. */
    CLOCK,

    /** °C, converted to °F for imperial. */
    TEMPERATURE,

    /** pascals -> bar (metric) or psi (imperial). */
    PRESSURE,

    /** combined front x rear gear from the multi-field shifting stream. */
    GEARS,
}

/**
 * Data fields available in the full-screen climb view (2x2 grid).
 *
 * Climb-derived fields have no [dataTypeId]; system fields carry the Karoo
 * data type id they stream from (values arrive via OnStreamState).
 * Entry names are persisted in DataStore — never rename existing ones.
 */
enum class ClimbField(
    val label: String,
    val category: FieldCategory,
    val format: FieldFormat,
    val dataTypeId: String? = null,
    /** Second stream for fields combined from two data types (GEARS front+rear). */
    val extraDataTypeId: String? = null,
    /** Short label for the fullscreen grid cell when [label] is too long for it. */
    cellLabel: String? = null,
) {
    // climb-derived (this extension's own engine)
    DIST_TO_TOP("DIST TO TOP", FieldCategory.CLIMB, FieldFormat.DERIVED),
    ELEV_TO_TOP("ELEV TO TOP", FieldCategory.CLIMB, FieldFormat.DERIVED),
    CLIMB("CLIMB", FieldCategory.CLIMB, FieldFormat.DERIVED),
    GRADE("GRADE", FieldCategory.CLIMB, FieldFormat.DERIVED),
    AVG_GRADE("AVG GRADE", FieldCategory.CLIMB, FieldFormat.DERIVED),
    LENGTH("LENGTH", FieldCategory.CLIMB, FieldFormat.DERIVED),

    // Karoo's native climber streams ("K" prefix to tell them apart)
    SYS_DIST_TO_TOP("K DIST TO TOP", FieldCategory.CLIMB, FieldFormat.DISTANCE, DataType.Type.DISTANCE_TO_TOP),
    SYS_ELEV_TO_TOP("K ELEV TO TOP", FieldCategory.CLIMB, FieldFormat.ELEVATION, DataType.Type.ELEVATION_TO_TOP),
    SYS_DIST_FROM_BOTTOM("K FROM BOTTOM", FieldCategory.CLIMB, FieldFormat.DISTANCE, DataType.Type.DISTANCE_FROM_BOTTOM),
    SYS_ELEV_FROM_BOTTOM("K ELEV CLIMBED", FieldCategory.CLIMB, FieldFormat.ELEVATION, DataType.Type.ELEVATION_FROM_BOTTOM),
    SYS_CLIMB_NUMBER("K CLIMB NO", FieldCategory.CLIMB, FieldFormat.INTEGER, DataType.Type.CLIMB_NUMBER),
    SYS_CLIMB("K CLIMB", FieldCategory.CLIMB, FieldFormat.INTEGER, DataType.Type.CLIMB),

    // speed
    SPEED("SPEED", FieldCategory.SPEED, FieldFormat.SPEED, DataType.Type.SPEED),
    AVG_SPEED("AVG SPEED", FieldCategory.SPEED, FieldFormat.SPEED, DataType.Type.AVERAGE_SPEED),
    MAX_SPEED("MAX SPEED", FieldCategory.SPEED, FieldFormat.SPEED, DataType.Type.MAX_SPEED),
    SPEED_3S("SPEED 3S", FieldCategory.SPEED, FieldFormat.SPEED, DataType.Type.SMOOTHED_3S_AVERAGE_SPEED),
    SPEED_5S("SPEED 5S", FieldCategory.SPEED, FieldFormat.SPEED, DataType.Type.SMOOTHED_5S_AVERAGE_SPEED),
    SPEED_10S("SPEED 10S", FieldCategory.SPEED, FieldFormat.SPEED, DataType.Type.SMOOTHED_10S_AVERAGE_SPEED),
    DISTANCE("DISTANCE", FieldCategory.SPEED, FieldFormat.DISTANCE, DataType.Type.DISTANCE),

    // power
    POWER("POWER", FieldCategory.POWER, FieldFormat.INTEGER, DataType.Type.POWER),
    POWER_3S("POWER 3S", FieldCategory.POWER, FieldFormat.INTEGER, DataType.Type.SMOOTHED_3S_AVERAGE_POWER),
    POWER_5S("POWER 5S", FieldCategory.POWER, FieldFormat.INTEGER, DataType.Type.SMOOTHED_5S_AVERAGE_POWER),
    POWER_10S("POWER 10S", FieldCategory.POWER, FieldFormat.INTEGER, DataType.Type.SMOOTHED_10S_AVERAGE_POWER),
    POWER_30S("POWER 30S", FieldCategory.POWER, FieldFormat.INTEGER, DataType.Type.SMOOTHED_30S_AVERAGE_POWER),
    POWER_20M("POWER 20M", FieldCategory.POWER, FieldFormat.INTEGER, DataType.Type.SMOOTHED_20M_AVERAGE_POWER),
    POWER_1H("POWER 1H", FieldCategory.POWER, FieldFormat.INTEGER, DataType.Type.SMOOTHED_1HR_AVERAGE_POWER),
    AVG_POWER("AVG POWER", FieldCategory.POWER, FieldFormat.INTEGER, DataType.Type.AVERAGE_POWER),
    MAX_POWER("MAX POWER", FieldCategory.POWER, FieldFormat.INTEGER, DataType.Type.MAX_POWER),
    NORMALIZED_POWER("NP", FieldCategory.POWER, FieldFormat.INTEGER, DataType.Type.NORMALIZED_POWER),
    POWER_ZONE("PWR ZONE", FieldCategory.POWER, FieldFormat.INTEGER, DataType.Type.POWER_ZONE),
    PERCENT_FTP("% FTP", FieldCategory.POWER, FieldFormat.PERCENT, DataType.Type.PERCENT_MAX_FTP),
    INTENSITY_FACTOR("IF", FieldCategory.POWER, FieldFormat.NUMBER2, DataType.Type.INTENSITY_FACTOR),
    TSS("TSS", FieldCategory.POWER, FieldFormat.NUMBER, DataType.Type.TRAINING_STRESS_SCORE),
    VARIABILITY_INDEX("VI", FieldCategory.POWER, FieldFormat.NUMBER2, DataType.Type.VARIABILITY_INDEX),
    POWER_TO_WEIGHT("W/KG", FieldCategory.POWER, FieldFormat.NUMBER2, DataType.Type.POWER_TO_WEIGHT),
    BALANCE("BALANCE", FieldCategory.POWER, FieldFormat.NUMBER, DataType.Type.PEDAL_POWER_BALANCE),
    AVG_BALANCE("AVG BAL", FieldCategory.POWER, FieldFormat.NUMBER, DataType.Type.AVERAGE_PEDAL_POWER_BALANCE),
    BALANCE_3S("BAL 3S", FieldCategory.POWER, FieldFormat.NUMBER, DataType.Type.SMOOTHED_3S_AVERAGE_PEDAL_POWER_BALANCE),
    BALANCE_5S("BAL 5S", FieldCategory.POWER, FieldFormat.NUMBER, DataType.Type.SMOOTHED_5S_AVERAGE_PEDAL_POWER_BALANCE),
    BALANCE_10S("BAL 10S", FieldCategory.POWER, FieldFormat.NUMBER, DataType.Type.SMOOTHED_10S_AVERAGE_PEDAL_POWER_BALANCE),
    BALANCE_30S("BAL 30S", FieldCategory.POWER, FieldFormat.NUMBER, DataType.Type.SMOOTHED_30S_AVERAGE_PEDAL_POWER_BALANCE),
    PEDAL_SMOOTHNESS("SMOOTHNESS", FieldCategory.POWER, FieldFormat.PERCENT, DataType.Type.PEDAL_SMOOTHNESS),
    TORQUE_EFFECTIVENESS("TORQ EFF", FieldCategory.POWER, FieldFormat.PERCENT, DataType.Type.TORQUE_EFFECTIVENESS),
    TORQUE("TORQUE", FieldCategory.POWER, FieldFormat.NUMBER, DataType.Type.TORQUE),
    TORQUE_3S("TORQUE 3S", FieldCategory.POWER, FieldFormat.NUMBER, DataType.Type.SMOOTHED_3S_AVERAGE_TORQUE),
    AVG_TORQUE("AVG TORQUE", FieldCategory.POWER, FieldFormat.NUMBER, DataType.Type.AVERAGE_TORQUE),
    MAX_TORQUE("MAX TORQUE", FieldCategory.POWER, FieldFormat.NUMBER, DataType.Type.MAX_TORQUE),
    ENERGY("KJ", FieldCategory.POWER, FieldFormat.INTEGER, DataType.Type.ENERGY_OUTPUT),
    CALORIES("KCAL", FieldCategory.POWER, FieldFormat.INTEGER, DataType.Type.CALORIES),
    CALORIES_PER_HOUR("KCAL/H", FieldCategory.POWER, FieldFormat.INTEGER, DataType.Type.CALORIES_PER_HOUR),

    // heart rate
    HEART_RATE("HR", FieldCategory.HEART_RATE, FieldFormat.INTEGER, DataType.Type.HEART_RATE),
    HR_ZONE("HR ZONE", FieldCategory.HEART_RATE, FieldFormat.INTEGER, DataType.Type.HR_ZONE),
    AVG_HR("AVG HR", FieldCategory.HEART_RATE, FieldFormat.INTEGER, DataType.Type.AVERAGE_HR),
    MAX_HR("MAX HR", FieldCategory.HEART_RATE, FieldFormat.INTEGER, DataType.Type.MAX_HR),
    PERCENT_MAX_HR("% MAX HR", FieldCategory.HEART_RATE, FieldFormat.PERCENT, DataType.Type.PERCENT_MAX_HR),
    PERCENT_HRR("% HRR", FieldCategory.HEART_RATE, FieldFormat.PERCENT, DataType.Type.PERCENT_HRR),
    AVG_PERCENT_HRR("AVG % HRR", FieldCategory.HEART_RATE, FieldFormat.PERCENT, DataType.Type.AVERAGE_PERCENT_HRR),

    // cadence
    CADENCE("CADENCE", FieldCategory.CADENCE, FieldFormat.INTEGER, DataType.Type.CADENCE),
    AVG_CADENCE("AVG CAD", FieldCategory.CADENCE, FieldFormat.INTEGER, DataType.Type.AVERAGE_CADENCE),
    MAX_CADENCE("MAX CAD", FieldCategory.CADENCE, FieldFormat.INTEGER, DataType.Type.MAX_CADENCE),
    CADENCE_3S("CAD 3S", FieldCategory.CADENCE, FieldFormat.INTEGER, DataType.Type.SMOOTHED_3S_AVERAGE_CADENCE),
    CADENCE_5S("CAD 5S", FieldCategory.CADENCE, FieldFormat.INTEGER, DataType.Type.SMOOTHED_5S_AVERAGE_CADENCE),
    CADENCE_10S("CAD 10S", FieldCategory.CADENCE, FieldFormat.INTEGER, DataType.Type.SMOOTHED_10S_AVERAGE_CADENCE),

    // elevation
    ASCENT("ASCENT", FieldCategory.ELEVATION, FieldFormat.ELEVATION, DataType.Type.ELEVATION_GAIN),
    DESCENT("DESCENT", FieldCategory.ELEVATION, FieldFormat.ELEVATION, DataType.Type.ELEVATION_LOSS),
    SYS_GRADE("K GRADE", FieldCategory.ELEVATION, FieldFormat.NUMBER, DataType.Type.ELEVATION_GRADE),
    VAM("VAM", FieldCategory.ELEVATION, FieldFormat.VERTICAL_SPEED, DataType.Type.VERTICAL_SPEED),
    AVG_VAM("AVG VAM", FieldCategory.ELEVATION, FieldFormat.VERTICAL_SPEED, DataType.Type.AVERAGE_VERTICAL_SPEED),
    VAM_30S("VAM 30S", FieldCategory.ELEVATION, FieldFormat.VERTICAL_SPEED, DataType.Type.AVERAGE_VERTICAL_SPEED_30S),
    MIN_ELEVATION("MIN ELEV", FieldCategory.ELEVATION, FieldFormat.ELEVATION, DataType.Type.MIN_ELEVATION),
    MAX_ELEVATION("MAX ELEV", FieldCategory.ELEVATION, FieldFormat.ELEVATION, DataType.Type.MAX_ELEVATION),
    AVG_ELEVATION("AVG ELEV", FieldCategory.ELEVATION, FieldFormat.ELEVATION, DataType.Type.AVERAGE_ELEVATION),
    ELEV_CORRECTION("ELEV CORR", FieldCategory.ELEVATION, FieldFormat.ELEVATION, DataType.Type.PRESSURE_ELEVATION_CORRECTION),

    // time
    RIDE_TIME("RIDE TIME", FieldCategory.TIME, FieldFormat.DURATION, DataType.Type.RIDE_TIME),
    ELAPSED_TIME("ELAPSED", FieldCategory.TIME, FieldFormat.DURATION, DataType.Type.ELAPSED_TIME),
    PAUSED_TIME("PAUSED", FieldCategory.TIME, FieldFormat.DURATION, DataType.Type.PAUSED_TIME),
    CLOCK("CLOCK", FieldCategory.TIME, FieldFormat.CLOCK, DataType.Type.CLOCK_TIME),

    // navigation
    DIST_TO_DEST("TO DEST", FieldCategory.NAVIGATION, FieldFormat.DISTANCE, DataType.Type.DISTANCE_TO_DESTINATION),
    DIST_NEXT_TURN("NEXT TURN", FieldCategory.NAVIGATION, FieldFormat.DISTANCE, DataType.Type.DISTANCE_TO_NEXT_TURN),
    ELEV_REMAINING("ELEV LEFT", FieldCategory.NAVIGATION, FieldFormat.ELEVATION, DataType.Type.ELEVATION_REMAINING),
    DESCENT_REMAINING("DESC LEFT", FieldCategory.NAVIGATION, FieldFormat.ELEVATION, DataType.Type.DESCENT_REMAINING),
    TIME_TO_DEST("TIME TO DEST", FieldCategory.NAVIGATION, FieldFormat.DURATION, DataType.Type.TIME_TO_DESTINATION),
    ETA("ETA", FieldCategory.NAVIGATION, FieldFormat.CLOCK, DataType.Type.TIME_OF_ARRIVAL),
    HEADING("HEADING", FieldCategory.NAVIGATION, FieldFormat.INTEGER, DataType.Type.HEADING),

    // current lap
    LAP_NUMBER("LAP", FieldCategory.LAP, FieldFormat.INTEGER, DataType.Type.LAP_NUMBER),
    LAP_TIME("LAP TIME", FieldCategory.LAP, FieldFormat.DURATION, DataType.Type.ELAPSED_TIME_LAP),
    LAP_DISTANCE("LAP DIST", FieldCategory.LAP, FieldFormat.DISTANCE, DataType.Type.DISTANCE_LAP),
    LAP_AVG_SPEED("LAP SPEED", FieldCategory.LAP, FieldFormat.SPEED, DataType.Type.AVERAGE_SPEED_LAP),
    LAP_MAX_SPEED("LAP MAX SPD", FieldCategory.LAP, FieldFormat.SPEED, DataType.Type.MAX_SPEED_LAP),
    LAP_AVG_HR("LAP HR", FieldCategory.LAP, FieldFormat.INTEGER, DataType.Type.AVERAGE_LAP_HR),
    LAP_MAX_HR("LAP MAX HR", FieldCategory.LAP, FieldFormat.INTEGER, DataType.Type.MAX_HR_LAP),
    LAP_CADENCE("LAP CAD", FieldCategory.LAP, FieldFormat.INTEGER, DataType.Type.CADENCE_LAP),
    LAP_MAX_CADENCE("LAP MAX CAD", FieldCategory.LAP, FieldFormat.INTEGER, DataType.Type.MAX_CADENCE_LAP),
    LAP_POWER("LAP POWER", FieldCategory.LAP, FieldFormat.INTEGER, DataType.Type.POWER_LAP),
    LAP_NP("LAP NP", FieldCategory.LAP, FieldFormat.INTEGER, DataType.Type.NORMALIZED_POWER_LAP),
    LAP_BALANCE("LAP BAL", FieldCategory.LAP, FieldFormat.NUMBER, DataType.Type.AVERAGE_PEDAL_POWER_BALANCE_LAP),
    LAP_MAX_POWER("LAP MAX PWR", FieldCategory.LAP, FieldFormat.INTEGER, DataType.Type.MAX_POWER_LAP),
    LAP_TORQUE("LAP TORQUE", FieldCategory.LAP, FieldFormat.NUMBER, DataType.Type.TORQUE_LAP),
    LAP_MAX_TORQUE("LAP MAX TRQ", FieldCategory.LAP, FieldFormat.NUMBER, DataType.Type.MAX_TORQUE_LAP),
    LAP_W_KG("LAP W/KG", FieldCategory.LAP, FieldFormat.NUMBER2, DataType.Type.POWER_TO_WEIGHT_LAP),
    LAP_VAM("LAP VAM", FieldCategory.LAP, FieldFormat.VERTICAL_SPEED, DataType.Type.AVERAGE_VERTICAL_SPEED_LAP),
    LAP_ASCENT("LAP ASCENT", FieldCategory.LAP, FieldFormat.ELEVATION, DataType.Type.ELEVATION_GAIN_LAP),
    LAP_DESCENT("LAP DESCENT", FieldCategory.LAP, FieldFormat.ELEVATION, DataType.Type.ELEVATION_LOSS_LAP),
    LAP_MIN_ELEV("LAP MIN ELEV", FieldCategory.LAP, FieldFormat.ELEVATION, DataType.Type.MIN_ELEVATION_LAP),
    LAP_MAX_ELEV("LAP MAX ELEV", FieldCategory.LAP, FieldFormat.ELEVATION, DataType.Type.MAX_ELEVATION_LAP),
    LAP_AVG_ELEV("LAP AVG ELEV", FieldCategory.LAP, FieldFormat.ELEVATION, DataType.Type.AVERAGE_ELEVATION_LAP),
    LAP_CORE_TEMP("LAP CORE T", FieldCategory.LAP, FieldFormat.TEMPERATURE, DataType.Type.AVERAGE_CORE_TEMP_LAP),
    LAP_MAX_CORE_TEMP("LAP MAX CORE", FieldCategory.LAP, FieldFormat.TEMPERATURE, DataType.Type.MAX_CORE_TEMP_LAP),
    LAP_SKIN_TEMP("LAP SKIN T", FieldCategory.LAP, FieldFormat.TEMPERATURE, DataType.Type.AVERAGE_SKIN_TEMP_LAP),
    LAP_MAX_SKIN_TEMP("LAP MAX SKIN", FieldCategory.LAP, FieldFormat.TEMPERATURE, DataType.Type.MAX_SKIN_TEMP_LAP),

    // last lap
    LL_TIME("LL TIME", FieldCategory.LAST_LAP, FieldFormat.DURATION, DataType.Type.ELAPSED_TIME_LAST_LAP),
    LL_DISTANCE("LL DIST", FieldCategory.LAST_LAP, FieldFormat.DISTANCE, DataType.Type.DISTANCE_LAP_LAST_LAP),
    LL_AVG_SPEED("LL SPEED", FieldCategory.LAST_LAP, FieldFormat.SPEED, DataType.Type.AVERAGE_SPEED_LAST_LAP),
    LL_MAX_SPEED("LL MAX SPD", FieldCategory.LAST_LAP, FieldFormat.SPEED, DataType.Type.MAX_SPEED_LAST_LAP),
    LL_AVG_HR("LL HR", FieldCategory.LAST_LAP, FieldFormat.INTEGER, DataType.Type.AVERAGE_HR_LAST_LAP),
    LL_MAX_HR("LL MAX HR", FieldCategory.LAST_LAP, FieldFormat.INTEGER, DataType.Type.MAX_HR_LAST_LAP),
    LL_CADENCE("LL CAD", FieldCategory.LAST_LAP, FieldFormat.INTEGER, DataType.Type.AVERAGE_CADENCE_LAST_LAP),
    LL_MAX_CADENCE("LL MAX CAD", FieldCategory.LAST_LAP, FieldFormat.INTEGER, DataType.Type.MAX_CADENCE_LAST_LAP),
    LL_POWER("LL POWER", FieldCategory.LAST_LAP, FieldFormat.INTEGER, DataType.Type.AVERAGE_POWER_LAST_LAP),
    LL_NP("LL NP", FieldCategory.LAST_LAP, FieldFormat.INTEGER, DataType.Type.NORMALIZED_POWER_LAST_LAP),
    LL_BALANCE("LL BAL", FieldCategory.LAST_LAP, FieldFormat.NUMBER, DataType.Type.AVERAGE_PEDAL_POWER_BALANCE_LAST_LAP),
    LL_MAX_POWER("LL MAX PWR", FieldCategory.LAST_LAP, FieldFormat.INTEGER, DataType.Type.MAX_POWER_LAST_LAP),
    LL_W_KG("LL W/KG", FieldCategory.LAST_LAP, FieldFormat.NUMBER2, DataType.Type.POWER_TO_WEIGHT_LAST_LAP),
    LL_ASCENT("LL ASCENT", FieldCategory.LAST_LAP, FieldFormat.ELEVATION, DataType.Type.ELEVATION_GAIN_LAST_LAP),
    LL_DESCENT("LL DESCENT", FieldCategory.LAST_LAP, FieldFormat.ELEVATION, DataType.Type.ELEVATION_LOSS_LAST_LAP),

    // shifting
    GEARS(
        "SHIFTING GEARS",
        FieldCategory.SHIFTING,
        FieldFormat.GEARS,
        DataType.Type.SHIFTING_GEARS,
        cellLabel = "GEARS",
    ),
    FRONT_GEAR("FRONT GEAR", FieldCategory.SHIFTING, FieldFormat.INTEGER, DataType.Type.SHIFTING_FRONT_GEAR),
    REAR_GEAR("REAR GEAR", FieldCategory.SHIFTING, FieldFormat.INTEGER, DataType.Type.SHIFTING_REAR_GEAR),
    SHIFT_BATTERY("SHIFT BATT", FieldCategory.SHIFTING, FieldFormat.PERCENT, DataType.Type.SHIFTING_BATTERY),
    SHIFT_COUNT("SHIFTS", FieldCategory.SHIFTING, FieldFormat.INTEGER, DataType.Type.SHIFTING_COUNT),
    SHIFT_COUNT_FRONT("SHIFTS F", FieldCategory.SHIFTING, FieldFormat.INTEGER, DataType.Type.SHIFTING_COUNT_FRONT),
    SHIFT_COUNT_REAR("SHIFTS R", FieldCategory.SHIFTING, FieldFormat.INTEGER, DataType.Type.SHIFTING_COUNT_REAR),

    // live segments
    SEGMENT_TIME("SEG TIME", FieldCategory.SEGMENT, FieldFormat.DURATION, DataType.Type.SEGMENT_TIME),
    SEGMENT_KOM("KOM", FieldCategory.SEGMENT, FieldFormat.DURATION, DataType.Type.SEGMENT_KOM),
    SEGMENT_VS_KOM("VS KOM", FieldCategory.SEGMENT, FieldFormat.DURATION, DataType.Type.SEGMENT_TIME_TO_KOM),
    SEGMENT_VS_PR("VS PR", FieldCategory.SEGMENT, FieldFormat.DURATION, DataType.Type.SEGMENT_TIME_TO_PR),
    SEGMENT_VS_CARROT("VS CARROT", FieldCategory.SEGMENT, FieldFormat.DURATION, DataType.Type.SEGMENT_TIME_TO_CARROT),
    SEGMENT_PR("PR", FieldCategory.SEGMENT, FieldFormat.DURATION, DataType.Type.SEGMENT_PR),
    SEGMENT_DIST_LEFT("SEG DIST", FieldCategory.SEGMENT, FieldFormat.DISTANCE, DataType.Type.SEGMENT_DISTANCE_REMAINING),
    SEGMENT_ELEV_LEFT("SEG ELEV", FieldCategory.SEGMENT, FieldFormat.ELEVATION, DataType.Type.SEGMENT_ELEVATION_REMAINING),

    // workout
    WO_POWER_TARGET("PWR TARGET", FieldCategory.WORKOUT, FieldFormat.INTEGER, DataType.Type.WORKOUT_POWER_TARGET),
    WO_CADENCE_TARGET("CAD TARGET", FieldCategory.WORKOUT, FieldFormat.INTEGER, DataType.Type.WORKOUT_CADENCE_TARGET),
    WO_HR_TARGET("HR TARGET", FieldCategory.WORKOUT, FieldFormat.INTEGER, DataType.Type.WORKOUT_HEART_RATE_TARGET),
    WO_INTERVAL_LEFT("INT LEFT", FieldCategory.WORKOUT, FieldFormat.DURATION, DataType.Type.WORKOUT_REMAINING_INTERVAL_DURATION),
    WO_TOTAL_LEFT("WO LEFT", FieldCategory.WORKOUT, FieldFormat.DURATION, DataType.Type.WORKOUT_REMAINING_TOTAL_DURATION),
    WO_INTERVAL_COUNT("INTERVAL", FieldCategory.WORKOUT, FieldFormat.INTEGER, DataType.Type.WORKOUT_INTERVAL_COUNT),
    WO_PRIMARY_TARGET("TARGET 1", FieldCategory.WORKOUT, FieldFormat.NUMBER, DataType.Type.WORKOUT_PRIMARY_TARGET),
    WO_SECONDARY_TARGET("TARGET 2", FieldCategory.WORKOUT, FieldFormat.NUMBER, DataType.Type.WORKOUT_SECONDARY_TARGET),
    WO_PRIMARY_OUTPUT("OUTPUT 1", FieldCategory.WORKOUT, FieldFormat.NUMBER, DataType.Type.WORKOUT_PRIMARY_TARGET_OUTPUT_VALUE),
    WO_SECONDARY_OUTPUT("OUTPUT 2", FieldCategory.WORKOUT, FieldFormat.NUMBER, DataType.Type.WORKOUT_SECONDARY_TARGET_OUTPUT_VALUE),

    // body temperature
    CORE_TEMP("CORE TEMP", FieldCategory.BODY, FieldFormat.TEMPERATURE, DataType.Type.CORE_TEMP),
    AVG_CORE_TEMP("AVG CORE", FieldCategory.BODY, FieldFormat.TEMPERATURE, DataType.Type.AVERAGE_CORE_TEMP),
    MAX_CORE_TEMP("MAX CORE", FieldCategory.BODY, FieldFormat.TEMPERATURE, DataType.Type.MAX_CORE_TEMP),
    SKIN_TEMP("SKIN TEMP", FieldCategory.BODY, FieldFormat.TEMPERATURE, DataType.Type.SKIN_TEMP),
    AVG_SKIN_TEMP("AVG SKIN", FieldCategory.BODY, FieldFormat.TEMPERATURE, DataType.Type.AVERAGE_SKIN_TEMP),
    MAX_SKIN_TEMP("MAX SKIN", FieldCategory.BODY, FieldFormat.TEMPERATURE, DataType.Type.MAX_SKIN_TEMP),

    // sun & weather
    TEMPERATURE("TEMP", FieldCategory.SUN, FieldFormat.TEMPERATURE, DataType.Type.TEMPERATURE),
    SUNRISE("SUNRISE", FieldCategory.SUN, FieldFormat.CLOCK, DataType.Type.SUNRISE),
    SUNSET("SUNSET", FieldCategory.SUN, FieldFormat.CLOCK, DataType.Type.SUNSET),
    CIVIL_DAWN("DAWN", FieldCategory.SUN, FieldFormat.CLOCK, DataType.Type.CIVIL_DAWN),
    CIVIL_DUSK("DUSK", FieldCategory.SUN, FieldFormat.CLOCK, DataType.Type.CIVIL_DUSK),
    TIME_TO_SUNRISE("TO SUNRISE", FieldCategory.SUN, FieldFormat.DURATION, DataType.Type.TIME_TO_SUNRISE),
    TIME_TO_SUNSET("TO SUNSET", FieldCategory.SUN, FieldFormat.DURATION, DataType.Type.TIME_TO_SUNSET),
    TIME_TO_DAWN("TO DAWN", FieldCategory.SUN, FieldFormat.DURATION, DataType.Type.TIME_TO_CIVIL_DAWN),
    TIME_TO_DUSK("TO DUSK", FieldCategory.SUN, FieldFormat.DURATION, DataType.Type.TIME_TO_CIVIL_DUSK),

    // device
    KAROO_BATTERY("KAROO BATT", FieldCategory.DEVICE, FieldFormat.PERCENT, DataType.Type.BATTERY_PERCENT),
    TIRE_PRESSURE_FRONT("TIRE F", FieldCategory.DEVICE, FieldFormat.PRESSURE, DataType.Type.TIRE_PRESSURE_FRONT),
    TIRE_PRESSURE_REAR("TIRE R", FieldCategory.DEVICE, FieldFormat.PRESSURE, DataType.Type.TIRE_PRESSURE_REAR),

    // suspension
    SUS_STATE_FRONT("SUS FRONT", FieldCategory.SUSPENSION, FieldFormat.INTEGER, DataType.Type.SUSPENSION_STATE_FRONT),
    SUS_STATE_REAR("SUS REAR", FieldCategory.SUSPENSION, FieldFormat.INTEGER, DataType.Type.SUSPENSION_STATE_REAR),
    SUS_EFFORT_ZONE("SUS ZONE", FieldCategory.SUSPENSION, FieldFormat.INTEGER, DataType.Type.SUSPENSION_EFFORT_ZONE),
    SUS_COUNT_FRONT("SUS CNT F", FieldCategory.SUSPENSION, FieldFormat.INTEGER, DataType.Type.SUSPENSION_STATE_COUNT_FRONT),
    SUS_COUNT_REAR("SUS CNT R", FieldCategory.SUSPENSION, FieldFormat.INTEGER, DataType.Type.SUSPENSION_STATE_COUNT_REAR),
    SUS_MODE("SUS MODE", FieldCategory.SUSPENSION, FieldFormat.INTEGER, DataType.Type.SUSPENSION_MODE),
    SUS_BIAS("SUS BIAS", FieldCategory.SUSPENSION, FieldFormat.PERCENT, DataType.Type.SUSPENSION_BIAS),
    SUS_LSC_FRONT("LSC F", FieldCategory.SUSPENSION, FieldFormat.INTEGER, DataType.Type.SUSPENSION_LSC_FRONT),
    SUS_LSC_REAR("LSC R", FieldCategory.SUSPENSION, FieldFormat.INTEGER, DataType.Type.SUSPENSION_LSC_REAR),
    SUS_BATTERY("SUS BATT", FieldCategory.SUSPENSION, FieldFormat.PERCENT, DataType.Type.SUSPENSION_BATTERY),

    // e-bike
    LEV_BATTERY("LEV BATT", FieldCategory.EBIKE, FieldFormat.PERCENT, DataType.Type.LEV_BATTERY_STATUS),
    LEV_RANGE("RANGE", FieldCategory.EBIKE, FieldFormat.DISTANCE, DataType.Type.LEV_ESTIMATED_RANGE),
    LEV_ASSIST("ASSIST", FieldCategory.EBIKE, FieldFormat.INTEGER, DataType.Type.LEV_ASSIST_MODE),
    LEV_CONSUMPTION("WH/KM", FieldCategory.EBIKE, FieldFormat.NUMBER, DataType.Type.LEV_ENERGY_CONSUMPTION),
    LEV_CONSUMPTION_20M("WH/KM 20M", FieldCategory.EBIKE, FieldFormat.NUMBER, DataType.Type.LEV_20M_ENERGY_CONSUMPTION),
    LEV_MOTOR_POWER("MOTOR PWR", FieldCategory.EBIKE, FieldFormat.INTEGER, DataType.Type.LEV_MOTOR_POWER),
    LEV_COMBINED_POWER("TOTAL PWR", FieldCategory.EBIKE, FieldFormat.INTEGER, DataType.Type.LEV_COMBINED_POWER),

    // Ki2 extension (Shimano Di2) — only streams when Ki2 is installed and paired
    KI2_GEARS(
        "SHIFTING GEARS",
        FieldCategory.KI2,
        FieldFormat.GEARS,
        "${KI2}DATATYPE_FRONT_GEARS_INDEX",
        "${KI2}DATATYPE_REAR_GEARS_INDEX",
        cellLabel = "GEARS",
    ),
    KI2_FRONT_GEAR("DI2 FRONT", FieldCategory.KI2, FieldFormat.INTEGER, "${KI2}DATATYPE_FRONT_GEARS_INDEX"),
    KI2_REAR_GEAR("DI2 REAR", FieldCategory.KI2, FieldFormat.INTEGER, "${KI2}DATATYPE_REAR_GEARS_INDEX"),
    KI2_FRONT_TEETH("DI2 F TEETH", FieldCategory.KI2, FieldFormat.INTEGER, "${KI2}DATATYPE_FRONT_GEARS_SIZE"),
    KI2_REAR_TEETH("DI2 R TEETH", FieldCategory.KI2, FieldFormat.INTEGER, "${KI2}DATATYPE_REAR_GEARS_SIZE"),
    KI2_GEAR_RATIO("RATIO", FieldCategory.KI2, FieldFormat.NUMBER2, "${KI2}DATATYPE_GEAR_RATIO"),
    KI2_SHIFT_COUNT("DI2 SHIFTS", FieldCategory.KI2, FieldFormat.INTEGER, "${KI2}DATATYPE_SHIFT_COUNT"),
    KI2_SHIFT_COUNT_FRONT("DI2 SHIFTS F", FieldCategory.KI2, FieldFormat.INTEGER, "${KI2}DATATYPE_FRONT_SHIFT_COUNT"),
    KI2_SHIFT_COUNT_REAR("DI2 SHIFTS R", FieldCategory.KI2, FieldFormat.INTEGER, "${KI2}DATATYPE_REAR_SHIFT_COUNT"),
    KI2_BATTERY("DI2 BATT", FieldCategory.KI2, FieldFormat.PERCENT, "${KI2}DATATYPE_SHIFTING_BATTERY_PERCENTAGE"),
    KI2_MODE("DI2 MODE", FieldCategory.KI2, FieldFormat.INTEGER, "${KI2}DATATYPE_SHIFTING_MODE"),
    ;

    /** What the fullscreen grid cell shows; the picker always shows [label]. */
    val cellLabel: String = cellLabel ?: label
}

data class Settings(
    val overlayEnabled: Boolean = true,
    val paletteId: String = "karoo",
    val baseMode: BaseMode = BaseMode.FULL,
    /** Tap-to-cycle preview windows, kilometers. */
    val previewWindowsKm: List<Double> = listOf(2.0, 5.0, 10.0),
    /** Overlay appears this many meters before the climb starts. */
    val triggerDistanceM: Double = 500.0,
    /** Pop the chip open into the drawer when a climb becomes active. */
    val autoExpand: Boolean = true,
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
