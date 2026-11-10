package com.daviddokakis.upahead

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Not a regular test: prints what UpAhead makes of a real route's POIs. Runs only with
 * UPAHEAD_POIS set to a file of lines "km<TAB>karoo type<TAB>name" (made from a GPX, kept out of git).
 */
class RealRouteCheck {
    @Test fun printStops() {
        val path = System.getenv("UPAHEAD_POIS")
        assumeTrue(path != null && File(path).exists())
        val pois = File(path!!).readLines().filter { it.isNotBlank() }.map { l ->
            val (km, type, name) = l.split('\t'); RoutePoi(type, name, km.toDouble(), 0.0, 0.0)
        }
        val total = System.getenv("UPAHEAD_TOTAL_KM")?.toDouble() ?: 314.2
        val r = RouteModel.build("real", total, listOf(0.0 to 0.0, total to 0.0), pois, emptyList())
        println("STOPS ${r.stops.size} of ${pois.size} POIs")
        r.stops.forEach { println("%6.1f  %-40s %s%s".format(it.km, it.kinds.joinToString(" "), it.name, it.cut?.let { c -> "  [cut '$c']" } ?: "")) }
        println("SECTIONS ${r.sections}")
    }
}
