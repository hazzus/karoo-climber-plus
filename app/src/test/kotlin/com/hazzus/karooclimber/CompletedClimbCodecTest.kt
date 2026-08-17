package com.hazzus.karooclimber

import com.hazzus.karooclimber.data.CompletedClimb
import com.hazzus.karooclimber.data.ElevationProfile
import com.hazzus.karooclimber.data.decodeCompletedClimb
import com.hazzus.karooclimber.data.encode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletedClimbCodecTest {

    // Polyline factor 10 => 0.1 m resolution; allow a little slack for half-rounding.
    private val eps = 0.06

    @Test
    fun `full record with profile round-trips`() {
        val profile = listOf(
            ElevationProfile.Point(0.0, 812.0),
            ElevationProfile.Point(250.5, 828.3),
            ElevationProfile.Point(600.0, 864.7),
            ElevationProfile.Point(812.0, 864.0),
        )
        val original = CompletedClimb(
            length = 812.0,
            avgGrade = 6.4,
            totalElevation = 52.0,
            climbNumber = 1,
            elapsedS = 734.5,
            avgPower = 213.0,
            profile = profile,
        )

        val decoded = decodeCompletedClimb(original.encode())
        assertNotNull(decoded)
        decoded!!

        assertEquals(812.0, decoded.length, 1e-9)
        assertEquals(6.4, decoded.avgGrade, 1e-9)
        assertEquals(52.0, decoded.totalElevation!!, 1e-9)
        assertEquals(1, decoded.climbNumber)
        assertEquals(734.5, decoded.elapsedS!!, 1e-9)
        assertEquals(213.0, decoded.avgPower!!, 1e-9)

        val dProfile = decoded.profile
        assertNotNull(dProfile)
        assertEquals(profile.size, dProfile!!.size)
        profile.zip(dProfile).forEach { (o, d) ->
            assertEquals(o.distance, d.distance, eps)
            assertEquals(o.elevation, d.elevation, eps)
        }
    }

    @Test
    fun `old record without new keys decodes with nulls`() {
        val decoded = decodeCompletedClimb("len=812.0;grade=6.4;elev=52.0;num=1")
        assertNotNull(decoded)
        decoded!!
        assertEquals(812.0, decoded.length, 1e-9)
        assertEquals(6.4, decoded.avgGrade, 1e-9)
        assertEquals(52.0, decoded.totalElevation!!, 1e-9)
        assertEquals(1, decoded.climbNumber)
        assertNull(decoded.elapsedS)
        assertNull(decoded.avgPower)
        assertNull(decoded.profile)
    }

    @Test
    fun `unknown key is ignored`() {
        val decoded = decodeCompletedClimb("len=500.0;grade=5.0;foo=bar;time=100.0")
        assertNotNull(decoded)
        decoded!!
        assertEquals(500.0, decoded.length, 1e-9)
        assertEquals(5.0, decoded.avgGrade, 1e-9)
        assertEquals(100.0, decoded.elapsedS!!, 1e-9)
        assertNull(decoded.avgPower)
        assertNull(decoded.profile)
    }

    @Test
    fun `empty prof value yields null profile, scalars intact`() {
        val decoded = decodeCompletedClimb("len=500.0;grade=5.0;prof=")
        assertNotNull(decoded)
        decoded!!
        assertEquals(500.0, decoded.length, 1e-9)
        assertEquals(5.0, decoded.avgGrade, 1e-9)
        assertNull(decoded.profile)
    }

    @Test
    fun `garbage prof value yields null profile without throwing`() {
        val decoded = decodeCompletedClimb("len=500.0;grade=5.0;prof=!!!garbage;pwr=200.0")
        assertNotNull(decoded)
        decoded!!
        assertEquals(500.0, decoded.length, 1e-9)
        assertEquals(5.0, decoded.avgGrade, 1e-9)
        assertEquals(200.0, decoded.avgPower!!, 1e-9)
        assertNull(decoded.profile)
    }

    @Test
    fun `single-point profile is not emitted as prof`() {
        val climb = CompletedClimb(
            length = 300.0,
            avgGrade = 4.0,
            profile = listOf(ElevationProfile.Point(0.0, 100.0)),
        )
        val encoded = climb.encode()
        assertFalse("single-point profile must not be encoded", encoded.contains("prof="))
        assertNull(decodeCompletedClimb(encoded)!!.profile)
    }

    @Test
    fun `encoded profile record contains no newline and survives split`() {
        val profile = listOf(
            ElevationProfile.Point(0.0, 100.0),
            ElevationProfile.Point(150.0, 118.0),
            ElevationProfile.Point(400.0, 152.0),
        )
        val encoded = CompletedClimb(length = 400.0, avgGrade = 13.0, profile = profile).encode()
        assertFalse("record must not contain a newline", encoded.contains("\n"))

        // The polyline value sits between prof= and the end of the record (last field),
        // so splitting on ';' must keep it whole and re-decode identically.
        val redecoded = decodeCompletedClimb(encoded)
        assertNotNull(redecoded)
        val dProfile = redecoded!!.profile
        assertNotNull(dProfile)
        assertEquals(profile.size, dProfile!!.size)
        profile.zip(dProfile).forEach { (o, d) ->
            assertEquals(o.distance, d.distance, eps)
            assertEquals(o.elevation, d.elevation, eps)
        }
    }
}
