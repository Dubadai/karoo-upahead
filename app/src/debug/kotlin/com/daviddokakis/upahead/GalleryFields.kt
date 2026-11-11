package com.daviddokakis.upahead

import android.content.Context
import android.graphics.Bitmap

/**
 * DEBUG ONLY: what the field gallery (FieldGallery.kt) draws: the field with its own renderer, on
 * the example route (pictures only). [sizes]: null for every Karoo size.
 */
object GalleryFields {
    val variants = listOf("route", "route-large", "range-25", "range-5", "feed-time", "next-stop", "summit", "finish-time", "many", "many-25", "many-5", "file", "file-5", "file-time", "info", "info-cut", "flash-5", "flash-full", "off", "off-25", "off-5", "off-real", "summits", "summits-25", "summits-busy", "info-long", "flat", "flat-25", "cut", "cut-time")
    val sizes: List<String>? = null

    fun render(context: Context, variant: String, w: Int, h: Int, night: Boolean): Bitmap {
        val s = ExampleRoute.state
        val p = Prefs()
        val (state, prefs) = when (variant) {
            "route-large" -> s to p.copy(iconSize = IconSize.LARGE)
            "range-25" -> s.copy(range = 25) to p
            "range-5" -> s.copy(range = 5) to p
            "feed-time" -> s to p.copy(head = HeadInfo.FEED, headAs = HeadAs.TIME)
            "file" -> (FileRoute.state(context) ?: s) to p.copy(head = HeadInfo.NEXT)
            "file-5" -> (FileRoute.state(context) ?: s).copy(range = 5) to p.copy(head = HeadInfo.FEED)
            "file-time" -> (FileRoute.state(context) ?: s).copy(range = 5) to p.copy(head = HeadInfo.NEXT, headAs = HeadAs.TIME)
            "info" -> s.copy(selectedKm = 36.0, selectedEtaSec = 1500.0) to p.copy(head = HeadInfo.NEXT)
            "info-cut" -> s.copy(range = 25, selectedKm = 66.0, selectedEtaSec = 7200.0) to p.copy(head = HeadInfo.NEXT)
            "many" -> BusyRoute.state to p.copy(showOther = true, head = HeadInfo.NEXT)
            "many-25" -> BusyRoute.state.copy(range = 25) to p.copy(showOther = true, head = HeadInfo.NEXT)
            "many-5" -> BusyRoute.state.copy(range = 5) to p.copy(showOther = true)
            "flash-5" -> s.copy(range = 5, rangeFlash = true) to p
            "flash-full" -> s.copy(rangeFlash = true) to p
            "off", "off-25", "off-5", "off-real" -> {
                // 2.4 km back to the route, meeting it at km 27.0, from 60 m below it (pictures only)
                val r = s.route!!; val at = 27.0; val len = 2.4
                val straight = listOf(0.0 to r.elevAt(at) - 120, len to r.elevAt(at))
                s.copy(alongKm = at - len, range = when (variant) { "off-25" -> 25; "off-5", "off-real" -> 5; else -> 0 }, rejoinAtKm = at,
                    wayBack = if (variant == "off-real") (0..24).map { k -> val d = len * k / 24; d to (r.elevAt(at) - 120 + 120 * d / len + 25 * kotlin.math.sin(d * 4)) } else straight,
                    wayBackReal = variant == "off-real", offRoute = true) to p
            }
            "info-long" -> {
                // a gran fondo's long stop name and a late arrival (pictures only)
                val r = s.route!!
                val long = RouteModel.build("Example", 120.0, r.elevation, listOf(
                    RoutePoi("aid_station", "Avituallamiento Coll de Soller: food water wc", 66.0, 0.0, 0.0),
                    RoutePoi("control", "Cut-off 13:30", 66.0, 0.0, 0.0)), listOf(18.0 to 18.0))
                s.copy(route = long, range = 25, selectedKm = 66.0, selectedEtaSec = 7200.0, clockMin = 11 * 60 + 50) to p.copy(head = HeadInfo.NEXT)
            }
            "summits-25" -> s.copy(range = 25) to p.copy(showFood = false, showToilets = false, showMechanic = false, showTiming = false, showCutoffs = false)
            "summits-busy" -> s to p
            "flat", "flat-25" -> {
                // a flat route (under 150 m in view), so the stops would sit low (pictures only)
                val r = s.route!!
                val flat = RouteModel.build("Example", 120.0, r.elevation.map { (k, e) -> k to 100 + e * 0.12 }, listOf(
                    RoutePoi("control", "Timing start", 30.0, 0.0, 0.0), RoutePoi("aid_station", "Feed 1: food water wc", 36.1, 0.0, 0.0),
                    RoutePoi("water", "Water", 44.0, 0.0, 0.0), RoutePoi("control", "Cut-off 13:30", 66.0, 0.0, 0.0),
                    RoutePoi("aid_station", "Feed 2: food water", 95.0, 0.0, 0.0)), emptyList())
                s.copy(route = flat, range = if (variant == "flat-25") 25 else 0) to p
            }
            "cut" -> s.copy(range = 25) to p.copy(head = HeadInfo.CUT)
            "cut-time" -> s.copy(range = 25) to p.copy(head = HeadInfo.CUT, headAs = HeadAs.TIME)
            "summits" -> s to p.copy(showFood = false, showToilets = false, showMechanic = false, showTiming = false, showCutoffs = false)
            "next-stop" -> s.copy(range = 25) to p.copy(head = HeadInfo.NEXT)
            "summit" -> s.copy(range = 25) to p.copy(head = HeadInfo.SUMMIT)
            "finish-time" -> s to p.copy(head = HeadInfo.FINISH, headAs = HeadAs.TIME)
            else -> s to p
        }
        return ProfileRenderer(context).render(state, prefs, w, h, night)
    }
}
