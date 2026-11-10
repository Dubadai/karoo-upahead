package com.daviddokakis.upahead

import org.junit.Assert.assertEquals
import org.junit.Test

class GeoTest {
    // A straight line along the equator from lng 0 to 0.01 (~1.1 km)
    private val line = listOf(GeoPoint(0.0, 0.0, 0.0), GeoPoint(1.113, 0.0, 0.01))

    @Test fun onTheLineIsZero() = assertEquals(0.0, Geo.distanceToLineM(line, 0.0, 0.005), 0.5)

    @Test fun beside_theLine() = assertEquals(111.3, Geo.distanceToLineM(line, 0.001, 0.005), 1.0)

    @Test fun pastTheEnd_measuresToTheEnd() = assertEquals(111.3, Geo.distanceToLineM(line, 0.0, 0.011), 1.0)
}
