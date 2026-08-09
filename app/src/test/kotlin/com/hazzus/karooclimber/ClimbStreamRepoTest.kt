package com.hazzus.karooclimber

import com.hazzus.karooclimber.data.climbInfoFromValues
import io.hammerhead.karooext.models.DataType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClimbStreamRepoTest {

    @Test
    fun `null or empty values means not on a climb`() {
        assertNull(climbInfoFromValues(null))
        assertNull(climbInfoFromValues(emptyMap()))
    }

    @Test
    fun `values without any climb geometry means not on a climb`() {
        assertNull(climbInfoFromValues(mapOf(DataType.Field.ELEVATION_GRADE to 4.0)))
    }

    @Test
    fun `zeroed climb geometry means not on a climb`() {
        assertNull(climbInfoFromValues(mapOf(DataType.Field.DISTANCE_TO_TOP to 0.0)))
        assertNull(
            climbInfoFromValues(
                mapOf(
                    DataType.Field.DISTANCE_TO_TOP to 0.0,
                    DataType.Field.DISTANCE_FROM_BOTTOM to 0.0,
                ),
            ),
        )
    }

    @Test
    fun `distance-to-top alone counts as an active climb`() {
        val info = climbInfoFromValues(mapOf(DataType.Field.DISTANCE_TO_TOP to 800.0))
        assertEquals(800.0, info!!.distanceToTop!!, 1e-9)
        assertNull(info.distanceFromBottom)
        assertNull(info.climbNumber)
    }

    @Test
    fun `full value map parses every field and narrows counts to ints`() {
        val info = climbInfoFromValues(
            mapOf(
                DataType.Field.DISTANCE_TO_TOP to 600.0,
                DataType.Field.ELEVATION_TO_TOP to 90.0,
                DataType.Field.DISTANCE_FROM_BOTTOM to 400.0,
                DataType.Field.ELEVATION_FROM_BOTTOM to 60.0,
                DataType.Field.CLIMB_ELEVATION to 150.0,
                DataType.Field.CLIMB_NUMBER to 2.0,
                DataType.Field.TOTAL_CLIMBS to 5.0,
            ),
        )!!
        assertEquals(600.0, info.distanceToTop!!, 1e-9)
        assertEquals(90.0, info.elevationToTop!!, 1e-9)
        assertEquals(400.0, info.distanceFromBottom!!, 1e-9)
        assertEquals(60.0, info.elevationFromBottom!!, 1e-9)
        assertEquals(150.0, info.climbElevation!!, 1e-9)
        assertEquals(2, info.climbNumber)
        assertEquals(5, info.totalClimbs)
    }
}
