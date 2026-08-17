package com.hazzus.karooclimber.data

/**
 * A climb the rider has already finished this ride, kept for the per-ride history.
 * All distances/elevations in meters, grade in percent. The optional fields are
 * "room to grow": null today, populated by later steps.
 *
 * The later stats:
 *  - [elapsedS] — raw wall-clock seconds spent on the climb (not moving time),
 *  - [avgPower] — average power over the climb, in watts,
 *  - [profile]  — the climb-relative elevation silhouette (distance starts ~0,
 *    elevation absolute meters), so history can redraw the climb it captured.
 */
data class CompletedClimb(
    val length: Double,                    // meters
    val avgGrade: Double,                  // percent
    val totalElevation: Double? = null,    // meters; room to grow
    val climbNumber: Int? = null,          // dedupe key / display
    val elapsedS: Double? = null,          // raw seconds (wall-clock, not moving time)
    val avgPower: Double? = null,          // watts
    val profile: List<ElevationProfile.Point>? = null, // climb-relative silhouette
)

/**
 * Serialize one [CompletedClimb] as a tolerant `key=value;key=value` string, e.g.
 * `len=812.0;grade=6.4;elev=52.0;num=1`. Null optional fields are omitted.
 *
 * The keys ([KEY_LEN], [KEY_GRADE], [KEY_ELEV], [KEY_NUM], [KEY_TIME], [KEY_PWR],
 * [KEY_PROF]) end up persisted, so they are deliberately short and stable: DO NOT
 * rename them — old records must keep decoding. New fields get NEW keys and are
 * appended; [decodeCompletedClimb] ignores keys it doesn't know, so readers and
 * writers can evolve independently.
 *
 * [profile] is encoded with the same Google-polyline codec Karoo uses for elevation
 * ([ElevationProfile.ELEVATION_POLYLINE_FACTOR] = 10.0, precision 1). Its output is ASCII 63–126, so it
 * never contains `=`, `;`, or a newline and is safe both inside this format and inside
 * the newline-joined record list in `RideHistoryRepo`. A profile of <2 points is
 * degenerate and simply isn't emitted.
 */
fun CompletedClimb.encode(): String = buildList {
    add("$KEY_LEN=$length")
    add("$KEY_GRADE=$avgGrade")
    totalElevation?.let { add("$KEY_ELEV=$it") }
    climbNumber?.let { add("$KEY_NUM=$it") }
    elapsedS?.let { add("$KEY_TIME=$it") }
    avgPower?.let { add("$KEY_PWR=$it") }
    profile?.takeIf { it.size >= 2 }?.let { pts ->
        add("$KEY_PROF=${Polyline.encode(pts.map { it.distance to it.elevation }, ElevationProfile.ELEVATION_POLYLINE_FACTOR)}")
    }
}.joinToString(PAIR_SEP)

/**
 * Parse one record produced by [CompletedClimb.encode], tolerantly:
 *  - unknown keys are ignored (forward-compat as new fields are added),
 *  - missing optional keys become null,
 *  - a missing or unparseable required key (`len`/`grade`) yields null — the record
 *    is treated as invalid rather than throwing,
 *  - a malformed/empty `prof` yields `profile = null` without invalidating the rest
 *    of the record.
 *
 * Never throws on malformed input; any garbage in returns null (bad required field)
 * or a best-effort record (bad optional field simply stays null).
 */
fun decodeCompletedClimb(s: String): CompletedClimb? {
    val fields = HashMap<String, String>()
    for (token in s.split(PAIR_SEP)) {
        val eq = token.indexOf(KV_SEP)
        if (eq <= 0) continue // no '=', or empty key
        val key = token.substring(0, eq).trim()
        if (key.isNotEmpty()) fields[key] = token.substring(eq + 1).trim()
    }
    val length = fields[KEY_LEN]?.toDoubleOrNull() ?: return null
    val avgGrade = fields[KEY_GRADE]?.toDoubleOrNull() ?: return null
    return CompletedClimb(
        length = length,
        avgGrade = avgGrade,
        totalElevation = fields[KEY_ELEV]?.toDoubleOrNull(),
        climbNumber = fields[KEY_NUM]?.toIntOrNull(),
        elapsedS = fields[KEY_TIME]?.toDoubleOrNull(),
        avgPower = fields[KEY_PWR]?.toDoubleOrNull(),
        profile = fields[KEY_PROF]?.let { decodeProfile(it) },
    )
}

/**
 * Decode a `prof` value back into climb-relative points, or null if it's empty,
 * malformed, or degenerate (<2 points). Guarded so a bad profile never throws or
 * invalidates the rest of the record.
 */
private fun decodeProfile(encoded: String): List<ElevationProfile.Point>? {
    if (encoded.isEmpty()) return null
    return try {
        Polyline.decode(encoded, ElevationProfile.ELEVATION_POLYLINE_FACTOR)
            .map { ElevationProfile.Point(it.first, it.second) }
            .takeIf { it.size >= 2 }
    } catch (_: Exception) {
        null
    }
}

// Persisted key names — see [CompletedClimb.encode]; must not be renamed.
private const val KEY_LEN = "len"
private const val KEY_GRADE = "grade"
private const val KEY_ELEV = "elev"
private const val KEY_NUM = "num"
private const val KEY_TIME = "time"
private const val KEY_PWR = "pwr"
private const val KEY_PROF = "prof"
private const val PAIR_SEP = ";"
private const val KV_SEP = '='
