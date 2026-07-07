package com.hazzus.karooclimber.palette

import android.graphics.Color

/**
 * Grade → color band palettes.
 *
 * Band values adapted from the Barberfish project (github.com/jpweytjens/barberfish),
 * Apache License 2.0, Copyright Barberfish contributors.
 * Bands are (minGrade%, colorInt), sorted descending; first band whose min <= grade wins.
 */
data class GradePalette(
    val id: String,
    val displayName: String,
    val bands: List<Pair<Double, Int>>,
) {
    fun colorFor(grade: Double): Int =
        bands.firstOrNull { grade >= it.first }?.second ?: bands.last().second
}

object GradePalettes {

    private val KAROO = GradePalette(
        "karoo", "Karoo",
        listOf(
            20.0 to 0xFF9020A0.toInt(), // >20%      — purple
            14.0 to 0xFFD01020.toInt(), // 14–19.9%  — red
            11.0 to 0xFFF06020.toInt(), // 11–13.9%  — orange
            8.0 to 0xFFF08868.toInt(), //  8–10.9%  — salmon
            5.0 to 0xFFF0D800.toInt(), //  5–7.9%   — yellow
            2.0 to 0xFF40D078.toInt(), //  2–4.9%   — mint green
            0.0 to 0xFF1A8C3A.toInt(), //  <2%      — dark green
        ),
    )

    private val WAHOO = GradePalette(
        "wahoo", "Wahoo",
        listOf(
            20.0 to 0xFF540000.toInt(),
            12.0 to 0xFFAA0200.toInt(),
            8.0 to 0xFFFF5501.toInt(),
            4.0 to 0xFFFEFF00.toInt(),
            0.0 to 0xFF04FE00.toInt(),
        ),
    )

    private val GARMIN = GradePalette(
        "garmin", "Garmin",
        listOf(
            12.0 to 0xFFED1B24.toInt(), // >12%  HC
            9.0 to 0xFFF36C72.toInt(), //  9–12% Cat 1
            6.0 to 0xFFFBAD41.toInt(), //  6–9%  Cat 2
            3.0 to 0xFFF9EE44.toInt(), //  3–6%  Cat 3
            0.0 to 0xFF6EBE43.toInt(), //  0–3%  Cat 4
        ),
    )

    private val ZWIFT = GradePalette(
        "zwift", "Zwift",
        listOf(
            9.0 to 0xFFEA5147.toInt(), //  9%+   — red
            6.0 to 0xFFFE8253.toInt(), //  6–9%  — orange
            3.0 to 0xFFF2C510.toInt(), //  3–6%  — yellow
            0.0 to 0xFF39A7D6.toInt(), //  0–3%  — blue
        ),
    )

    private val HSLUV = GradePalette(
        "hsluv", "HSLuv",
        listOf(
            18.0 to 0xFFFF41DF.toInt(), // >18%   purple/neuromuscular
            15.0 to 0xFFFF5F68.toInt(), // 15–18% red/anaerobic
            12.0 to 0xFFBB9000.toInt(), // 12–15% yellow/VO2max
            9.0 to 0xFF71A500.toInt(), //  9–12% yellow-green/threshold
            6.0 to 0xFF00AA86.toInt(), //  6–9%  green/tempo
            3.0 to 0xFF00A5B8.toInt(), //  3–6%  teal/endurance
            0.0 to 0xFF9395A1.toInt(), //  <3%   blue-gray/recovery
        ),
    )

    private val TURBO = GradePalette(
        "turbo", "Turbo",
        listOf(
            15.0 to 0xFF8E1201.toInt(), // [15, ∞)  — deep crimson
            12.0 to 0xFFBC2900.toInt(), // [12, 15) — dark red
            9.0 to 0xFFDD4700.toInt(), //  [9, 12) — red-orange
            6.0 to 0xFFFE932C.toInt(), //  [6, 9)  — orange
            3.0 to 0xFFF1D749.toInt(), //  [3, 6)  — yellow
            0.0 to 0xFFB0F94D.toInt(), //  [0, 3)  — lime green
            -3.0 to 0xFF30F0A9.toInt(), // [-3, 0)  — mint
            -6.0 to 0xFF2BC7F0.toInt(), // [-6, -3) — light blue
            -9.0 to 0xFF5783E9.toInt(), // [-9, -6) — blue
            Double.NEGATIVE_INFINITY to 0xFF401C4C.toInt(), // (-∞, -9) — dark purple
        ),
    )

    val all: List<GradePalette> = listOf(KAROO, WAHOO, GARMIN, ZWIFT, HSLUV, TURBO)

    fun byId(id: String): GradePalette = all.firstOrNull { it.id == id } ?: KAROO

    /** Fallback for flat/descending stretches in palettes without negative bands. */
    val descentColor: Int = Color.rgb(0x50, 0x78, 0x96)
}
