package com.hazzus.karooclimber.data

/**
 * A climb the rider has already finished this ride, kept for the per-ride history.
 * All distances/elevations in meters, grade in percent. The optional fields are
 * "room to grow": null today, populated by later steps (and further stats such as
 * elapsed/moving time, average speed/power will be added as optional fields here).
 */
data class CompletedClimb(
    val length: Double,                    // meters
    val avgGrade: Double,                  // percent
    val totalElevation: Double? = null,    // meters; room to grow
    val climbNumber: Int? = null,          // dedupe key / display
    // future extension fields (elapsedTimeS, movingTimeS, avgSpeed, avgPower ...) go here
)

/**
 * Serialize one [CompletedClimb] as a tolerant `key=value;key=value` string, e.g.
 * `len=812.0;grade=6.4;elev=52.0;num=1`. Null optional fields are omitted.
 *
 * The keys ([KEY_LEN], [KEY_GRADE], [KEY_ELEV], [KEY_NUM]) end up persisted, so they
 * are deliberately short and stable: DO NOT rename them — old records must keep
 * decoding. New fields get NEW keys and are appended; [decodeCompletedClimb] ignores
 * keys it doesn't know, so readers and writers can evolve independently.
 */
fun CompletedClimb.encode(): String = buildList {
    add("$KEY_LEN=$length")
    add("$KEY_GRADE=$avgGrade")
    totalElevation?.let { add("$KEY_ELEV=$it") }
    climbNumber?.let { add("$KEY_NUM=$it") }
}.joinToString(PAIR_SEP)

/**
 * Parse one record produced by [CompletedClimb.encode], tolerantly:
 *  - unknown keys are ignored (forward-compat as new fields are added),
 *  - missing optional keys (`elev`/`num`) become null,
 *  - a missing or unparseable required key (`len`/`grade`) yields null — the record
 *    is treated as invalid rather than throwing.
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
    )
}

// Persisted key names — see [CompletedClimb.encode]; must not be renamed.
private const val KEY_LEN = "len"
private const val KEY_GRADE = "grade"
private const val KEY_ELEV = "elev"
private const val KEY_NUM = "num"
private const val PAIR_SEP = ";"
private const val KV_SEP = '='
