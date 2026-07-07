package com.hazzus.karooclimber.data

/**
 * Google encoded polyline algorithm over pairs of doubles.
 *
 * Karoo uses it in two flavors:
 *  - route polyline: (lat, lng), precision 5 (factor 1e5)
 *  - elevation polyline: (distance, elevation), precision 1 (factor 10)
 */
object Polyline {

    fun decode(encoded: String, factor: Double): List<Pair<Double, Double>> {
        val result = ArrayList<Pair<Double, Double>>()
        var index = 0
        var first = 0L
        var second = 0L
        while (index < encoded.length) {
            val (dFirst, i1) = decodeValue(encoded, index)
            first += dFirst
            val (dSecond, i2) = decodeValue(encoded, i1)
            second += dSecond
            index = i2
            result.add(first / factor to second / factor)
        }
        return result
    }

    fun encode(pairs: List<Pair<Double, Double>>, factor: Double): String {
        val sb = StringBuilder()
        var prevFirst = 0L
        var prevSecond = 0L
        for ((a, b) in pairs) {
            val ia = Math.round(a * factor)
            val ib = Math.round(b * factor)
            encodeValue(ia - prevFirst, sb)
            encodeValue(ib - prevSecond, sb)
            prevFirst = ia
            prevSecond = ib
        }
        return sb.toString()
    }

    private fun decodeValue(encoded: String, startIndex: Int): Pair<Long, Int> {
        var index = startIndex
        var shift = 0
        var result = 0L
        var byte: Int
        do {
            byte = encoded[index++].code - 63
            result = result or ((byte and 0x1f).toLong() shl shift)
            shift += 5
        } while (byte >= 0x20)
        val value = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        return value to index
    }

    private fun encodeValue(value: Long, sb: StringBuilder) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            sb.append((((v and 0x1f) or 0x20) + 63).toInt().toChar())
            v = v shr 5
        }
        sb.append((v + 63).toInt().toChar())
    }
}
