package com.daviddokakis.upahead

import android.content.Context
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.HidePolyline
import io.hammerhead.karooext.models.HideSymbols
import io.hammerhead.karooext.models.MapEffect
import io.hammerhead.karooext.models.ShowPolyline
import io.hammerhead.karooext.models.ShowSymbols
import io.hammerhead.karooext.models.Symbol
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * What UpAhead adds to the Karoo's map. The Karoo draws every route POI itself (its grey pins) and an
 * extension can't hide or change those, so we only add what it can't show:
 * - a badge on the corner of the pin for timing start / end and cut-offs (`ic_map_*`: the badge sits
 *   up and right of the drawable's centre, on the pin's round part);
 * - timed sections as black dashes along the route (lines have no dash style, so each dash is a line).
 */
class MapLayer(private val context: Context, private val session: UpAheadSession) {

    suspend fun run(emitter: Emitter<MapEffect>) {
        var shownSymbols = emptySet<String>()
        var shownLines = emptySet<String>()
        var drawnKey: Any? = null
        try {
            while (coroutineContext.isActive) {
                val route = session.state.value.route
                val geo = session.geo.value
                val prefs = Prefs.read(context)
                val key = Triple(route, geo.size, prefs.mapBadges to prefs.mapSections)
                if (key != drawnKey) {
                    val symbols = if (route != null && prefs.mapBadges) badges(route) else emptyList()
                    val lines = if (route != null && prefs.mapSections) dashes(route, geo) else emptyList()
                    (shownSymbols - symbols.map { it.id }.toSet()).takeIf { it.isNotEmpty() }?.let { emitter.onNext(HideSymbols(it.toList())) }
                    (shownLines - lines.map { it.id }.toSet()).forEach { emitter.onNext(HidePolyline(it)) }
                    if (symbols.isNotEmpty()) emitter.onNext(ShowSymbols(symbols))
                    lines.forEach { emitter.onNext(it) }
                    shownSymbols = symbols.map { it.id }.toSet(); shownLines = lines.map { it.id }.toSet()
                    drawnKey = key
                }
                delay(2000)
            }
        } finally {
            if (shownSymbols.isNotEmpty()) emitter.onNext(HideSymbols(shownSymbols.toList()))
            shownLines.forEach { emitter.onNext(HidePolyline(it)) }
        }
    }

    private fun badges(route: RouteModel): List<Symbol.Icon> = route.stops.mapIndexedNotNull { i, st ->
        val res = when {
            Kind.CUTOFF in st.kinds -> R.drawable.ic_map_cutoff
            Kind.TIMING_START in st.kinds -> R.drawable.ic_map_timing_start
            Kind.TIMING_END in st.kinds -> R.drawable.ic_map_timing_end
            else -> return@mapIndexedNotNull null
        }
        if (st.lat == 0.0 && st.lng == 0.0) null else Symbol.Icon("badge-$i", st.lat, st.lng, res, 0f)
    }

    private fun dashes(route: RouteModel, geo: List<GeoPoint>): List<ShowPolyline> {
        if (geo.size < 2) return emptyList()
        val out = mutableListOf<ShowPolyline>()
        for ((si, sec) in route.sections.filter { it.timed }.withIndex()) {
            var km = sec.startKm
            var n = 0
            while (km < sec.endKm) {
                val end = minOf(sec.endKm, km + DASH_KM)
                val pts = slice(geo, km, end)
                if (pts.size >= 2) out += ShowPolyline("timed-$si-${n++}", Polyline.encode(pts), DASH_COLOUR, DASH_WIDTH)
                km = end + DASH_KM
            }
        }
        return out
    }

    /** The route line from [a] to [b] km, with the end points on the line. */
    private fun slice(geo: List<GeoPoint>, a: Double, b: Double): List<Pair<Double, Double>> {
        fun at(km: Double): Pair<Double, Double> {
            val i = geo.indexOfFirst { it.km >= km }.let { if (it <= 0) 1 else it }
            val p0 = geo[i - 1]; val p1 = geo[i]
            val t = if (p1.km - p0.km < 1e-9) 0.0 else ((km - p0.km) / (p1.km - p0.km)).coerceIn(0.0, 1.0)
            return (p0.lat + (p1.lat - p0.lat) * t) to (p0.lng + (p1.lng - p0.lng) * t)
        }
        return listOf(at(a)) + geo.filter { it.km > a && it.km < b }.map { it.lat to it.lng } + listOf(at(b))
    }

    companion object {
        const val DASH_KM = 0.08
        const val DASH_COLOUR = 0xFF111111.toInt()
        /** Line width in SDK units (≈ 1.8 px each); the Karoo's route is about 20 px wide. */
        const val DASH_WIDTH = 4
    }
}
