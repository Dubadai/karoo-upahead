package com.daviddokakis.upahead

import kotlin.math.PI
import kotlin.math.cos

/**
 * A made-up route for pictures only: the field chooser's preview, the Settings Example card and the
 * field gallery. Never shown during a ride (no fake values in the app). 24.6 km ridden, as in our
 * showcase numbers.
 */
object ExampleRoute {
    private val CONTROL = listOf(0.0 to 20.0, 10.0 to 60.0, 18.0 to 120.0, 26.0 to 420.0, 30.0 to 560.0, 36.0 to 840.0, 44.0 to 300.0,
        52.0 to 120.0, 60.0 to 380.0, 66.0 to 210.0, 74.0 to 450.0, 82.0 to 160.0, 95.0 to 90.0, 108.0 to 260.0, 120.0 to 30.0)

    private fun elev(km: Double): Double {
        val i = CONTROL.indexOfFirst { it.first >= km }.coerceAtLeast(1)
        val (a, ea) = CONTROL[i - 1]; val (b, eb) = CONTROL[i]
        val t = ((km - a) / (b - a)).coerceIn(0.0, 1.0)
        return ea + (eb - ea) * (1 - cos(PI * t)) / 2
    }

    val route: RouteModel by lazy {
        val elevation = (0..1200).map { i -> val km = i / 10.0; km to elev(km) }
        val pois = listOf(
            RoutePoi("water", "Water", 28.0, 0.0, 0.0),   // the first stop ahead: "COMING UP" shows water, the most used kind
            RoutePoi("control", "Timing start", 30.0, 0.0, 0.0),
            RoutePoi("control", "Timing end", 36.0, 0.0, 0.0),
            RoutePoi("aid_station", "Feed 1: food water wc mechanic", 36.1, 0.0, 0.0),
            RoutePoi("aid_station", "Feed 2: food water wc", 66.0, 0.0, 0.0),
            RoutePoi("control", "Cut-off 13:30", 66.0, 0.0, 0.0),
            RoutePoi("water", "Water", 95.0, 0.0, 0.0),
            RoutePoi("aid_station", "Feed 3: food water wc mechanic", 108.0, 0.0, 0.0),
        )
        RouteModel.build("Example", 120.0, elevation, pois, listOf(18.0 to 18.0, 52.0 to 8.0, 66.0 to 8.0, 95.0 to 13.0))
    }

    val state: UpAheadState get() = UpAheadState(route = route, alongKm = 24.6, range = 0, headEtaSec = 4800.0, clockMin = 10 * 60 + 24)
}
