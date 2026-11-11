package com.daviddokakis.upahead

import java.text.Normalizer
import kotlin.math.abs

/** What a column groups by: one switch per group on the graph, and the rider's priority order. */
enum class Group { TIMING, CUTOFF, FOOD, WATER, TOILETS, MECHANIC, SUMMIT, OTHER }

/** Badge colours (mock round 10, picked 2026-11-10): rounded squares coloured by category. */
object BadgeColour {
    const val YELLOW = 0xFFFFE900.toInt()   // timing (Karoo yellow); climb3 #FFF500 tried, too close to tell apart (2026-11-11)
    const val RED = 0xFFD60404.toInt()      // cut-offs, cautions, medical: climb6, the Karoo's own red (native, 2026-11-11)
    const val FOOD = 0xFF007A49.toInt()     // success_green_700
    const val WATER = 0xFF005297.toInt()    // climb blue 600: water and toilets
    const val BIKE = 0xFF007A49.toInt()     // mechanic, bike shop: success_green_700
    const val SUMMIT = 0xFF636363.toInt()   // grey_600
    const val CONTROL = 0xFF214559.toInt()  // checkpoints: k2DarkBlue
    const val GREY = 0xFF1B2D2D.toInt()   // k2Black
    const val WHITE = 0xFFFFFFFF.toInt()
    const val BLACK = 0xFF000000.toInt()
}

/**
 * One icon on the graph: its glyph (`ic_poi_*`, white, tinted when drawn), group, badge colours and how
 * much of the square its ink fills (yellow and red as approved on the Karoo; the drop a tad smaller).
 */
/** [label]: what the icon means, in the stop box ("Water", "Cut-off"). */
enum class Kind(val label: String, val glyph: Int, val group: Group, val bg: Int, val fg: Int = BadgeColour.WHITE, val ink: Float = 0.64f) {
    TIMING_START("Timing start", R.drawable.ic_poi_timing_start, Group.TIMING, BadgeColour.YELLOW, BadgeColour.BLACK, 0.54f),
    TIMING_END("Timing end", R.drawable.ic_poi_timing_end, Group.TIMING, BadgeColour.YELLOW, BadgeColour.BLACK, 0.54f),
    UNTIMED("Untimed", R.drawable.ic_poi_untimed, Group.TIMING, BadgeColour.YELLOW, BadgeColour.BLACK, 0.54f),
    CHECKPOINT("Checkpoint", R.drawable.ic_poi_s_control, Group.TIMING, BadgeColour.CONTROL),
    CUTOFF("Cut-off", R.drawable.ic_poi_cutoff, Group.CUTOFF, BadgeColour.RED, ink = 0.54f),
    FINISH("Finish", R.drawable.ic_poi_finish, Group.TIMING, BadgeColour.CONTROL),
    FEED("Feed", R.drawable.ic_poi_s_feed, Group.FOOD, BadgeColour.FOOD),
    FOOD("Food", R.drawable.ic_poi_s_food, Group.FOOD, BadgeColour.FOOD),
    COFFEE("Coffee", R.drawable.ic_poi_s_coffee, Group.FOOD, BadgeColour.FOOD),
    SHOP("Shop", R.drawable.ic_poi_s_shop, Group.FOOD, BadgeColour.FOOD),
    WATER("Water", R.drawable.ic_poi_drop, Group.WATER, BadgeColour.WATER, ink = 0.56f),
    TOILET("Toilet", R.drawable.ic_poi_s_restroom, Group.TOILETS, BadgeColour.WATER),
    MECHANIC("Mechanic", R.drawable.ic_poi_mechanic, Group.MECHANIC, BadgeColour.BIKE),
    BIKE_SHOP("Bike shop", R.drawable.ic_poi_mechanic, Group.MECHANIC, BadgeColour.BIKE),   // the wrench: the Karoo's shop glyph is too detailed at 48 px
    MEDICAL("Medical", R.drawable.ic_poi_s_medical, Group.MECHANIC, BadgeColour.RED),
    FIRST_AID("First aid", R.drawable.ic_poi_s_first_aid, Group.MECHANIC, BadgeColour.RED),
    SUMMIT("Summit", R.drawable.ic_poi_s_summit, Group.SUMMIT, BadgeColour.SUMMIT),
    CAUTION("Caution", R.drawable.ic_poi_s_caution, Group.OTHER, BadgeColour.RED),
    VIEWPOINT("Viewpoint", R.drawable.ic_poi_s_viewpoint, Group.OTHER, BadgeColour.GREY),
    OTHER("Point", R.drawable.ic_poi_generic_dot, Group.OTHER, BadgeColour.GREY, ink = 0.3f),
}

/** One place on the route with everything there, at [km] along it. [cut]: a cut-off time ("16:40"). */
data class Stop(val km: Double, val kinds: List<Kind>, val name: String?, val cut: String? = null, val lat: Double = 0.0, val lng: Double = 0.0)

/** A timed (or untimed / neutral) section, from [startKm] to [endKm]. */
data class Section(val startKm: Double, val endKm: Double, val timed: Boolean)

/** A POI as the Karoo gives it (karoo-ext Symbol.POI), already placed along the route. */
data class RoutePoi(val type: String, val name: String?, val km: Double, val lat: Double, val lng: Double)

/**
 * The loaded route as UpAhead uses it: the elevation line (km, m), its stops and timed sections,
 * and the summits of the Karoo's own climbs.
 */
data class RouteModel(
    val name: String,
    val totalKm: Double,
    val elevation: List<Pair<Double, Double>>,
    val stops: List<Stop>,
    val sections: List<Section>,
    val summits: List<Pair<Double, Double>>,
) {
    /** Elevation (m) at [km], straight between the line's points. */
    fun elevAt(km: Double): Double {
        if (elevation.isEmpty()) return 0.0
        if (km <= elevation.first().first) return elevation.first().second
        if (km >= elevation.last().first) return elevation.last().second
        var lo = 0; var hi = elevation.size - 1
        while (hi - lo > 1) { val mid = (lo + hi) / 2; if (elevation[mid].first <= km) lo = mid else hi = mid }
        val (k0, e0) = elevation[lo]; val (k1, e1) = elevation[hi]
        return if (k1 - k0 < 1e-9) e0 else e0 + (e1 - e0) * (km - k0) / (k1 - k0)
    }

    /** Grade (%) between [a] and [b] km. */
    fun grade(a: Double, b: Double): Double = if (b - a < 1e-6) 0.0 else (elevAt(b) - elevAt(a)) / ((b - a) * 10)

    companion object {
        /** POIs closer than this along the route are one stop (a feed and its timing mat, say). */
        const val SAME_STOP_KM = 0.15
        const val OPEN_SEGMENT_KM = 25.0

        fun build(
            name: String, totalKm: Double, elevation: List<Pair<Double, Double>>,
            pois: List<RoutePoi>, climbs: List<Pair<Double, Double>>, reversed: Boolean = false,
        ): RouteModel {
            val flip = { km: Double -> if (reversed) totalKm - km else km }
            val elev = (if (reversed) elevation.map { (k, e) -> flip(k) to e }.reversed() else elevation)
            val stops = mergeStops(pois.mapNotNull { p -> PoiText.stop(p.type, p.name, flip(p.km), p.lat, p.lng) }.sortedBy { it.km })
            val tmp = RouteModel(name, totalKm, elev, stops, emptyList(), emptyList())
            // The Karoo sometimes sends no climbs for a route (our commute, 2026-11-10): find them ourselves
            val climbs = climbs.ifEmpty { Climbs.detect(tmp) }
            val summits = climbs.map { (start, length) -> val top = flip(if (reversed) start else start + length); top to tmp.elevAt(top) }
                .sortedBy { it.first }
            return tmp.copy(sections = sections(stops, summits), summits = summits)
        }

        private fun mergeStops(sorted: List<Stop>): List<Stop> {
            val out = mutableListOf<Stop>()
            for (s in sorted) {
                val last = out.lastOrNull()
                if (last != null && abs(s.km - last.km) < SAME_STOP_KM) {
                    out[out.size - 1] = last.copy(
                        kinds = (last.kinds + s.kinds).distinct(),
                        name = PoiText.betterName(last.name, s.name),
                        cut = last.cut ?: s.cut,
                    )
                } else out += s
            }
            return out
        }

        /**
         * Timing start → the next timing end; untimed start → the next untimed end. A timing start with
         * no end (a Strava "Segment Start", say) runs to the next summit: a POI summit, else the top of
         * the Karoo's next climb, within [OPEN_SEGMENT_KM].
         */
        private fun sections(stops: List<Stop>, climbTops: List<Pair<Double, Double>>): List<Section> {
            val out = mutableListOf<Section>()
            var timedFrom: Double? = null
            var untimedFrom: Double? = null
            for (s in stops) {
                val t = s.name?.let { PoiText.timing(it) }
                // Timed start / end by kind: from the name or from the Karoo's segment_start / segment_end types
                if (Kind.TIMING_END in s.kinds && timedFrom != null) { out += Section(timedFrom, s.km, true); timedFrom = null }
                if (t == PoiText.Timing.UNTIMED_END && untimedFrom != null) { out += Section(untimedFrom, s.km, false); untimedFrom = null }
                if (Kind.TIMING_START in s.kinds) timedFrom = s.km
                if (t == PoiText.Timing.UNTIMED_START) untimedFrom = s.km
            }
            timedFrom?.let { from ->
                val top = stops.firstOrNull { it.km > from && Kind.SUMMIT in it.kinds }?.km ?: climbTops.firstOrNull { it.first > from }?.first
                if (top != null && top - from <= OPEN_SEGMENT_KM) out += Section(from, top, true)
            }
            return out
        }
    }
}

/**
 * Reads what a POI is from its Karoo type and its name. The Karoo has 35 POI types and nothing for
 * timing, cut-offs or "food, water and toilets here", so those come from words in the name, in
 * English, Spanish, Catalan, French, German, Italian and Swedish ("Puig Major: food water wc mechanic",
 * "Timing start", "Cut-off 16:40").
 */
object PoiText {
    enum class Timing { START, END, UNTIMED_START, UNTIMED_END }

    private fun plain(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    private fun words(vararg w: String) = Regex("(?<![a-z])(" + w.joinToString("|") + ")(?![a-z])")

    private val FOOD = words("food", "feed", "feeding", "eat", "comida", "menjar", "avituallamiento", "avituallament", "avituallaments",
        "ravito", "ravitaillement", "verpflegung", "ristoro", "matstation")
    private val WATER = words("water", "agua", "aigua", "eau", "wasser", "acqua", "vatten", "bottles?", "bidons?")
    private val TOILET = words("wc", "toilets?", "restrooms?", "lavabos?", "aseos?", "banys?", "banos?", "toilettes?", "klo", "bagni", "bagno", "toaletter?")
    private val MECHANIC = words("mechanic", "mechanics", "mecanico", "mecanica", "mecanic", "mecanicien", "mechaniker", "meccanico", "mekaniker", "repair", "tools")
    private val MEDICAL = words("medical", "medic", "first aid", "medico", "metge", "ambulance", "ambulancia", "sanitario", "erste hilfe", "sjukvard", "soccorso")
    private val CUT = Regex("(?<![a-z])(cut[ -]?off|cutoff|cierre|tancament|temps (de )?tall|tiempo (de )?corte|hora (de )?(tall|corte)|barriere|zeitlimit|karenzzeit|tempo massimo|tidsgrans)(?![a-z])")
    /** Heads-up signs, not stops: "10km pròxim avituallament", "1km Perill vies tren", "10km Meta". */
    private val HEADS_UP = Regex("^\\s*\\d+([.,]\\d+)?\\s*kms?(?![a-z])")
    private val CAUTION = words("corba perillosa", "curva peligrosa", "curva pericolosa", "virage dangereux", "dangerous", "danger", "perill", "peligro",
        "vies tren", "vias tren", "railway", "level crossing", "caution", "warning", "achtung", "atencio", "atencion")
    /** A climb's start ("Inici Coll de Femenia"): the Karoo finds climbs itself, so it's just "other". */
    private val CLIMB_START = Regex("^\\s*(inici|inicio|start of|debut|beginn)(?![a-z])")
    private val TIME = Regex("(?<!\\d)([01]?\\d|2[0-3])[:.h]([0-5]\\d)(?!\\d)")
    private val TIMED = "(timing|timed|chrono|crono|cronometr\\w*|zeitnahme|tidtagning|segment|kom)"
    private val UNTIMED = "(untimed|neutral\\w*|sin crono|sense crono|non chrono\\w*|ohne zeitnahme)"
    private val STARTS = "(start|starts|inicio|inici|debut|begin|beginn|comienzo)"
    private val ENDS = "(end|ends|stop|finish|fin|final|ende|slut|mal|arrivee)"

    private fun near(a: String, b: String) = Regex("(?<![a-z])$a(?![a-z]).{0,12}(?<![a-z])$b(?![a-z])|(?<![a-z])$b(?![a-z]).{0,12}(?<![a-z])$a(?![a-z])")
    private val UNTIMED_START = near(UNTIMED, STARTS)
    private val UNTIMED_END = near(UNTIMED, ENDS)
    private val TIMED_START = near(TIMED, STARTS)
    private val TIMED_END = near(TIMED, ENDS)

    /** Timing in [name], if any. Untimed is checked first ("neutral start" isn't a timing start). */
    fun timing(name: String): Timing? {
        val n = plain(name)
        return when {
            UNTIMED_START.containsMatchIn(n) -> Timing.UNTIMED_START
            UNTIMED_END.containsMatchIn(n) -> Timing.UNTIMED_END
            TIMED_START.containsMatchIn(n) -> Timing.START
            TIMED_END.containsMatchIn(n) -> Timing.END
            else -> null
        }
    }

    /**
     * A cut-off in [name]: its time ("Cut-off 16:40" → "16:40"), "" for a cut-off without a time in the
     * name ("Temps tall 312": organisers often put the time in the description, which the Karoo doesn't
     * pass on), or null if it isn't a cut-off.
     */
    fun cutoff(name: String): String? {
        val n = plain(name)
        if (!CUT.containsMatchIn(n)) return null
        val m = TIME.find(n) ?: return ""
        return "${m.groupValues[1].toInt()}:${m.groupValues[2]}"
    }

    /**
     * The name to show for a stop that merges POIs: a place's name ("Puig Major") over a timing or cut-off
     * marker's ("Timing end", "Cut-off 11:20").
     */
    fun betterName(a: String?, b: String?): String? {
        fun marker(n: String?) = n == null || timing(n) != null || cutoff(n) != null
        return if (marker(a) && !marker(b)) b else a ?: b
    }

    /** A name as the info box shows it: without the services list after a colon ("Feed 2: food water" → "Feed 2"). */
    fun displayName(name: String): String = name.substringBefore(':').trim().ifEmpty { name }

    /** One POI → a stop: services from the name first, then the Karoo type. Null for a heads-up sign. */
    fun stop(type: String, name: String?, km: Double, lat: Double, lng: Double): Stop? {
        val n = name?.let { plain(it) } ?: ""
        if (HEADS_UP.containsMatchIn(n)) return null
        val kinds = mutableListOf<Kind>()
        when (name?.let { timing(it) }) {
            Timing.START -> kinds += Kind.TIMING_START
            Timing.END -> kinds += Kind.TIMING_END
            Timing.UNTIMED_START, Timing.UNTIMED_END -> kinds += Kind.UNTIMED
            null -> {}
        }
        val cut = name?.let { cutoff(it) }
        if (cut != null) kinds += Kind.CUTOFF
        if (FOOD.containsMatchIn(n)) kinds += Kind.FOOD
        if (WATER.containsMatchIn(n)) kinds += Kind.WATER
        if (TOILET.containsMatchIn(n)) kinds += Kind.TOILET
        if (MECHANIC.containsMatchIn(n)) kinds += Kind.MECHANIC
        if (MEDICAL.containsMatchIn(n)) kinds += Kind.MEDICAL
        if (kinds.isEmpty() && CAUTION.containsMatchIn(n)) kinds += Kind.CAUTION
        if (kinds.isEmpty() && CLIMB_START.containsMatchIn(n)) kinds += Kind.OTHER
        if (kinds.isEmpty()) kinds += byType(type)
        return Stop(km, kinds.distinct(), name?.takeIf { it.isNotBlank() }, cut, lat, lng)
    }

    /** The Karoo's POI type (karoo-ext Symbol.POI.Types) when the name says nothing more. */
    fun byType(type: String): Kind = when (type) {
        "aid_station", "rest_stop" -> Kind.FEED
        "food", "bar", "winery" -> Kind.FOOD
        "coffee" -> Kind.COFFEE
        "convenience_store", "shopping", "gas_station" -> Kind.SHOP
        "water" -> Kind.WATER
        "restroom", "shower" -> Kind.TOILET
        "bike_shop" -> Kind.BIKE_SHOP
        "first_aid" -> Kind.FIRST_AID
        "hospital" -> Kind.MEDICAL
        "control" -> Kind.CHECKPOINT
        // Ride with GPS types the Karoo passes on (2026-11-10, our commute test): finish, start, stop
        "finish" -> Kind.FINISH
        "start" -> Kind.TIMING_START
        "stop" -> Kind.TIMING_END
        // Not in karoo-ext's list, but the Karoo gives them for Strava waypoints (Mallorca 312, 2026-11-10)
        "segment_start" -> Kind.TIMING_START
        "segment_end" -> Kind.TIMING_END
        "summit" -> Kind.SUMMIT
        "caution" -> Kind.CAUTION
        "viewpoint", "monument", "park" -> Kind.VIEWPOINT
        else -> Kind.OTHER
    }
}

/**
 * Climbs found in the elevation line, for when the Karoo sends none: from a low point up to the top, ended
 * when the road drops more than 10 m (or a quarter of the climb's gain) below it. Kept when at least
 * 0.5 km long, 3 % on average and 20 m up. Returns (start km, length km) like the Karoo's climbs.
 */
object Climbs {
    fun detect(route: RouteModel, stepKm: Double = 0.1): List<Pair<Double, Double>> {
        val n = (route.totalKm / stepKm).toInt()
        if (n < 2) return emptyList()
        val km = DoubleArray(n + 1) { it * stepKm }
        val e = DoubleArray(n + 1) { route.elevAt(km[it]) }
        val out = mutableListOf<Pair<Double, Double>>()
        var start = 0
        var top = 0
        for (i in 1..n) {
            if (e[i] <= e[start] && top == start) { start = i; top = i; continue }   // flat or down: the climb starts later
            if (e[i] > e[top]) top = i
            val gain = e[top] - e[start]
            if (top > start && e[top] - e[i] > maxOf(10.0, gain / 4)) {
                val length = km[top] - km[start]
                if (length >= 0.5 && gain >= 20 && gain / (length * 10) >= 3.0) out += km[start] to length
                start = i; top = i
            }
        }
        val gain = e[top] - e[start]; val length = km[top] - km[start]
        if (top > start && length >= 0.5 && gain >= 20 && gain / (length * 10) >= 3.0) out += km[start] to length
        return out
    }
}

/** Google encoded polylines (karoo-ext: route precision 5, elevation pairs precision 1). */
object Polyline {
    fun decode(encoded: String, factor: Double): List<Pair<Double, Double>> {
        val out = ArrayList<Pair<Double, Double>>()
        var i = 0; var a = 0L; var b = 0L
        fun next(): Long {
            var shift = 0; var result = 0L
            while (true) {
                val c = encoded[i++].code - 63
                result = result or ((c and 0x1f).toLong() shl shift)
                shift += 5
                if (c < 0x20) break
            }
            return if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        }
        while (i < encoded.length) {
            a += next(); if (i >= encoded.length) break
            b += next()
            out += a / factor to b / factor
        }
        return out
    }

    fun encode(points: List<Pair<Double, Double>>, factor: Double = 1e5): String {
        val sb = StringBuilder()
        var pa = 0L; var pb = 0L
        fun put(v: Long) {
            var x = if (v < 0) (v shl 1).inv() else v shl 1
            while (x >= 0x20) { sb.append(((0x20 or (x and 0x1f).toInt()) + 63).toChar()); x = x shr 5 }
            sb.append((x + 63).toInt().toChar())
        }
        for ((a, b) in points) {
            val la = Math.round(a * factor); val lb = Math.round(b * factor)
            put(la - pa); put(lb - pb); pa = la; pb = lb
        }
        return sb.toString()
    }
}
