package com.hazzus.karooclimber

import com.hazzus.karooclimber.data.powerFromValues
import io.hammerhead.karooext.models.DataType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PowerStreamRepoTest {

    @Test
    fun `null or empty values means no power`() {
        assertNull(powerFromValues(null))
        assertNull(powerFromValues(emptyMap()))
    }

    @Test
    fun `single field carries the power value`() {
        assertEquals(213.0, powerFromValues(mapOf(DataType.Field.SINGLE to 213.0))!!, 1e-9)
    }

    @Test
    fun `first value is used when single field is absent`() {
        assertEquals(199.0, powerFromValues(mapOf("whatever" to 199.0))!!, 1e-9)
    }

    @Test
    fun `coasting zero is a real sample, not absent`() {
        assertEquals(0.0, powerFromValues(mapOf(DataType.Field.SINGLE to 0.0))!!, 1e-9)
    }
}
