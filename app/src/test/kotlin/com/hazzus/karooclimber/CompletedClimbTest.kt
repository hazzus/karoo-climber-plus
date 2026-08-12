package com.hazzus.karooclimber

import com.hazzus.karooclimber.data.CompletedClimb
import com.hazzus.karooclimber.data.decodeCompletedClimb
import com.hazzus.karooclimber.data.encode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompletedClimbTest {

    @Test
    fun `round-trips a fully populated record`() {
        val c = CompletedClimb(length = 812.0, avgGrade = 6.4, totalElevation = 52.0, climbNumber = 1)
        assertEquals(c, decodeCompletedClimb(c.encode()))
    }

    @Test
    fun `round-trips a record with null optionals`() {
        val c = CompletedClimb(length = 300.0, avgGrade = 3.5)
        val encoded = c.encode()
        // null optionals are omitted from the wire form
        assertEquals("len=300.0;grade=3.5", encoded)
        assertEquals(c, decodeCompletedClimb(encoded))
    }

    @Test
    fun `decode ignores unknown keys`() {
        val c = decodeCompletedClimb("len=500.0;foo=9;grade=4.0")
        assertEquals(CompletedClimb(length = 500.0, avgGrade = 4.0), c)
    }

    @Test
    fun `decode tolerates missing optional keys`() {
        val c = decodeCompletedClimb("len=500.0;grade=4.0")!!
        assertNull(c.totalElevation)
        assertNull(c.climbNumber)
    }

    @Test
    fun `decode returns null on missing required field`() {
        assertNull(decodeCompletedClimb("grade=4.0"))        // no len
        assertNull(decodeCompletedClimb("len=500.0"))        // no grade
        assertNull(decodeCompletedClimb(""))                 // empty
    }

    @Test
    fun `decode returns null on malformed required field`() {
        assertNull(decodeCompletedClimb("len=500.0;grade=abc"))
        assertNull(decodeCompletedClimb("len=;grade=4.0"))
    }

    @Test
    fun `decode does not throw on garbage input`() {
        // best-effort: garbage without valid required fields is null, never an exception
        assertNull(decodeCompletedClimb(";;;==;=;key"))
    }
}
