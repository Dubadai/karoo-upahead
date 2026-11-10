package com.daviddokakis.upahead

import kotlin.math.PI
import kotlin.math.sin

/** DEBUG ONLY: a made-up 160 km route crowded with stops of every kind, to see the field with many icons. */
object BusyRoute {
    val route: RouteModel by lazy {
        val elevation = (0..1600).map { i -> val km = i / 10.0; km to (200 + 180 * sin(km / 9) + 120 * sin(km / 3.7 + 1) + 60 * sin(km * PI / 14)) }
        val pois = listOf(
            RoutePoi("control", "Timing start", 12.0, 0.0, 0.0), RoutePoi("summit", "Col 1", 17.5, 0.0, 0.0), RoutePoi("control", "Timing end", 17.6, 0.0, 0.0),
            RoutePoi("aid_station", "Feed 1: food water wc mechanic", 21.0, 0.0, 0.0), RoutePoi("caution", "Dangerous bend", 24.0, 0.0, 0.0),
            RoutePoi("water", "Fountain", 31.0, 0.0, 0.0), RoutePoi("coffee", "Café", 34.0, 0.0, 0.0), RoutePoi("restroom", null, 36.0, 0.0, 0.0),
            RoutePoi("control", "Cut-off 11:20", 42.0, 0.0, 0.0), RoutePoi("aid_station", "Feed 2: food water wc", 43.0, 0.0, 0.0),
            RoutePoi("first_aid", "Medical", 48.0, 0.0, 0.0), RoutePoi("bike_shop", "Bike shop", 55.0, 0.0, 0.0), RoutePoi("summit", "Col 2", 61.0, 0.0, 0.0),
            RoutePoi("viewpoint", "View", 63.0, 0.0, 0.0), RoutePoi("water", "Water", 70.0, 0.0, 0.0), RoutePoi("control", "Control 3", 78.0, 0.0, 0.0),
            RoutePoi("aid_station", "Feed 3: food water wc mechanic medical", 84.0, 0.0, 0.0), RoutePoi("control", "Cut-off 14:10", 84.2, 0.0, 0.0),
            RoutePoi("convenience_store", "Shop", 92.0, 0.0, 0.0), RoutePoi("caution", "Railway", 97.0, 0.0, 0.0), RoutePoi("summit", "Col 3", 103.0, 0.0, 0.0),
            RoutePoi("control", "Neutral start", 104.0, 0.0, 0.0), RoutePoi("control", "Neutral end", 111.0, 0.0, 0.0), RoutePoi("water", "Water", 116.0, 0.0, 0.0),
            RoutePoi("aid_station", "Feed 4: food water wc mechanic", 125.0, 0.0, 0.0), RoutePoi("coffee", "Café", 131.0, 0.0, 0.0),
            RoutePoi("control", "Cut-off 17:00", 140.0, 0.0, 0.0), RoutePoi("restroom", null, 146.0, 0.0, 0.0), RoutePoi("generic", "Finish", 160.0, 0.0, 0.0),
        )
        RouteModel.build("Busy", 160.0, elevation, pois, emptyList())
    }
    val state get() = UpAheadState(route = route, alongKm = 6.0, range = 0, headEtaSec = 3900.0, clockMin = 10 * 60 + 24)
}
