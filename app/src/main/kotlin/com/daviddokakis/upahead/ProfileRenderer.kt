package com.daviddokakis.upahead

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import com.dubadai.karoo.common.KarooType
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The UpAhead field, any size, as one bitmap (design: docs/design/mock, rounds 1–6):
 * - header like the Karoo's own: graph icon, the header info (next cut / feed / water / toilet, as
 *   distance or time) on the left, the range as just km on the right;
 * - the elevation graph edge to edge, km in a strip under it; ridden part grey; a line with a km tag is you;
 * - every stop as a column of style-B badges in a band above the graph (never on it), a straight
 *   dashed line down to its km; top priority nearest the graph, the rest as "+n"; stops closer than an
 *   icon share a column; only the next cut-off; timed sections as a yellow strip along the bottom.
 */
class ProfileRenderer(private val context: Context) {
    private val drawables = HashMap<Pair<Int, Int>, Drawable>()
    /** Glyph size in its circle, × this (1 = as designed). Only the debug icon test page changes it. */
    var glyphBoost = 1f

    fun render(s: UpAheadState, p: Prefs, w: Int, h: Int, night: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), s, p, w, h, night)
        return bmp
    }

    // The Karoo's own palette (karoo-common docs/design/native-colours, 2026-11-11): grey_200/300/600,
    // climb6 red, success_green_600/700; nothing made up per app
    private class Colours(night: Boolean) {
        val ink = if (night) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
        val muted = if (night) 0xFFBCBCBC.toInt() else 0xFF636363.toInt()        // grey_300 / grey_600
        val red = KarooType.HEART_RED   // climb6 #D60404, the Karoo's own red (red_600 was weak on the yellow card)
        val green = if (night) 0xFF0DBA75.toInt() else 0xFF007A49.toInt()          // success_green_600 / 700
        val riddenLine = if (night) 0xFF636363.toInt() else 0xFFBCBCBC.toInt()     // grey_600 / grey_300
        val riddenFill = if (night) 0x8C636363.toInt() else 0x99DDDDDD.toInt()     // grey_600 / grey_200, see-through
    }

    fun draw(c: Canvas, s: UpAheadState, p: Prefs, w: Int, h: Int, night: Boolean) {
        hits.clear(); tapTop = 0f; tapBottom = 0f
        val col = Colours(night)
        // Background (picked 2026-11-10, B): softer than the Karoo's pure white / black, a gradient from white to a
        // faint sky blue (night: charcoal to near black)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), if (night) BG_TOP_NIGHT else BG_TOP_DAY,
                if (night) BG_BOTTOM_NIGHT else BG_BOTTOM_DAY, android.graphics.Shader.TileMode.CLAMP) })
        // Header text a size up from the Karoo's own (2026-11-10): 23 → 26 px caps, 20 → 22 on small tiles
        val caps = KarooType.headerCapsPx(h) + if (h < 150) 2 else 3
        val base = (KarooType.headerCapsTop(h, w > 300) + caps).toFloat()
        val label = KarooType.labelPaint(caps)
        headBase = base; headCaps = caps.toFloat()
        val route = s.route
        val u = if (s.imperial) KM_TO_MI else 1.0
        val unit = if (s.imperial) "MI" else "KM"

        // Header: icon, then the info; the range on the right
        val box = KarooType.headerIconBoxPx(caps).toFloat()
        // The graph icon only without a route (it reads like an app icon); with one, the info starts at the edge (2026-11-10)
        if (route == null) KarooType.drawGlyph(c, KarooType.Glyph.GRADE, KarooType.HEADER_ICON_LEFT_PX.toFloat(), base, box, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col.ink })
        // No range in the header (like the Karoo's own graph: the km ticks show it, and a scale bar flashes on a tap)
        val rangeText = if (route == null) "UP AHEAD" else ""
        label.color = col.ink
        // The header's info and the range: both if they fit; else the info shortened ("FEED 11.3");
        // else the info alone. The next feed or cut-off matters more than the range on a small tile.
        val infoLeft = KarooType.HEADER_ICON_LEFT_PX + if (route == null) box + 8 else 0f
        val rangeRight = w - KarooType.HEADER_RIGHT_INSET_PX.toFloat()
        // Icons in the header are as tall as the capitals and stand on the baseline, like the Karoo's own
        // The range on its own, no eye in front (too heavy; 2026-11-10)
        val rangeW = label.measureText(rangeText)
        val withRange = if (rangeText.isEmpty()) rangeRight - infoLeft else rangeRight - rangeW - 12 - infoLeft
        val alone = rangeRight - infoLeft
        val wayLen = s.wayBack?.takeIf { s.offRoute }?.lastOrNull()?.first
        val options = if (route != null && wayLen == null && s.offRoute) listOf(listOf("OFF ROUTE" to col.red))
            else if (route != null && wayLen != null) listOf(listOf("REJOIN IN ${fmt(wayLen * u)} $unit" to col.red), listOf("REJOIN IN ${fmt(wayLen * u)}" to col.red), listOf("REJOIN ${fmt(wayLen * u)}" to col.red))
            else if (route != null && s.alongKm != null) headInfo(route, s, p, w < 300, col, u, unit) else emptyList()
        // A stop's icon in the header ("@KIND") is drawn bare, in the text colour, no square behind it
        val partW = { t: String -> if (t.startsWith("@")) headGlyphWidth(Kind.valueOf(t.drop(1)), caps.toFloat()) + 6 else label.measureText(t) }
        val width = { l: List<Pair<String, Int>> -> l.sumOf { partW(it.first).toDouble() }.toFloat() }
        val info = options.firstOrNull { width(it) <= withRange }
        val infoAlone = if (info == null) options.firstOrNull { width(it) <= alone } else null
        if (infoAlone == null) {
            c.drawText(rangeText, rangeRight, base, label)
        }
        (info ?: infoAlone)?.let { list ->
            val lp = Paint(label).apply { textAlign = Paint.Align.LEFT }
            var x = infoLeft
            for ((t, color) in list) {
                if (t.startsWith("@")) { val k = Kind.valueOf(t.drop(1)); headGlyph(c, k, x, base, caps.toFloat(), col.ink); x += headGlyphWidth(k, caps.toFloat()) + 6; continue }
                lp.color = color; c.drawText(t, x, base, lp); x += lp.measureText(t)
            }
        }

        if (route == null || route.elevation.size < 2) {
            val text = if (route == null) "NO ROUTE" else "NO ELEVATION"
            c.drawText(text, w / 2f, KarooType.emptyBaseline(h), KarooType.emptyPaint(text, w).apply { color = col.muted })
            return
        }
        // Off route (2026-11-10): the Karoo's way back is its own lead-in, ending where it meets the route. You are at
        // its start, the way back's length before the rejoin point; the route is drawn only from there on.
        // only while off route (the Karoo keeps its way back a while after you're back: route test 2026-11-10)
        val wayBack = s.wayBack?.takeIf { it.size >= 2 && s.offRoute }
        val rejoinKm = s.rejoinAtKm?.takeIf { wayBack != null }
        val offRoute = rejoinKm != null
        val you = if (rejoinKm != null) rejoinKm - wayBack!!.last().first else s.alongKm
        // Your line stays where you left the route while you ride back to it (the distances stay real)
        val marker = if (offRoute) you else s.markerKm ?: you
        val rangeKm = s.range / u
        // off route nothing lies behind you (the way back starts at you): the graph starts at your line, no empty space
        val k0 = if (offRoute && you != null) (if (s.range > 0) you else min(0.0, you))
            // on route: you start at the left edge; once you've ridden 10 % of the range, 10 % stays in view behind you
            // (grey), so the graph grows a short tail as you go (feedback, 2026-11-10)
            else if (s.range > 0 && you != null) max(0.0, (marker ?: you) - rangeKm * 0.1) else 0.0
        val k1 = if (s.range > 0) min(route.totalKm, k0 + rangeKm) else route.totalKm
        if (k1 - k0 < 0.05) return
        // The tap area (next range) is the header plus a strip under it; the icons start below it
        // Fields 300 px and up: a strip under the graph for the km, like the Karoo's own (2026-11-10)
        val axisH = if (h >= 300) AXIS_H else 0f
        // Inside the tile like the Karoo's own content: 12 px from the left, 9 px from the right, and off the bottom
        // border on tiles without a km strip (mock, 2026-11-11)
        val left = EDGE_L; val right = w - EDGE_R; val bottom = h - axisH - if (h < 300) EDGE_B else 0f
        // The km tag sits under the header; the stops start below it, never touching it (2026-11-10)
        val tagTop = base + 10; val tagH = if (h < 300) 28f else 36f
        val plotTop = tagTop + tagH + 8
        tapTop = tagTop; tapBottom = bottom
        val x = { km: Double -> (left + (km - k0) / (k1 - k0) * (right - left)).toFloat() }

        // Icon size: never under 48 px (≈ 26 dp; picked on the Karoo 2026-11-10), so it stays readable on the bike
        val compact = h < 300
        val large = p.iconSize == IconSize.LARGE
        val icon = when { compact -> if (large) 58f else 48f; h >= 600 -> if (large) 86f else 56f; else -> if (large) 72f else 48f }

        // Stops in view (shown groups only, the next cut-off only). Never merged (2026-11-10): each keeps its
        // own column at its km; one too close to another goes up a lane, its straight line running behind the
        // icons below. At most 3 lanes on a full page, 2 on a half page, 1 on a tile (fewer lanes, 2026-11-10):
        // stops are placed by importance (ahead of you first, then the rider's priority, then nearest), each
        // in the lowest lane where it fits; the ones that don't fit are left out.
        val nextCut = route.stops.firstOrNull { it.cut != null && (you == null || it.km > you) }
        class Column(val km: Double, val x: Float, val kinds: List<Kind>, var lane: Int = 0)
        val band = (bottom - plotTop) * (if (compact) 0.62f else 0.55f)
        val maxLanes = minOf(max(1, floor(band / (icon + 3)).toInt()), if (compact) 1 else if (h >= 600) 3 else 2)
        val candidates = route.stops.filter { it.km in k0..k1 && (rejoinKm == null || it.km >= rejoinKm) && (!p.hidePassed || you == null || it.km >= you) }.mapNotNull { st ->
            val kinds = st.kinds.filter { p.shows(it.group) && (it != Kind.CUTOFF || st === nextCut) }
            if (kinds.isEmpty()) null else Column(st.km, x(st.km).coerceIn(left + icon / 2 + 4, right - icon / 2 - 4), kinds)
        }
        // Ahead of you first. Small tiles (one lane): the nearest next, so the stop the header names is the one
        // drawn (2026-11-10); bigger fields: the rider's priority, then the nearest
        val importance = if (compact) compareBy<Column>({ if (you != null && it.km < you) 1 else 0 }, { kotlin.math.abs(it.km - (you ?: k0)) })
            else compareBy<Column>({ if (you != null && it.km < you) 1 else 0 }, { col -> col.kinds.minOf { p.rank(it.group) } }, { kotlin.math.abs(it.km - (you ?: k0)) })
        val placed = Array(maxLanes) { mutableListOf<Float>() }
        val columns = mutableListOf<Column>()
        for (col in candidates.sortedWith(importance)) {
            val lane = (0 until maxLanes).firstOrNull { l -> placed[l].none { kotlin.math.abs(it - col.x) < icon + 4 } } ?: continue
            col.lane = lane; placed[lane] += col.x; columns += col
        }
        columns.sortBy { it.x }
        val lanes = (columns.maxOfOrNull { it.lane } ?: -1) + 1
        val plusPx = (icon * 0.5f).roundToInt() + 6
        val maxStack = max(1, floor((band / max(1, lanes) - plusPx) / (icon + 3)).toInt())
        fun shown(col: Column): Pair<List<Kind>, Int> {
            val sorted = col.kinds.sortedBy { p.rank(it.group) }
            if (compact) {
                val t = sorted.first()
                val one = if ((t.group == Group.FOOD || t.group == Group.WATER) && col.kinds.size > 2) Kind.FEED else t
                return listOf(one) to 0
            }
            return if (sorted.size <= maxStack) sorted to 0 else sorted.take(maxStack) to sorted.size - maxStack
        }
        val tallest = columns.maxOfOrNull { shown(it).first.size } ?: 0
        val plusRow = if (columns.any { shown(it).second > 0 }) plusPx else 0
        val laneH = tallest * (icon + 3) + plusRow
        val nextAhead = route.stops.firstOrNull { st -> you != null && st.km > you && st.kinds.any { p.shows(it.group) } }
        // (not on small tiles: their header already names the next stop)
        val nextBeyond = !compact && s.range > 0 && nextAhead != null && nextAhead.km > k1
        val bandH = max(if (tallest > 0) lanes * laneH else 0f, if (nextBeyond) 34f else 0f)
        val showSummits = p.showSummits && !compact && (s.range > 0 || h >= 600)
        val profTop = plotTop + bandH + if (showSummits) 30 else 12

        // The graph's vertical scale: the window's lowest to highest, some fill kept under the lowest
        val n = 360
        val pts = (0..n).map { i -> val km = k0 + (k1 - k0) * i / n; km to route.elevAt(km) }
        var lo = pts.minOf { it.second }; var hi = pts.maxOf { it.second }
        // off route: the scale includes your altitude, so the way back stays inside the graph
        s.wayBack?.let { wb -> lo = min(lo, wb.minOf { it.second }); hi = max(hi, wb.maxOf { it.second }) }
        val span = max(hi - lo, if (s.range in 1..5) 40.0 else 150.0)
        val floorPx = min(28f, (bottom - profTop) * 0.15f)
        val y = { e: Double -> (bottom - floorPx - (e - lo) / span * (bottom - floorPx - profTop)).toFloat() }
        // Icons sit just above the graph's highest point in view, not at the top of a tall field
        // (a flat route on a full page; 2026-11-10): the band moves down by the room the graph leaves.
        // Half page and up: the lowest row of stops sits at the field's middle at most (feedback, 2026-11-11), so the
        // lower half stays free for zoom taps; higher is fine
        val drop = max(0f, y(hi) - profTop).let { if (compact) it else min(it, max(0f, h / 2f - (plotTop + bandH - icon / 2 - 3))) }

        fun line(from: Double, to: Double): Path { val path = Path(); var first = true
            for ((km, e) in pts) { if (km < from || km > to) continue; if (first) path.moveTo(x(km), y(e)) else path.lineTo(x(km), y(e)); first = false }
            return path }
        // The fill stops exactly at the km strip (with one, no overlap into it); without, just past the tile's edge
        val fillBottom = if (axisH > 0) bottom else bottom + 2
        fun area(from: Double, to: Double): Path = line(from, to).apply { lineTo(x(min(to, k1)), fillBottom); lineTo(x(max(from, k0)), fillBottom); close() }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; strokeJoin = Paint.Join.ROUND }
        // off route the route starts at the rejoin point (nothing ridden or skipped before it is drawn)
        val split = (rejoinKm ?: marker)?.coerceIn(k0, k1) ?: k0
        // One style (Detailed dropped, 2026-11-10), the Karoo's own Upcoming Elevation look (picked K1): the part ahead filled in
        // translucent Karoo yellow under a yellow line. The ridden part plainly behind you: a grey band (fading
        // in from the top), a grey fill and a grey line.
        if (split > k0 && !offRoute) {
            // the grey band fades in from the top, strongest at the bottom (feedback, 2026-11-10)
            c.drawRect(x(k0), plotTop, x(split), bottom, Paint().apply { shader = android.graphics.LinearGradient(0f, plotTop, 0f, bottom,
                if (night) 0x00FFFFFF else 0x00000000, if (night) 0x40FFFFFF else 0x38000000, android.graphics.Shader.TileMode.CLAMP) })
            fill.color = col.riddenFill; c.drawPath(area(k0, split), fill)
            stroke.color = col.riddenLine; stroke.strokeWidth = 2.5f; c.drawPath(line(k0, split), stroke)
        }
        // Dark: translucent Karoo yellow under a bright yellow line (picked 2026-11-10). Light: the Karoo's own
        // Upcoming Elevation colours, measured on the device: solid khaki fill, olive-yellow 4 px line.
        fill.color = if (night) 0x59FFE900 else KAROO_FILL_DAY; c.drawPath(area(split, k1), fill)
        stroke.color = if (night) 0xFFFFE900.toInt() else KAROO_LINE_DAY; stroke.strokeWidth = if (night) 3f else 4f
        c.drawPath(line(split, k1), stroke)
        if (!compact) {
            // Heights on the left at a round step (2–4 in view), each on a thin line across (feedback: keep the lines, else
            // the numbers mean little); km along the bottom, a tick with the km to its right. Light and dark alike; not on
            // small tiles (no room).
            val gp = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.5f; color = if (night) 0x55FFFFFF else 0x40000000 }
            val stepE = listOf(10.0, 20.0, 25.0, 50.0, 100.0, 200.0, 250.0, 500.0, 1000.0).first { span / it <= 4 }
            val lp = KarooType.labelPaint(if (compact) MIN_CAPS else 18).apply { color = col.muted; textAlign = Paint.Align.LEFT }   // heights in the Karoo's grey (feedback, 2026-11-11)
            var e = kotlin.math.ceil(lo / stepE) * stepE
            while (h >= 600 && e <= lo + span) {   // heights and their lines on the full page only (2026-11-10)
                val gy = y(e)
                if (gy > profTop + 8 && gy < bottom - 4) {
                    c.drawLine(left, gy, right, gy, gp)
                    c.drawText(if (s.imperial) "${(e * M_TO_FT).roundToInt()}" else "${e.roundToInt()}", left, gy - 5, lp)
                }
                e += stepE
            }
            val stepK = listOf(0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 25.0, 50.0, 100.0).first { (k1 - k0) * u / it <= 5 } / u
            val kp = KarooType.labelPaint(KM_CAPS.toInt()).apply { color = col.ink; textAlign = Paint.Align.LEFT }
            var kk = kotlin.math.ceil(k0 / stepK) * stepK
            while (kk < k1) {
                val kx = x(kk)
                val t = fmt(kk * u, whole = stepK * u >= 1)
                // the whole label inside the tile (a cut "|3" at the right edge, seen on the device 2026-11-10)
                if (kx > left + 20 && kx + 5 + kp.measureText(t) < right - 6 && (rejoinKm == null || kk >= rejoinKm)) {
                    // the tick from the graph's edge (touching the fill) down to the digits' baseline
                    val kb = bottom + (axisH + KM_CAPS) / 2   // as much room above the digits as below them
                    fill.color = col.ink; c.drawRect(kx - 1.5f, bottom, kx + 1.5f, kb, fill); c.drawText(t, kx + 5, kb, kp)
                }
                kk += stepK
            }
        }

        // Off route: the Karoo's way back in red, from you (its start) to where it meets the route. Its heights from
        // Open-Meteo (a solid line); until they come, or without a connection, a straight slope from your altitude
        // (dashed: an estimate).
        val wb = wayBack; val from = you
        if (rejoinKm != null && wb != null && from != null && rejoinKm > k0) {
            val xAt = { d: Double -> x(from + d) }
            val a = xAt(0.0); val b = xAt(wb.last().first)
            c.save(); c.clipRect(left, plotTop, right, bottom)
            val red = col.red
            val edge = Path().apply { wb.forEachIndexed { i, (d, e) -> if (i == 0) moveTo(xAt(d), y(e)) else lineTo(xAt(d), y(e)) } }
            c.drawPath(Path(edge).apply { lineTo(b, bottom); lineTo(a, bottom); close() }, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (red and 0x00FFFFFF) or (if (night) 0x8C000000.toInt() else 0x80000000.toInt()) })
            c.drawPath(edge, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = red; strokeWidth = 5f; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND
                if (!s.wayBackReal) pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f) })
            c.restore()
        }

        // Timed sections: yellow and black stripes on the graph's line itself, over the stretch that's timed (2026-11-10)
        val tiger = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = if (compact) 6f else 8f; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.BUTT }
        for (sec in route.sections.filter { it.timed && p.showTiming }) {
            if (sec.endKm < k0 || sec.startKm > k1) continue
            val path = line(max(sec.startKm, k0), min(sec.endKm, k1))
            tiger.color = 0xFFFFE900.toInt(); tiger.pathEffect = null; c.drawPath(path, tiger)
            tiger.color = 0xFF000000.toInt(); tiger.pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f); c.drawPath(path, tiger)
        }

        // Columns: lane 0 nearest the graph; a straight dashed line down to the graph (all lines first, so a
        // higher lane's line runs behind the icons below it)
        val rowBottom0 = plotTop + bandH - icon / 2 - 3 + drop
        val rowOf = { col: Column -> rowBottom0 - col.lane * laneH }
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.6f; color = col.ink; pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f) }
        val plus = KarooType.labelPaint((icon * 0.36f).roundToInt()).apply { textAlign = Paint.Align.CENTER; color = col.ink }
        // A tapped stop (mock round 12): outlined, its line solid; the others dimmed
        val sel = s.selectedKm
        val isSel = { col: Column -> sel != null && kotlin.math.abs(col.km - sel) < 0.05 }
        // Faded: the other stops while one is tapped, and stops you've passed (after the ride home, 2026-11-10)
        val dimmed = { col: Column -> (sel != null && !isSel(col)) || (sel == null && marker != null && col.km < marker - 0.05) }
        val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; color = col.ink }
        // Your line first: it always runs behind the stop tiles (feedback, 2026-11-11); its km tag comes later, on top
        val showYou = marker != null && marker >= k0 && marker <= k1
        val ux = if (marker != null) max(x(marker), left + 2) else 0f
        if (showYou) {
            // Dark: a yellow line. Light: a black line (feedback, 2026-11-10), like the Karoo's own.
            fill.color = if (night) 0xFFFFE900.toInt() else 0xFF000000.toInt(); c.drawRect(ux - 1.5f, tagTop + tagH, ux + 1.5f, bottom, fill)
        }
        for (column in columns) {
            if (dimmed(column)) c.saveLayerAlpha(null, DIM_ALPHA)
            c.drawLine(column.x, rowOf(column) + icon / 2 + 2, column.x, y(route.elevAt(column.km)) - 3, if (isSel(column)) solid else dash)
            if (dimmed(column)) c.restore()
        }
        for (column in columns) {
            val (kinds, more) = shown(column)
            val rowBottom = rowOf(column)
            val stackTop = rowBottom - (kinds.size - 1) * (icon + 3) - icon / 2
            // Quarter tiles and smaller: too small to tap a stop, the info box doesn't fit (2026-11-10)
            if (!compact) hits += Hit(column.x - icon / 2 - 4, stackTop - 4, column.x + icon / 2 + 4, rowBottom + icon / 2 + 4, column.km)
            // A faded tile sits on the page colour, so your line never shows through it
            if (dimmed(column) && !compact) {
                val under = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(),
                    if (night) BG_TOP_NIGHT else BG_TOP_DAY, if (night) BG_BOTTOM_NIGHT else BG_BOTTOM_DAY, android.graphics.Shader.TileMode.CLAMP) }
                kinds.indices.forEach { i -> val cy = rowBottom - i * (icon + 3)
                    c.drawRoundRect(column.x - icon / 2, cy - icon / 2, column.x + icon / 2, cy + icon / 2, icon * CORNER, icon * CORNER, under) }
                c.saveLayerAlpha(null, DIM_ALPHA)
            } else if (dimmed(column)) c.saveLayerAlpha(null, DIM_ALPHA)
            // Tiles without taps (quarter, row, small): the icon alone, no box, in its colour (picked B, 2026-11-11)
            kinds.forEachIndexed { i, k -> val cy = rowBottom - i * (icon + 3)
                if (compact) bareGlyph(c, k, column.x, cy, icon * BARE_FILL / k.ink, bareColour(k, night, col)) else badge(c, k, column.x, cy, icon) }
            val lift = if (isSel(column)) 8 else 0
            if (more > 0) c.drawText("+$more", column.x, rowBottom - kinds.size * (icon + 3) + icon / 2 - 2 - lift, plus)
            if (dimmed(column)) c.restore()
            if (isSel(column)) {
                val pad = 5f
                c.drawRoundRect(column.x - icon / 2 - pad, stackTop - pad, column.x + icon / 2 + pad, rowBottom + icon / 2 + pad, 12f, 12f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3.5f; color = col.ink })
            }
        }

        // Summit heights, right above their own peak (mock, 2026-11-11; was one row along the top), never higher than
        // that row; left out where a stop, your line or the heights on the left are
        if (showSummits) {
            val sp = KarooType.labelPaint(MIN_CAPS).apply { textAlign = Paint.Align.CENTER; color = col.muted }   // never under the Karoo's small labels; grey like all heights (feedback, 2026-11-11)
            // What's already there, as boxes: each stop's icons and its dashed line, your line, the heights on the left
            val taken = mutableListOf<android.graphics.RectF>()
            for (column in columns) {
                val (kinds, _) = shown(column); val rb = rowOf(column)
                taken += android.graphics.RectF(column.x - icon / 2 - 4, rb - (kinds.size - 1) * (icon + 3) - icon / 2 - icon * 0.5f, column.x + icon / 2 + 4, rb + icon / 2 + 4)
                taken += android.graphics.RectF(column.x - 4, rb + icon / 2, column.x + 4, bottom)
            }
            if (showYou) taken += android.graphics.RectF(ux - 4, 0f, ux + 4, bottom)
            if (h >= 600) taken += android.graphics.RectF(left, 0f, left + 64f, bottom)   // the graph's heights (full page only)
            for ((km, e) in route.summits) {
                if (km < k0 || km > k1) continue
                val t = if (s.imperial) "${(e * M_TO_FT).roundToInt()}" else "${e.roundToInt()}"
                val sx = x(km); val half = sp.measureText(t) / 2 + 4
                val base = max(y(e) - 10, profTop - 8 + drop)
                val box = android.graphics.RectF(sx - half, base - MIN_CAPS - 4, sx + half, base + 4)
                if (taken.any { android.graphics.RectF.intersects(it, box) }) continue
                c.drawText(t, sx, base, sp); taken += box
            }
        }

        // You: ▼ and a line, like BetterIntervals' "now" marker
        if (showYou) {
            // The line (drawn before the stops) through the whole graph, the km in a tag always at the top, right
            // under the header (a label, not a button; feedback, 2026-11-10). Dark: a white tag. Light: white bold
            // on a black tag, like the Karoo's own.
            // always shown (feedback, 2026-11-10: empty without); off route the Karoo's own distance along the route, as its graph shows
            val t = fmt((if (offRoute) s.alongKm ?: 0.0 else marker) * u)
            run {
            val tp = KarooType.labelPaint(if (compact) 20 else 26).apply { color = if (night) 0xFF000000.toInt() else 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER; isFakeBoldText = !night }
            val tw = tp.measureText(t) + 18
            val tx = ux.coerceIn(left + tw / 2 + 2, right - tw / 2 - 2)
            c.drawRoundRect(tx - tw / 2, tagTop, tx + tw / 2, tagTop + tagH, 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (night) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() })
            if (night) c.drawRoundRect(tx - tw / 2, tagTop, tx + tw / 2, tagTop + tagH, 6f, 6f, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = 0xFF000000.toInt() })
            c.drawText(t, tx, tagTop + tagH / 2 + tp.textSize * 0.36f, tp)
            }
        }

        // The next stop beyond the range: top right, with how far
        if (nextBeyond && nextAhead != null && you != null) {
            val size = 26f; val cy = plotTop + 2 + size / 2
            val t = "${fmt((nextAhead.km - you) * u)} ${unit.lowercase()} ›"
            val tp = KarooType.labelPaint(caps).apply { color = col.ink }
            val tw = tp.measureText(t)
            c.drawText(t, right, cy + caps / 2f, tp)
            val k = nextAhead.kinds.filter { p.shows(it.group) }.minByOrNull { p.rank(it.group) }
            if (k != null) badge(c, if ((k.group == Group.FOOD || k.group == Group.WATER) && nextAhead.kinds.size > 2) Kind.FEED else k, right - tw - 6 - size / 2, cy, size)
        }

        // Right after a tap on the range: the graph fades and a scale bar shows how far it reaches, like the Karoo's own
        // ("|—— 5.0km ——|"), for a moment
        if (s.rangeFlash) {
            val top = 0f   // the header fades too (2026-11-10)
            c.drawRect(0f, top, w.toFloat(), h.toFloat(), Paint().apply { color = if (night) 0xC0141414.toInt() else 0xC8FFFFFF.toInt() })
            val km = (k1 - k0) * u
            val t = "${String.format(java.util.Locale.US, "%.1f", km)}${unit.lowercase()}"
            val tp = KarooType.labelPaint(if (compact) 34 else 48).apply { color = col.ink; textAlign = Paint.Align.CENTER }
            val cy = h / 2f; val l = 10f; val r = w - 10f
            val bar = Paint().apply { color = col.ink }
            val half = tp.measureText(t) / 2 + 14
            c.drawRect(l, cy - 1.5f, w / 2f - half, cy + 1.5f, bar); c.drawRect(w / 2f + half, cy - 1.5f, r, cy + 1.5f, bar)
            c.drawRect(l, cy - 12, l + 3, cy + 12, bar); c.drawRect(r - 3, cy - 12, r, cy + 12, bar)
            c.drawText(t, w / 2f, cy + tp.textSize * 0.36f, tp)
        }

        // The tapped stop's info box, at the bottom (mock rounds 12–15)
        val st = sel?.let { km -> route.stops.firstOrNull { kotlin.math.abs(it.km - km) < 0.05 } }
        // the box opens without a position too (rerouting, at the desk): then it shows the km from the start
        if (st != null) infoBox(c, st, route, s, p, w, h, night, col, u, unit)
    }

    /** One tap region: a stop's icons ([km]) or the info box (null: close). */
    data class Hit(val left: Float, val top: Float, val right: Float, val bottom: Float, val km: Double?)

    private var headBase = 0f
    private var headCaps = 0f

    /** The range tap area of the last [draw] (bitmap px): the graph between the header and the km strip, so a
     *  long-press on the header or the km strip still reaches the Karoo (change field). 0..0: none. */
    var tapTop = 0f
        private set
    var tapBottom = 0f
        private set

    /** Where the last [draw] put its tap regions, in the bitmap's pixels. */
    var hits = mutableListOf<Hit>()
        private set

    /**
     * The info box of a tapped stop (mock round 15, picked 2026-11-10): the bottom half of a full page,
     * all of a half page under the header. The stop's icons, its name, then rows laid out like the settings
     * kit, in the Karoo's own words: Distance to stop, Time to stop, Time of arrival, and a cut-off (late by / to
     * spare). No lines. A close button down the right side; a tap anywhere on the box closes it.
     */
    private fun infoBox(c: Canvas, st: Stop, route: RouteModel, s: UpAheadState, p: Prefs, w: Int, h: Int, night: Boolean, fieldCol: Colours, u: Double, unit: String) {
        // Karoo yellow, like the Karoo's own POI card on its map (feedback, 2026-11-10), black ink, day and night;
        // the same type sizes on every field size
        val col = Colours(false)
        val along = s.alongKm
        val big = h >= 600
        // Flat, full width, on its own colour so a tapped stop is plain to see (2026-11-10). Full page: the bottom
        // half, down to the edge. Half page and smaller: the whole tile, header too (the info comes first, 2026-11-10);
        // while it's open a tap anywhere on it closes it.
        val top0 = if (big) h / 2f else 0f
        val x = 0f; val y = top0; val bw = w.toFloat(); val bh = h - top0
        if (bh < 120) return
        c.drawRect(x, y, x + bw, y + bh, Paint().apply { color = BadgeColour.YELLOW })
        hits += Hit(x, y, x + bw, y + bh, null)
        // The close button: a big square in the top-right corner, inset a little, corners round, so it's easy to
        // hit on the bike (2026-11-10; a full-height strip tried). A tap anywhere on the box closes it too.
        val ink = 0xFF111111.toInt()
        val xPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 8f; color = BadgeColour.YELLOW }
        // A small black rounded square with a white ✕ (picked from six, 2026-11-10)
        val btn = CLOSE_BUTTON
        val bx = x + bw - 8 - btn; val by = y + 8
        val cx = bx + btn / 2; val cy = by + btn / 2
        c.drawRoundRect(bx, by, bx + btn, by + btn, 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink })
        xPaint.color = 0xFFFFFFFF.toInt(); xPaint.strokeWidth = 5.5f
        val xk = 15f
        c.drawLine(cx - xk, cy - xk, cx + xk, cy + xk, xPaint); c.drawLine(cx + xk, cy - xk, cx - xk, cy + xk, xPaint)
        val l = x + 20; val r = x + bw - 20
        // the icons and the name stop short of the button; the rows below it run the full width
        val rTop = bx - 12
        // The name first, then what's there: each icon with its word (feedback, 2026-11-10), then the rows
        val kinds = st.kinds.filter { p.shows(it.group) || it.group == Group.OTHER }
        val nameP = KarooType.labelPaint(29).apply { textAlign = Paint.Align.LEFT; color = col.ink; isFakeBoldText = true }
        val name = (st.name?.let { PoiText.displayName(it) } ?: kinds.firstOrNull()?.label ?: "Stop").uppercase()
        // Too long: cut with "…" at full size, never smaller (smallest text, 2026-11-11)
        val shown = cut(name, nameP, rTop - l)
        var yy = y + 20 + nameP.textSize * 0.74f
        c.drawText(shown, l, yy, nameP)
        // icons with their words, flowing onto a second line if needed (beside the close button: up to it)
        val ic = 36f
        val wordP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 27f; color = col.ink; typeface = android.graphics.Typeface.DEFAULT }
        var lineTop = yy + 14
        var cx0 = l
        for (k in kinds) {
            val chipW = ic + 8 + wordP.measureText(k.label)
            val limit = if (lineTop < by + btn + 4) rTop else r
            if (cx0 > l && cx0 + chipW > limit) { cx0 = l; lineTop += ic + 8 }
            badge(c, k, cx0 + ic / 2, lineTop + ic / 2, ic)
            // a yellow badge on the yellow card: outlined so it stays a badge
            if (k.bg == BadgeColour.YELLOW) c.drawRoundRect(cx0 + 1, lineTop + 1, cx0 + ic - 1, lineTop + ic - 1, ic * CORNER, ic * CORNER,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = 0xFF111111.toInt() })
            c.drawText(k.label, cx0 + ic + 8, lineTop + ic / 2 + wordP.textSize * 0.36f, wordP)
            cx0 += chipW + 18
        }
        yy = lineTop + ic + 10
        // rows: icon, label, value on the right
        val eta = s.selectedEtaSec
        var rowH = 50f; val fs = 28f; val isz = 28f
        val labelP = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = fs; color = col.ink; typeface = android.graphics.Typeface.DEFAULT }
        val valueP = KarooType.labelPaint((fs * 0.72f).roundToInt()).apply { textAlign = Paint.Align.RIGHT; isFakeBoldText = true }
        val rows = mutableListOf<Triple<Any, String, Pair<String, Int>>>()
        rows += if (along != null) Triple(KarooType.Glyph.DISTANCE, "Distance to stop", "${fmt((st.km - along) * u)} ${unit.lowercase()}" to col.ink)
        else Triple(KarooType.Glyph.DISTANCE, "From the start", "${fmt(st.km * u)} ${unit.lowercase()}" to col.ink)
        if (eta != null) rows += Triple(KarooType.Glyph.TIMER, "Time to stop", duration(eta) to col.ink)
        if (eta != null && s.clockMin != null) rows += Triple(KarooType.Glyph.CLOCK, "Time of arrival", clock(s.clockMin + (eta / 60).roundToInt()) to col.ink)
        val cut = st.cut
        if (cut != null) {
            val value = if (cut.isEmpty() || eta == null || s.clockMin == null) (cut.ifEmpty { "—" } to col.red) else {
                val (ch, cm) = cut.split(":").map { it.toInt() }
                val late = s.clockMin + (eta / 60).roundToInt() - (ch * 60 + cm)
                (if (late > 0) "Late by ${clock(late)}" else "${clock(-late)} to spare") to (if (late > 0) col.red else col.ink)
            }
            rows += Triple(Kind.CUTOFF, if (cut.isEmpty()) "Cut-off" else "Cut-off $cut", value)
        }
        // Rows close up (down to 31 px) before one is left out, so a cut-off still fits on a half page
        rowH = rowH.coerceAtMost((y + bh - 6 - yy) / rows.size).coerceAtLeast(34f)
        // The rows sit at the bottom of the card; the name, what's there and the close button stay at the top (2026-11-10)
        yy = max(yy, y + bh - 10 - rows.size * rowH)
        // Label and value never touch: if the widest row doesn't fit beside the close button, all rows shrink alike
        // Too wide: the short label ("Distance", "Arrival"), then a cut one; the text never shrinks (2026-11-11)
        val room = r - (l + isz + 14)
        for (i in rows.indices) {
            val (ico, label, value) = rows[i]
            val free = room - 14 - valueP.measureText(value.first)
            if (labelP.measureText(label) > free) rows[i] = Triple(ico, cut(SHORT[label] ?: label.substringBefore(' '), labelP, free), value)
        }
        val iconP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = col.ink }
        for ((ico, label, value) in rows) {
            if (yy + rowH > y + bh - 4) break
            val cyRow = yy + rowH / 2
            when (ico) {
                is String -> KarooType.drawGlyphFit(c, ico, l, cyRow + isz / 2, isz, iconP)
                is Kind -> headGlyph(c, ico, l + 2, cyRow + isz / 2, isz * 0.92f, col.ink)
            }
            c.drawText(label, l + isz + 14, cyRow + labelP.textSize * 0.36f, labelP)
            valueP.color = value.second
            c.drawText(value.first, r, cyRow + labelP.textSize * 0.36f, valueP)
            yy += rowH
        }
    }

    /**
     * The header's info, the rider's pick (Settings › At the top, mock round 11): the next stop of any kind
     * (with its icon), the next cut-off, feed, water, toilet, mechanic, summit or timing, or the finish; as
     * distance or time. Longest first, then shorter: without "NEXT", without the unit, the first parts.
     */
    private fun headInfo(route: RouteModel, s: UpAheadState, p: Prefs, narrow: Boolean, col: Colours, u: Double, unit: String): List<List<Pair<String, Int>>> {
        if (p.head == HeadInfo.OFF) return emptyList()
        val along = s.alongKm ?: return emptyList()
        val st = UpAheadSession.headTarget(route, along, p.head, p) ?: return emptyList()
        // Nothing of that kind left: the target is the finish (headTarget falls back to it)
        val head = if (st.kinds.isEmpty() && st.km >= route.totalKm - 1e-6) HeadInfo.FINISH else p.head
        val dist = "${fmt((st.km - along) * u)} $unit"
        val eta = s.headEtaSec
        val timeOrDist = if (p.headAs == HeadAs.TIME && eta != null) "IN ${duration(eta)}" else dist
        // Small tiles: only the icon and the distance (time), no words (feedback, 2026-11-10); a stop with several
        // things shows the one highest in the priority list
        if (narrow) {
            // Small tiles: always the closest stop, whatever Settings › At the top says (feedback, 2026-11-10: "if water
            // is closest, show just that"); the finish once no stop is left
            val next = UpAheadSession.headTarget(route, along, HeadInfo.NEXT, p) ?: return emptyList()
            val k = next.kinds.filter { p.shows(it.group) }.minByOrNull { p.rank(it.group) } ?: Kind.FINISH
            val d = "${fmt((next.km - along) * u)} $unit"
            val v = if (p.headAs == HeadAs.TIME && eta != null && p.head == HeadInfo.NEXT) "IN ${duration(eta)}" else d
            val one = listOf("@${k.name}" to col.ink, v to col.ink)
            return listOf(one, one.map { (t, c) -> t.removeSuffix(" $unit") to c }).distinct()
        }
        val parts = mutableListOf<Pair<String, Int>>()
        when (head) {
            HeadInfo.NEXT -> {
                val k = st.kinds.filter { p.shows(it.group) }.minByOrNull { p.rank(it.group) } ?: return emptyList()
                parts += "NEXT " to col.ink; parts += "@${k.name}" to col.ink; parts += timeOrDist to col.ink
            }
            HeadInfo.CUT -> {
                val cut = st.cut
                parts += "NEXT CUT " to col.ink
                if (cut.isNullOrEmpty()) parts += timeOrDist to col.red   // a cut-off whose time isn't in its name
                else {
                    parts += cut to col.red
                    if (p.headAs == HeadAs.DISTANCE) parts += " · $dist" to col.ink
                    else if (eta != null && s.clockMin != null) {
                        val arrive = s.clockMin + (eta / 60).roundToInt()
                        val (ch, cm) = cut.split(":").map { it.toInt() }
                        parts += " · ETA " to col.ink
                        parts += clock(arrive) to if (arrive > ch * 60 + cm) col.red else col.green
                    }
                }
            }
            HeadInfo.SUMMIT -> {
                val e = route.elevAt(st.km)
                parts += "NEXT SUMMIT " to col.ink; parts += timeOrDist to col.ink
                parts += " · ${if (s.imperial) (e * M_TO_FT).roundToInt() else e.roundToInt()} ${if (s.imperial) "FT" else "M"}" to col.ink
            }
            HeadInfo.FINISH -> { parts += "FINISH " to col.ink; parts += timeOrDist to col.ink }
            else -> {
                val name = when (head) { HeadInfo.FEED -> "FEED"; HeadInfo.WATER -> "WATER"; HeadInfo.TOILET -> "TOILET"; HeadInfo.MECHANIC -> "MECHANIC"; else -> "TIMING" }
                parts += "NEXT $name " to col.ink; parts += timeOrDist to col.ink
            }
        }
        val noNext = parts.mapIndexedNotNull { i, (t, c) -> if (i == 0) t.removePrefix("NEXT ").takeIf { it.isNotEmpty() }?.let { it to c } else t to c }
        val noUnit = noNext.map { (t, c) -> t.removeSuffix(" $unit") to c }
        val first = noUnit.take(2)
        return listOf(parts, noNext, noUnit, first).distinct()
    }

    /** One badge on its own, [px] square (Settings › Priority). */
    fun badgeBitmap(k: Kind, px: Int): Bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { badge(Canvas(it), k, px / 2f, px / 2f, px.toFloat()) }

    /**
     * A badge (mock round 10, picked 2026-11-10): a rounded square in its category's colour, the glyph in it,
     * sized by the glyph's ink (the Karoo's glyphs have more air in their box than ours).
     */
    private fun badge(c: Canvas, k: Kind, cx: Float, cy: Float, size: Float) {
        val r = size * CORNER
        c.drawRoundRect(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = k.bg })
        bareGlyph(c, k, cx, cy, size, k.fg)
    }

    /** Width of [k]'s glyph drawn in the header ([headGlyph]). */
    private fun headGlyphWidth(k: Kind, capsPx: Float): Float { val ink = inkOf(k.glyph); return ink.width() / ink.height() * capsPx * HEAD_ICON }

    /** [k]'s glyph in the header: its ink as tall as the capitals, its left at [x], standing on [baseline]. */
    private fun headGlyph(c: Canvas, k: Kind, x: Float, baseline: Float, capsPx: Float, color: Int) {
        val d = drawables.getOrPut(k.glyph to color) { context.getDrawable(k.glyph)!!.mutate().apply { setTint(color) } }
        val ink = inkOf(k.glyph)
        val box = capsPx * HEAD_ICON / ink.height()
        val left = x - ink.left * box; val top = baseline - ink.bottom * box
        d.setBounds(left.roundToInt(), top.roundToInt(), (left + box).roundToInt(), (top + box).roundToInt())
        d.draw(c)
    }

    /** The glyph alone, [color], sized by its ink within [size]. */
    /** A bare icon's colour (picked B, 2026-11-11): its category colour, the Karoo's lighter shade at night; timing
     *  yellow is black on light, where yellow on white disappears. */
    private fun bareColour(k: Kind, night: Boolean, col: Colours): Int = when (k.bg) {
        BadgeColour.YELLOW -> if (night) BadgeColour.YELLOW else col.ink
        BadgeColour.RED -> BadgeColour.RED
        BadgeColour.FOOD, BadgeColour.BIKE -> if (night) 0xFF0DBA75.toInt() else BadgeColour.FOOD   // success_green_600 at night
        BadgeColour.WATER -> if (night) 0xFFA3D5FF.toInt() else BadgeColour.WATER                   // climb blue 400 at night
        else -> col.ink
    }

    private fun bareGlyph(c: Canvas, k: Kind, cx: Float, cy: Float, size: Float, color: Int) {
        val d = drawables.getOrPut(k.glyph to color) { context.getDrawable(k.glyph)!!.mutate().apply { setTint(color) } }
        val ink = inkOf(k.glyph)
        val box = k.ink * glyphBoost * size / max(ink.width(), ink.height())
        val left = cx - ink.centerX() * box; val top = cy - ink.centerY() * box
        d.setBounds(left.roundToInt(), top.roundToInt(), (left + box).roundToInt(), (top + box).roundToInt())
        d.draw(c)
    }

    private val inks = HashMap<Int, android.graphics.RectF>()

    /** Where a glyph's ink is in its box (0…1), found once by drawing it and finding the opaque pixels. */
    private fun inkOf(res: Int): android.graphics.RectF = inks.getOrPut(res) {
        val n = 192
        val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        context.getDrawable(res)!!.mutate().apply { setBounds(0, 0, n, n); draw(Canvas(b)) }
        var l = n; var t = n; var r = -1; var bt = -1
        val px = IntArray(n * n); b.getPixels(px, 0, n, 0, 0, n, n)
        for (y in 0 until n) for (x in 0 until n) if ((px[y * n + x] ushr 24) > 40) { if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > bt) bt = y }
        if (r < 0) android.graphics.RectF(0.2f, 0.2f, 0.8f, 0.8f) else android.graphics.RectF(l / n.toFloat(), t / n.toFloat(), (r + 1) / n.toFloat(), (bt + 1) / n.toFloat())
    }

    companion object {
        /** Corner radius of a badge, as a share of its size (5 in 24). */
        const val CORNER = 5f / 24f
        /** Width of the stop box's close button, a square top right (px). */
        const val CLOSE_BUTTON = 67f
        /** Height of the km strip under the Simple graph (px). */
        const val AXIS_H = 32f
        /** Cap height of the km under the graph (px). */
        const val KM_CAPS = 20f
        const val EDGE_L = 12f
        const val EDGE_R = 9f
        const val EDGE_B = 6f
        /** A bare icon fills this much of its old box. */
        const val BARE_FILL = 0.86f
        /** The smallest text on a field: the Karoo's own small labels, capitals about 16.5 px (karoo-type README). */
        const val MIN_CAPS = 17
        val SHORT = mapOf("Distance to stop" to "Distance", "From the start" to "From start", "Time to stop" to "Time",
            "Time of arrival" to "Arrival")
        /** [text] as it fits in [room] at the paint's own size: whole, or cut with "…". */
        fun cut(text: String, p: Paint, room: Float): String {
            if (p.measureText(text) <= room) return text
            var n = text.length
            while (n > 1 && p.measureText(text.take(n).trimEnd() + "…") > room) n--
            return text.take(n).trimEnd() + "…"
        }
        /** The field's background gradient, top and bottom: soft, not pure white / black. */
        /** The Karoo's own Upcoming Elevation in light mode (sampled on the device 2026-11-10): fill and line. */
        const val KAROO_FILL_DAY = 0xFFEAE191.toInt()
        const val KAROO_LINE_DAY = 0xFFD5C400.toInt()
        // The soft background: white to a faint sky (night: charcoal to near black); kept over plain (feedback, 2026-11-11)
        const val BG_TOP_DAY = 0xFFFFFFFF.toInt()
        const val BG_BOTTOM_DAY = 0xFFE6EEF6.toInt()
        const val BG_TOP_NIGHT = 0xFF1C2228.toInt()
        const val BG_BOTTOM_NIGHT = 0xFF0D1013.toInt()
        /** Other stops while one is tapped. */
        const val DIM_ALPHA = 56
        /** Header icon ink height, × the capitals (a hair taller reads as the same size). */
        const val HEAD_ICON = 1.08f
        const val KM_TO_MI = 0.621371
        const val M_TO_FT = 3.28084

        /** 30.6, 76.4, 112 (whole numbers from 100 up). */
        fun fmt(v: Double, whole: Boolean = false): String = if (whole || v >= 100) "${v.roundToInt()}" else String.format(java.util.Locale.US, "%.1f", v)
        fun clock(min: Int): String { val m = ((min % 1440) + 1440) % 1440; return String.format(java.util.Locale.US, "%d:%02d", m / 60, m % 60) }
        fun duration(sec: Double): String { val m = (sec / 60).roundToInt(); return String.format(java.util.Locale.US, "%d:%02d", m / 60, m % 60) }
    }
}
