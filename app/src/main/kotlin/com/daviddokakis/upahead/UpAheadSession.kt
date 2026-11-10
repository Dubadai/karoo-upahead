package com.daviddokakis.upahead

import android.content.Context
import android.os.SystemClock
import com.dubadai.karoo.common.Physics
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.ActiveRidePage
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.HttpResponseState
import io.hammerhead.karooext.models.OnHttpResponse
import io.hammerhead.karooext.models.RideProfile
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.OnNavigationState.NavigationState
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import timber.log.Timber
import java.util.Calendar

/** One point of the route line: [km] along it, and where it is. */
data class GeoPoint(val km: Double, val lat: Double, val lng: Double)

/**
 * The loaded route and where the rider is on it, plus the ETA to the header's target. One instance,
 * owned by [UpAheadExtension]. Route, POIs and climbs come from the Karoo's navigation state;
 * position from its Distance to Destination; speed and power for the ETA.
 */
class UpAheadSession(private val context: Context, private val karoo: KarooSystemService) {

    // One thread for all state changes, so updates never race.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val _state = MutableStateFlow(UpAheadState(range = Prefs.read(context).startRange))
    val state: StateFlow<UpAheadState> = _state
    private val _geo = MutableStateFlow<List<GeoPoint>>(emptyList())
    /** The route line with km along it, for the map layer. */
    val geo: StateFlow<List<GeoPoint>> = _geo

    private val _activePage = MutableStateFlow<RideProfile.Page?>(null)
    /** The ride page on screen, so the field only redraws while it's seen. */
    val activePage: StateFlow<RideProfile.Page?> = _activePage

    private var routeKm: Double? = null
    private var rejoining = false
    // Off route: the Karoo's way back (positions only), where it meets the route, your altitude, and heights for it
    private var rejoinKey: String? = null
    private var rejoinAtKm: Double? = null
    private var rejoinGeo: List<GeoPoint> = emptyList()
    private var wayBackHeights: List<Pair<Double, Double>>? = null
    private var altitude: Double? = null
    private var toDestM: Double? = null
    private var speedMs: Double? = null
    private var watts: Double? = null
    private var massKg = 75.0 + Physics.BIKE_KG
    private var recording = false
    private val speeds = SpeedByGrade()
    private val smoothed = Smoothed()

    fun start() {
        scope.launch { karoo.consumerFlow<OnNavigationState>().collect { onNavigation(it.state) } }
        scope.launch {
            karoo.streamDataFlow(DataType.Type.DISTANCE_TO_DESTINATION).collect { s ->
                toDestM = s.field(DataType.Field.DISTANCE_TO_DESTINATION)
                // The Karoo's on-route flag: off route at once, before it has a way back (that took ~70 s in a route
                // test, 2026-11-10); logged for the route-test rounds
                s.field(DataType.Field.ON_ROUTE)?.let { v -> karooOff = v < 0.5; updateOffRoute() }
                updatePosition()
            }
        }
        scope.launch { karoo.consumerFlow<io.hammerhead.karooext.models.OnLocationChanged>().collect { onLocation(it.lat, it.lng) } }
        scope.launch { karoo.streamDataFlow(DataType.Type.SPEED).collect { speedMs = it.field(DataType.Field.SPEED) } }
        scope.launch { karoo.streamDataFlow(DataType.Type.PRESSURE_ELEVATION_CORRECTION).collect { altitude = it.field(DataType.Field.PRESSURE_ELEVATION); if (wayBackHeights == null && rejoinAtKm != null) publishWayBack() } }
        scope.launch { karoo.streamDataFlow(DataType.Type.SMOOTHED_30S_AVERAGE_POWER).collect { watts = it.field(DataType.Field.SMOOTHED_30S_AVERAGE_POWER) } }
        scope.launch { karoo.consumerFlow<RideState>().collect { recording = it is RideState.Recording } }
        scope.launch {
            karoo.consumerFlow<UserProfile>().collect { p ->
                if (p.weight > 30) massKg = p.weight + Physics.BIKE_KG
                _state.update { it.copy(imperial = p.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL) }
            }
        }
        scope.launch { karoo.consumerFlow<ActiveRidePage>().collect { _activePage.value = it.page } }
        scope.launch { tick() }
    }

    fun stop() = scope.cancel()

    /** Zoom in (fewer km ahead) or out (more km), from a tap on the header's right or left half; stops at the ends. */
    fun zoom(inward: Boolean) {
        scope.launch {
            _state.update { s -> val r = ranges(s); val i = r.indexOf(s.range).coerceAtLeast(0); s.copy(range = r[(if (inward) i + 1 else i - 1).coerceIn(0, r.size - 1)]) }
            Timber.i("Zoom ${if (inward) "in" else "out"}: ${_state.value.range}")
        }
    }

    /** A tap on a stop's icons: open its info box (or close it, when it's the open one). */
    fun select(km: Double, view: String?) {
        scope.launch {
            // A finger tap on the Karoo can arrive twice (seen on the device 2026-11-10): a tap on a stop only
            // ever opens it, never closes it; the box closes it.
            openedAt = android.os.SystemClock.elapsedRealtime()
            _state.update { it.copy(selectedKm = km, selectedView = view, selectedEtaSec = null) }
            updateSelectedEta()
            Timber.i("Stop $km")
        }
    }

    private var openedAt = 0L

    /** A tap on the info box: close it (not the doubled tap that just opened it). */
    fun closeInfo() {
        scope.launch {
            if (android.os.SystemClock.elapsedRealtime() - openedAt < CLOSE_GUARD_MS) { Timber.i("Close ignored: just opened"); return@launch }
            _state.update { it.copy(selectedKm = null, selectedView = null, selectedEtaSec = null) }
            Timber.i("Stop closed")
        }
    }

    private fun updateSelectedEta() {
        val s = _state.value
        val route = s.route; val along = s.alongKm; val km = s.selectedKm
        val eta = if (route != null && along != null && km != null && km > along) Eta.seconds(route, along, km, speeds, watts, massKg) else null
        _state.update { it.copy(selectedEtaSec = eta) }
    }

    private var pendingTap: kotlinx.coroutines.Job? = null

    /**
     * A tap on the graph, like the Karoo's own elevation graph: one tap zooms out (the next range, round and round),
     * a double tap zooms in (the previous one). Two taps within [DOUBLE_TAP_MS] are a double tap (measured on the
     * Karoo 2026-11-10: a finger's double tap arrives 166–199 ms apart, a doubled ghost tap ~900 ms); so a single tap
     * waits that long before it acts.
     */
    fun rangeTap() {
        scope.launch {
            val waiting = pendingTap
            if (waiting != null && waiting.isActive) {
                waiting.cancel(); pendingTap = null
                previousRange()
            } else {
                pendingTap = scope.launch { delay(DOUBLE_TAP_MS); pendingTap = null; nextRange() }
            }
        }
    }

    /** The previous range (zoom in), round and round. */
    fun previousRange() {
        scope.launch {
            _state.update { s -> val r = ranges(s); val i = r.indexOf(s.range).coerceAtLeast(0)
                s.copy(range = r[(i - 1 + r.size) % r.size], rangeChangedAt = android.os.SystemClock.elapsedRealtime()) }
            Timber.i("Range (double tap): ${_state.value.range}")
        }
    }

    /** Next range, round and round: the remote button. */
    fun nextRange() {
        scope.launch {
            _state.update { s -> val r = ranges(s); s.copy(range = r[(r.indexOf(s.range).coerceAtLeast(0) + 1) % r.size], rangeChangedAt = android.os.SystemClock.elapsedRealtime()) }
            Timber.i("Range: ${_state.value.range}")
        }
    }

    private fun onNavigation(nav: NavigationState) {
        smoothed.reset()
        when (nav) {
            is NavigationState.NavigatingRoute -> {
                rejoining = nav.rejoinPolyline != null
                _geo.value = geoLine(nav.routePolyline)   // first: the rejoin point is looked up on it
                val rejoin = nav.rejoinPolyline
                if (rejoin != rejoinKey) {
                    rejoinKey = rejoin
                    rejoinAtKm = nav.rejoinDistance?.let { it / 1000 }   // replaced below by where the way back meets the route
                    // A way back under 10 m is none (a reroute from the start line sends an empty path; 2026-11-10)
                    rejoinGeo = rejoin?.let(::geoLine)?.takeIf { it.size >= 2 && it.last().km >= MIN_WAY_BACK_KM } ?: emptyList()
                    wayBackHeights = null
                    val geo = rejoinGeo
                    // After a reroute the Karoo gives rejoinDistance along its new way, not along the route (route test
                    // 2026-11-10: "6.40 km" where it meant km ~51). The way back ends on the route: find that spot on the route.
                    if (geo.isNotEmpty()) rejoinAtKm = routeKmAt(geo.last().lat, geo.last().lng) ?: rejoinAtKm
                    if (rejoin != null && geo.isNotEmpty()) scope.launch { fetchWayBackHeights(rejoin, geo) }
                }
                val total = nav.routeDistance / 1000
                routeKm = total
                val elevation = nav.routeElevationPolyline?.let { Polyline.decode(it, 10.0) }?.map { (m, e) -> m / 1000 to e } ?: emptyList()
                val pois = nav.pois.flatMap { p -> p.distancesAlongRoute.map { d -> RoutePoi(p.type, p.name, d / 1000, p.lat, p.lng) } }
                val climbs = nav.climbs.map { it.startDistance / 1000 to it.length / 1000 }
                // What the Karoo hands over, as is (route tests: Strava, Ride with GPS, Dashboard)
                nav.pois.forEach { p -> Timber.i("POI type=${p.type} name=${p.name} at=${p.distancesAlongRoute.map { "%.1f".format(it / 1000) }}") }
                Timber.i("Route ${nav.name}: ${nav.pois.size} POIs, ${nav.climbs.size} climbs, reversed=${nav.reversed}")
                val route = RouteModel.build(nav.name, total, elevation, pois, climbs, nav.reversed)
                // A newly loaded route starts on the default view (Settings › Start with: the whole route unless changed;
                // 2026-11-10); a reroute of the same route keeps the range you tapped to
                val newRoute = _state.value.route?.name != route.name
                _state.update { it.copy(route = route, headEtaSec = null, selectedKm = null, selectedEtaSec = null,
                    range = if (newRoute) Prefs.read(context).startRange else it.range) }
                publishWayBack()
                Timber.i("Route ${nav.name}: ${"%.1f".format(total)} km, ${elevation.size} elevation points, " +
                    "stops ${route.stops.map { "${"%.1f".format(it.km)} ${it.kinds}${it.cut?.let { c -> " cut $c" } ?: ""}" }}, sections ${route.sections}")
            }
            else -> {
                routeKm = null
                _geo.value = emptyList()
                rejoinKey = null; rejoinAtKm = null; rejoinGeo = emptyList(); wayBackHeights = null
                _state.update { it.copy(route = null, alongKm = null, headEtaSec = null, wayBack = null, rejoinAtKm = null) }
            }
        }
    }

    private fun updatePosition() {
        val total = routeKm ?: return
        val along = toDestM?.let { (total - it / 1000).coerceIn(0.0, total) }
        // Off the route the Karoo's distance to destination includes the way back, so the position steps
        // back: keep the ▼ where it was until we're back on the route (the distances stay real)
        // With the Karoo's way back known, you are where it starts (its length before the rejoin point), drawn in red
        if (!rejoining && along != null) lastOnRouteKm = along
        _state.update { it.copy(alongKm = along, markerKm = if (rejoining && rejoinAtKm == null) it.markerKm ?: it.alongKm else null) }
    }

    /**
     * Km along the route nearest to [lat], [lng]. Where the route passes the spot more than once (within 150 m), the
     * pass nearest to where you left it. Null without the route line.
     */
    private fun routeKmAt(lat: Double, lng: Double): Double? {
        val g = _geo.value; if (g.isEmpty()) return null
        val near = g.map { it.km to Geo.distanceM(lat, lng, it.lat, it.lng) }
        val close = near.filter { it.second < 150 }
        val last = lastOnRouteKm
        return (if (close.isNotEmpty() && last != null) close.minByOrNull { kotlin.math.abs(it.first - last) } else near.minByOrNull { it.second })?.first
    }

    // Off route: our own check (more than OFF_LINE_M from the route line for OFF_LINE_MS) decides while the route line
    // is known; the Karoo's on-route flag only without one. The Karoo's flag came 32 s after leaving and its "back"
    // up to ~60 s after rejoining (route tests 2026-11-10); ours ~6 s, as Mountaineer's.
    private var karooOff = false
    private var ownOff: Boolean? = null
    private var awaySince = 0L

    private fun onLocation(lat: Double, lng: Double) {
        val line = _geo.value
        if (line.size < 2) { ownOff = null; awaySince = 0L; updateOffRoute(); return }
        val away = Geo.distanceToLineM(line, lat, lng)
        val now = SystemClock.elapsedRealtime()
        if (away <= OFF_LINE_M) { ownOff = false; awaySince = 0L }
        else { if (awaySince == 0L) awaySince = now; if (now - awaySince >= OFF_LINE_MS) ownOff = true }
        updateOffRoute()
    }

    private fun updateOffRoute() {
        val off = ownOff ?: karooOff
        if (off != _state.value.offRoute) {
            // exact tag: the route-test rounds grep "UpAheadSession: Off route" / "Back on route"
            Timber.tag("UpAheadSession").i(if (off) "Off route" else "Back on route")
            _state.update { it.copy(offRoute = off) }
        }
    }

    /** Where you were last on the route (km), to pick the right pass when the route crosses itself. */
    private var lastOnRouteKm: Double? = null

    /** The way back into the state: Open-Meteo's heights once known, else a straight slope from your altitude. */
    private fun publishWayBack() {
        val at = rejoinAtKm; val route = _state.value.route
        if (at == null || rejoinGeo.size < 2 || route == null || rejoinGeo.last().km < MIN_WAY_BACK_KM) { _state.update { it.copy(wayBack = null, rejoinAtKm = null, wayBackReal = false) }; return }
        val len = rejoinGeo.last().km
        val real = wayBackHeights
        val wb = real ?: listOf(0.0 to (altitude ?: route.elevAt(at)), len to route.elevAt(at))
        _state.update { it.copy(wayBack = wb, rejoinAtKm = at, wayBackReal = real != null) }
        Timber.i("Off route: rejoin at %.2f km, way back %.2f km, heights %s".format(java.util.Locale.US, at, len, if (real != null) "Open-Meteo" else "straight"))
    }

    /**
     * Ground heights for the way back from Open-Meteo (as Mountaineer does): up to 100 points along it, positions
     * rounded to ~100 m (enough for its 90 m height model). Kept only while it's still the same way back.
     */
    private suspend fun fetchWayBackHeights(key: String, g: List<GeoPoint>) {
        publishWayBack()
        // the path this was started for (rejoinGeo can change meanwhile: crash 2026-11-10, "List is empty")
        if (g.size < 2) return
        val len = g.last().km
        val n = (len * 1000 / 25).toInt().plus(1).coerceIn(2, 100)
        val samples = (0 until n).map { k -> len * k / (n - 1) }
        val ll = samples.map { d ->
            val i = g.indexOfFirst { it.km >= d }.let { if (it <= 0) 1 else it }
            val p0 = g[i - 1]; val p1 = g[i]
            val f = if (p1.km - p0.km < 1e-6) 0.0 else ((d - p0.km) / (p1.km - p0.km)).coerceIn(0.0, 1.0)
            (p0.lat + f * (p1.lat - p0.lat)) to (p0.lng + f * (p1.lng - p0.lng))
        }
        val url = "https://api.open-meteo.com/v1/elevation?latitude=" + ll.joinToString(",") { "%.3f".format(java.util.Locale.US, it.first) } +
            "&longitude=" + ll.joinToString(",") { "%.3f".format(java.util.Locale.US, it.second) }
        val body = withTimeoutOrNull(30_000) { httpGet(url) } ?: run { Timber.w("Way-back heights: no answer"); return }
        try {
            val a = org.json.JSONObject(body).getJSONArray("elevation")
            if (a.length() != samples.size || rejoinKey != key) return
            wayBackHeights = samples.mapIndexed { k, d -> d to a.getDouble(k) }
            publishWayBack()
        } catch (e: Exception) { Timber.w(e, "Way-back heights not understood") }
    }

    /** GET through the Karoo (Wi-Fi or phone). Null on any failure. */
    private suspend fun httpGet(url: String): String? = suspendCancellableCoroutine { cont ->
        var id: String? = null
        id = karoo.addConsumer<OnHttpResponse>(
            OnHttpResponse.MakeHttpRequest("GET", url, mapOf("User-Agent" to "UpAhead-Karoo"), waitForConnection = false),
            onError = { msg -> Timber.w("HTTP error: $msg"); if (cont.isActive) cont.resume(null) },
        ) { event ->
            val st = event.state
            if (st is HttpResponseState.Complete) {
                id?.let { karoo.removeConsumer(it) }
                val ok = st.statusCode in 200..299 && st.body != null
                if (cont.isActive) cont.resume(if (ok) st.body!!.decodeToString() else null)
            }
        }
        cont.invokeOnCancellation { id?.let { karoo.removeConsumer(it) } }
    }

    /** Learn speed once a second; work out the header's ETA every 10 seconds. */
    private suspend fun tick() {
        var last = SystemClock.elapsedRealtime()
        var n = 0
        while (scope.isActive) {
            delay(1000)
            val now = SystemClock.elapsedRealtime()
            val dt = (now - last) / 1000.0; last = now
            val s = _state.value
            val route = s.route; val along = s.alongKm; val v = speedMs
            if (route != null && along != null && v != null && recording) speeds.learn(v, route.grade(along - 0.05, along + 0.05), dt)
            if (n++ % 10 == 0) {
                val eta = if (route != null && along != null) Prefs.read(context).let { pr -> headTarget(route, along, pr.head, pr) }
                    ?.let { Eta.seconds(route, along, it.km, speeds, watts, massKg) } else null
                val cal = Calendar.getInstance()
                _state.update { it.copy(headEtaSec = if (recording || it.headEtaSec == null) smoothed.next(eta) else it.headEtaSec, clockMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)) }
                updateSelectedEta()
            }
        }
    }

    private fun geoLine(encoded: String): List<GeoPoint> {
        val ll = Polyline.decode(encoded, 1e5)
        val out = ArrayList<GeoPoint>(ll.size)
        var km = 0.0
        ll.forEachIndexed { i, (la, ln) ->
            if (i > 0) km += Geo.distanceM(ll[i - 1].first, ll[i - 1].second, la, ln) / 1000
            out += GeoPoint(km, la, ln)
        }
        return out
    }

    companion object {
        /** Taps on the box this soon after it opened are the doubled opening tap, not a close. */
        const val CLOSE_GUARD_MS = 1000L
        /** Two taps on the graph this close together are a double tap (Android's standard). */
        const val DOUBLE_TAP_MS = 300L
        /** Off route: farther than this from the route line (m) for [OFF_LINE_MS] (as Mountaineer). */
        const val OFF_LINE_M = 40.0
        const val OFF_LINE_MS = 5_000L
        /** A way back shorter than this (10 m) counts as none. */
        const val MIN_WAY_BACK_KM = 0.01
        /** The ranges that make sense on this route: the whole route, then only those shorter than it. */
        fun ranges(s: UpAheadState): List<Int> {
            val total = s.route?.totalKm ?: return RANGES
            val u = if (s.imperial) ProfileRenderer.KM_TO_MI else 1.0
            return RANGES.filter { it == 0 || it < total * u }
        }

        /**
         * The stop the header talks about: the next stop of any shown kind, the next cut-off, feed, water,
         * toilet, mechanic, summit (a POI or the top of one of the Karoo's climbs) or timing, or the finish.
         */
        fun headTarget(route: RouteModel, alongKm: Double, head: HeadInfo, p: Prefs = Prefs()): Stop? {
            val ahead = route.stops.filter { it.km > alongKm }
            val target = when (head) {
                HeadInfo.NEXT -> ahead.firstOrNull { st -> st.kinds.any { p.shows(it.group) } }
                HeadInfo.CUT -> ahead.firstOrNull { it.cut != null }
                HeadInfo.FEED -> ahead.firstOrNull { st -> st.kinds.any { it == Kind.FOOD || it == Kind.FEED } }
                HeadInfo.WATER -> ahead.firstOrNull { st -> st.kinds.any { it == Kind.WATER || it == Kind.FEED } }
                HeadInfo.TOILET -> ahead.firstOrNull { Kind.TOILET in it.kinds }
                HeadInfo.MECHANIC -> ahead.firstOrNull { st -> st.kinds.any { it == Kind.MECHANIC || it == Kind.BIKE_SHOP } }
                HeadInfo.TIMING -> ahead.firstOrNull { st -> st.kinds.any { it == Kind.TIMING_START || it == Kind.CHECKPOINT } }
                HeadInfo.SUMMIT -> {
                    val poi = ahead.firstOrNull { Kind.SUMMIT in it.kinds }
                    val top = route.summits.firstOrNull { it.first > alongKm }?.let { Stop(it.first, listOf(Kind.SUMMIT), null) }
                    listOfNotNull(poi, top).minByOrNull { it.km }
                }
                HeadInfo.FINISH -> Stop(route.totalKm, emptyList(), null)
                HeadInfo.OFF -> null
            }
            // Nothing of that kind left (after the last stop): the finish, so the header always says what's next
            return target ?: if (head == HeadInfo.OFF || head == HeadInfo.CUT) null else Stop(route.totalKm, emptyList(), null)
        }
    }
}

object Geo {
    /** Shortest distance (m) from [lat], [lng] to the polyline [line] (flat-earth per segment, fine at these sizes). */
    fun distanceToLineM(line: List<GeoPoint>, lat: Double, lng: Double): Double {
        val ky = 111_320.0; val kx = 111_320.0 * kotlin.math.cos(Math.toRadians(lat))
        var best = Double.MAX_VALUE
        for (i in 1 until line.size) {
            val ax = (line[i - 1].lng - lng) * kx; val ay = (line[i - 1].lat - lat) * ky
            val bx = (line[i].lng - lng) * kx; val by = (line[i].lat - lat) * ky
            val dx = bx - ax; val dy = by - ay; val l2 = dx * dx + dy * dy
            val f = if (l2 < 1e-9) 0.0 else (-(ax * dx + ay * dy) / l2).coerceIn(0.0, 1.0)
            val px = ax + f * dx; val py = ay + f * dy
            val d = px * px + py * py
            if (d < best) best = d
        }
        return kotlin.math.sqrt(best)
    }

    fun distanceM(la1: Double, ln1: Double, la2: Double, ln2: Double): Double {
        val r = 6371000.0
        val p1 = Math.toRadians(la1); val p2 = Math.toRadians(la2)
        val dp = p2 - p1; val dl = Math.toRadians(ln2 - ln1)
        val a = Math.sin(dp / 2).let { it * it } + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(a))
    }
}
