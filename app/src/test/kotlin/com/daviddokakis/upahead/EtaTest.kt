package com.daviddokakis.upahead

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EtaTest {
    @Test fun learnsTheBunchSpeedOnTheFlat() {
        val s = SpeedByGrade()
        val physics = s.speedAt(0.0, 200.0, 83.0)
        repeat(600) { s.learn(11.0, 0.5, 1.0) }   // 10 minutes at 39.6 km/h in a bunch
        assertTrue(physics < 10.5)
        assertEquals(11.0, s.speedAt(0.0, 200.0, 83.0), 0.05)
        // Climbs aren't touched by flat riding
        assertEquals(s.speedAt(8.0, 200.0, 83.0), SpeedByGrade().speedAt(8.0, 200.0, 83.0), 1e-9)
    }

    @Test fun ignoresStandingStill() {
        val s = SpeedByGrade()
        val before = s.speedAt(0.0, 200.0, 83.0)
        repeat(100) { s.learn(0.5, 0.0, 1.0) }
        assertEquals(before, s.speedAt(0.0, 200.0, 83.0), 1e-9)
    }

    @Test fun descentsAreCapped() {
        assertTrue(SpeedByGrade().speedAt(-12.0, 300.0, 83.0) <= SpeedByGrade.MAX_DOWNHILL_MS + 1e-9)
    }

    @Test fun etaAddsUpTheRoute() {
        val flat = RouteModel.build("t", 50.0, listOf(0.0 to 0.0, 50.0 to 0.0), emptyList(), emptyList())
        val s = SpeedByGrade(); repeat(600) { s.learn(10.0, 0.0, 1.0) }
        assertEquals(1000.0, Eta.seconds(flat, 0.0, 10.0, s, 200.0, 83.0), 1.0)
    }

    @Test fun smoothedMovesAThirdButJumpsOnBigChanges() {
        val m = Smoothed()
        assertEquals(1000.0, m.next(1000.0)!!, 1e-9)
        assertEquals(1030.0, m.next(1090.0)!!, 1e-9)
        assertEquals(5000.0, m.next(5000.0)!!, 1e-9)
        assertNull(m.next(null))
    }
}
