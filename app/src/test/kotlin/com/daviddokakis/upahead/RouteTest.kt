package com.daviddokakis.upahead

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTest {
    private fun kinds(type: String, name: String?) = PoiText.stop(type, name, 0.0, 0.0, 0.0)!!.kinds

    @Test fun servicesFromTheName() {
        assertEquals(listOf(Kind.FOOD, Kind.WATER, Kind.TOILET, Kind.MECHANIC), kinds("aid_station", "Puig Major: food water wc mechanic"))
        assertEquals(listOf(Kind.FOOD, Kind.WATER, Kind.TOILET), kinds("aid_station", "Avituallamiento Sóller: comida, agua y lavabos"))
        assertEquals(listOf(Kind.WATER), kinds("generic", "Aigua"))
    }

    @Test fun typeWhenTheNameSaysNothing() {
        assertEquals(listOf(Kind.FOOD), kinds("aid_station", "Feed station 2"))   // "feed" in the name: food
        assertEquals(listOf(Kind.FEED), kinds("aid_station", "Sa Batalla"))
        assertEquals(listOf(Kind.TOILET), kinds("restroom", null))
        assertEquals(listOf(Kind.CHECKPOINT), kinds("control", "Control 3"))
        assertEquals(listOf(Kind.OTHER), kinds("atm", "Cash"))
    }

    @Test fun timingAndCutoffs() {
        assertEquals(PoiText.Timing.START, PoiText.timing("Timing start Puig Major"))
        assertEquals(PoiText.Timing.END, PoiText.timing("Fin cronometraje"))
        assertEquals(PoiText.Timing.START, PoiText.timing("Inicio crono"))
        assertEquals(PoiText.Timing.UNTIMED_START, PoiText.timing("Neutral start descent"))
        assertEquals(PoiText.Timing.UNTIMED_END, PoiText.timing("Untimed end"))
        assertNull(PoiText.timing("Start"))
        assertEquals("16:40", PoiText.cutoff("Cut-off 16:40"))
        assertEquals("9:05", PoiText.cutoff("Cierre control 09.05"))
        assertNull(PoiText.cutoff("Water 16:40"))
        assertEquals(listOf(Kind.TIMING_START), kinds("control", "Timing start"))
        assertEquals(listOf(Kind.CUTOFF), kinds("control", "Cut-off 11:20"))
        // "timing mat" isn't food
        assertTrue(Kind.FOOD !in kinds("control", "Timing mat"))
    }

    /** Names from a real event route (Mallorca 312 on Strava, Catalan and Spanish). */
    @Test fun realEventNames() {
        assertNull(PoiText.stop("caution", "10kms pròxim avituallament", 0.0, 0.0, 0.0))
        assertNull(PoiText.stop("caution", "1km Perill vies tren", 0.0, 0.0, 0.0))
        assertNull(PoiText.stop("generic", "10km Meta", 0.0, 0.0, 0.0))
        assertEquals("", PoiText.cutoff("Temps tall 312"))
        assertEquals("16:30", PoiText.cutoff("Tiempo de corte 312-225, 16:30h"))
        assertEquals(listOf(Kind.CUTOFF), kinds("caution", "Temps de tall 312-225"))
        assertEquals(listOf(Kind.TIMING_START), kinds("generic", "Inici Segment Garmin"))
        assertEquals(listOf(Kind.CAUTION), kinds("generic", "Corba perillosa"))
        assertEquals(listOf(Kind.CAUTION), kinds("generic", "Perill Vies tren"))
        assertEquals(listOf(Kind.OTHER), kinds("caution", "Inici coll den Bleda"))
        assertEquals(listOf(Kind.SUMMIT), kinds("summit", "PUIG MAJOR"))
        assertEquals(listOf(Kind.WATER), kinds("water", "SA FORADADA"))
    }

    @Test fun findsClimbsWhenTheKarooSendsNone() {
        // flat, a 1 km climb at 5 % (50 m), flat again, a small 10 m bump
        val elev = (0..100).map { i -> val km = i / 10.0
            km to when { km < 3 -> 10.0; km < 4 -> 10.0 + (km - 3) * 50; km < 6 -> 60.0 - (km - 4) * 25; km < 8 -> 10.0; km < 8.3 -> 10 + (km - 8) * 33; else -> 10.0 } }
        val r = RouteModel.build("t", 10.0, elev, emptyList(), emptyList())
        assertEquals(1, r.summits.size)
        assertEquals(4.0, r.summits.single().first, 0.15)
        assertEquals(60.0, r.summits.single().second, 2.0)
    }

    @Test fun namesForTheInfoBox() {
        assertEquals("Puig Major", PoiText.betterName("Timing end", "Puig Major"))
        assertEquals("Puig Major", PoiText.betterName("Puig Major", "Cut-off 11:20"))
        assertEquals("Feed 2", PoiText.displayName("Feed 2: food water wc"))
    }

    @Test fun karooSegmentTypes() {
        assertEquals(listOf(Kind.TIMING_START), kinds("segment_start", "Puig Major climb"))
        assertEquals(listOf(Kind.TIMING_END), kinds("segment_end", null))
    }

    @Test fun openSegmentRunsToTheSummit() {
        val elev = (0..100).map { it.toDouble() to 0.0 }
        val r = RouteModel.build("t", 100.0, elev, listOf(
            RoutePoi("generic", "Inici Segment Garmin", 50.6, 0.0, 0.0),
            RoutePoi("summit", "PUIG MAJOR", 55.6, 0.0, 0.0),
        ), emptyList())
        assertEquals(listOf(Section(50.6, 55.6, true)), r.sections)
    }

    @Test fun stopsMergeAndSectionsPair() {
        val elev = (0..100).map { it.toDouble() to it * 10.0 }
        val r = RouteModel.build("t", 100.0, elev, listOf(
            RoutePoi("control", "Timing start", 50.0, 1.0, 2.0),
            RoutePoi("control", "Timing end", 55.2, 0.0, 0.0),
            RoutePoi("aid_station", "Puig Major: food water", 55.25, 0.0, 0.0),
            RoutePoi("control", "Cut-off 11:20", 80.0, 0.0, 0.0),
        ), listOf(10.0 to 5.0))
        assertEquals(3, r.stops.size)
        assertEquals(listOf(Kind.TIMING_END, Kind.FOOD, Kind.WATER), r.stops[1].kinds)
        assertEquals(listOf(Section(50.0, 55.2, true)), r.sections)
        assertEquals("11:20", r.stops[2].cut)
        assertEquals(15.0, r.summits.single().first, 1e-9)
        assertEquals(150.0, r.summits.single().second, 1e-6)
        assertEquals(1.0, r.grade(10.0, 11.0), 1e-9)   // 10 m up over 1 km
    }

    @Test fun reversedRouteFlips() {
        val elev = listOf(0.0 to 0.0, 100.0 to 1000.0)
        val r = RouteModel.build("t", 100.0, elev, listOf(RoutePoi("water", null, 30.0, 0.0, 0.0)), emptyList(), reversed = true)
        assertEquals(70.0, r.stops.single().km, 1e-9)
        assertEquals(1000.0, r.elevAt(0.0), 1e-9)
    }

    @Test fun headTarget() {
        val r = ExampleRoute.route
        assertEquals("13:30", UpAheadSession.headTarget(r, 24.6, HeadInfo.CUT)!!.cut)
        assertEquals(36.0, UpAheadSession.headTarget(r, 24.6, HeadInfo.FEED)!!.km, 0.2)
        assertEquals(95.0, UpAheadSession.headTarget(r, 70.0, HeadInfo.WATER)!!.km, 1e-9)
        assertNull(UpAheadSession.headTarget(r, 24.6, HeadInfo.OFF))
        assertEquals(30.0, UpAheadSession.headTarget(r, 24.6, HeadInfo.NEXT)!!.km, 1e-9)       // timing start
        assertEquals(36.0, UpAheadSession.headTarget(r, 24.6, HeadInfo.SUMMIT)!!.km, 1e-9)     // top of the first climb
        assertEquals(120.0, UpAheadSession.headTarget(r, 24.6, HeadInfo.FINISH)!!.km, 1e-9)
        assertEquals(36.0, UpAheadSession.headTarget(r, 24.6, HeadInfo.MECHANIC)!!.km, 0.2)
    }

    @Test fun polylineRoundTrip() {
        val pts = listOf(39.5696 to 2.6502, 39.7712 to 2.7154, 39.8 to 2.9)
        val back = Polyline.decode(Polyline.encode(pts), 1e5)
        pts.zip(back).forEach { (a, b) -> assertEquals(a.first, b.first, 1e-5); assertEquals(a.second, b.second, 1e-5) }
    }
}
