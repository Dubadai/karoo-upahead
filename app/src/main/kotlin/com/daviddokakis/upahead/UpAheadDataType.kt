package com.daviddokakis.upahead

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.SystemClock
import android.widget.RemoteViews
import com.dubadai.karoo.common.FieldPacer
import com.dubadai.karoo.common.KarooField
import com.dubadai.karoo.common.KarooType
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateGraphicConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.roundToInt

/** The UpAhead field (typeId "upahead": never change it, ride pages keep the field by it): any size, one bitmap. */
class UpAheadDataType(
    extension: String,
    private val session: UpAheadSession,
) : DataTypeImpl(extension, TYPE_ID) {

    /**
     * Numeric fallback (plain-number places): km to the next stop. Always Streaming (empty values
     * without one): anything else makes the Karoo cover our view with its UNKNOWN / No Sensor tile.
     */
    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.Default).launch {
            // km to the next stop, else to the finish; sent only when it changes (to 0.1 km), so the Karoo
            // isn't handed an empty data point every second (359 "invalid datapoint" lines on 2026-11-10)
            session.state.map { s ->
                val along = s.alongKm; val route = s.route
                if (along == null || route == null) null
                else ((route.stops.firstOrNull { it.km > along }?.km ?: route.totalKm) - along).let { Math.round(it * 10) / 10.0 }
            }.distinctUntilChanged().collect { v ->
                emitter.onNext(StreamState.Streaming(DataPoint(dataTypeId, values = if (v == null) emptyMap() else mapOf(DataType.Field.SINGLE to v))))
            }
        }
        emitter.setCancellable { job.cancel() }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        Timber.d("View started: $config")
        emitter.onNext(UpdateGraphicConfig(showHeader = false))
        val (w, h) = config.viewSize
        val renderer = ProfileRenderer(context)
        // This view's own token: a stop tapped here opens its box only here, not in every UpAhead field
        val token = java.util.UUID.randomUUID().toString().take(8)
        if (config.preview) {
            // Page editor / field chooser: one still picture (the example route), never live.
            emitter.updateView(views(context, renderer.render(ExampleRoute.state, Prefs.read(context), w, h, isNight(context))))
            return
        }
        val job = CoroutineScope(Dispatchers.Default).launch {
            // The Karoo drops updates closer than ~0.9 s apart: redraw when something seen changes,
            // at most about once a second, and only while this field's page is on screen.
            var lastRender = 0L
            var shown: Any? = null
            var wasOnScreen = true
            // Sleep until the route or the page on screen changes (karoo-common FieldPacer), no polling
            val pace = FieldPacer(listOf(session.state, session.activePage))
            val pagePace = FieldPacer(listOf(session.activePage))
            while (isActive) {
                pace.mark()
                pagePace.mark()
                val onScreen = onScreen(config)
                if (onScreen != wasOnScreen) { wasOnScreen = onScreen; if (onScreen) shown = null }
                if (!onScreen) { pagePace.sleep(FieldPacer.CAP_MS); continue }
                val s = session.state.value.let { st -> if (st.selectedView == token) st else st.copy(selectedKm = null, selectedEtaSec = null) }
                    // the new range's scale bar shows for a moment after a tap, like the Karoo's own (then a redraw takes it away)
                    .let { st -> st.copy(rangeFlash = SystemClock.elapsedRealtime() - st.rangeChangedAt < RANGE_FLASH_MS) }
                val prefs = Prefs.read(context)
                val night = isNight(context)
                val key = listOf(s.copy(alongKm = s.alongKm?.let { (it * 1000 / step(s, w)).roundToInt() * step(s, w) / 1000 }), prefs, night)
                val since = SystemClock.elapsedRealtime() - lastRender
                if ((key != shown && since >= MIN_GAP_MS) || since >= IDLE_REDRAW_MS) {
                    try {
                        // With a route loaded the Karoo's turn bar takes room and every field shows at about
                        // 0.88 of its reported height, without telling us (measured 2026-11-10: 240 → 211,
                        // 456 → 403 px). Drawn at the reported height, the body got squashed and icons looked
                        // wide; drawn at the height shown, it fills the field 1:1.
                        val shownH = if (s.route != null) (h * ROUTE_SHOWN).roundToInt() else h
                        val bmp = renderer.render(s, prefs, w, shownH, night)
                        emitter.updateView(views(context, bmp, renderer.hits.toList(), token, renderer.tapTop, renderer.tapBottom))
                        shown = key
                    } catch (e: Exception) {
                        Timber.e(e, "Render failed")
                    }
                    lastRender = SystemClock.elapsedRealtime()
                }
                // The scale bar's flash ends by the clock, not by a change: wake for it
                val flashLeft = if (s.rangeFlash) s.rangeChangedAt + RANGE_FLASH_MS - SystemClock.elapsedRealtime() else Long.MAX_VALUE
                pace.sleep(minOf(FieldPacer.nextMs(key != shown, SystemClock.elapsedRealtime() - lastRender, MIN_GAP_MS, IDLE_REDRAW_MS), flashLeft))
            }
        }
        emitter.setCancellable { job.cancel() }
    }

    /** Metres the rider moves before the graph moves a pixel (no redraw for less). */
    private fun step(s: UpAheadState, w: Int): Double {
        val km = if (s.range > 0) s.range / (if (s.imperial) ProfileRenderer.KM_TO_MI else 1.0) else s.route?.totalKm ?: 1.0
        return (km * 1000 / w).coerceAtLeast(5.0)
    }

    private fun onScreen(config: ViewConfig): Boolean {
        val page = session.activePage.value ?: return true
        return KarooType.onPage(page.elements.map { it.gridSize }, page.elements.filter { it.dataTypeId == dataTypeId }.map { it.gridSize }, config.gridSize)
    }

    private fun isNight(context: Context) =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    companion object {
        /** Share of a field's reported height the Karoo shows while a route is loaded (its turn bar). */
        const val ROUTE_SHOWN = 0.88
        const val TYPE_ID = "upahead"
        private const val MIN_GAP_MS = 920L
        private const val RANGE_FLASH_MS = 1500L
        private const val IDLE_REDRAW_MS = 10_000L

        /**
         * The field's views (karoo-common KarooField): the header band at true size and the body stretched to
         * the field (full width, 2026-11-10; kept-in-proportion left a gap at each side), so a row the Karoo shortens on a map page with a route (141 → 123 px) doesn't squash
         * the header or the icons. A tap anywhere cycles the ranges (2026-11-10, pinch isn't possible: fields
         * only get taps); each stop has its own tap area on top.
         */
        fun views(context: Context, bitmap: android.graphics.Bitmap, hits: List<ProfileRenderer.Hit> = emptyList(), token: String = "", tapTop: Float = 0f, tapBottom: Float = 0f): RemoteViews {
            val band = KarooField.headerBandPx(bitmap.height, bitmap.width > 300)
            val field = KarooField.views(context, bitmap, band, context.getString(R.string.field_description), KarooField.Body.STRETCH)
            val v = RemoteViews(context.packageName, R.layout.field_root)
            v.addView(R.id.content, field)
            // The range tap area: the graph only; the header and the km strip take no taps, so the Karoo's long-press
            // (change field) works there (a tap area swallows the long-press; 2026-11-10)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val px = android.util.TypedValue.COMPLEX_UNIT_PX
                v.setViewLayoutMargin(R.id.taps, RemoteViews.MARGIN_TOP, tapTop, px)
                v.setViewLayoutHeight(R.id.taps, maxOf(0f, tapBottom - tapTop), px)
            }
            // Name the receiver class: it has no intent filter, so an action-only intent never arrives.
            fun tap(action: String, code: Int) = PendingIntent.getBroadcast(
                context, code, Intent(context, ActionReceiver::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            // A tap on the graph: the next range, round and round, like the Karoo's own elevation graph
            v.setOnClickPendingIntent(R.id.taps, tap(ActionReceiver.ACTION_RANGE, 2))
            // On top of it, a tap area per stop (opens its info box) and over an open box (closes it)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                hits.forEachIndexed { i, hit ->
                    val a = RemoteViews(context.packageName, R.layout.field_hit)
                    val px = android.util.TypedValue.COMPLEX_UNIT_PX
                    a.setViewLayoutWidth(R.id.hit, hit.right - hit.left, px)
                    a.setViewLayoutHeight(R.id.hit, hit.bottom - hit.top, px)
                    a.setViewLayoutMargin(R.id.hit, RemoteViews.MARGIN_LEFT, hit.left, px)
                    a.setViewLayoutMargin(R.id.hit, RemoteViews.MARGIN_TOP, hit.top, px)
                    val intent = Intent(context, ActionReceiver::class.java)
                        .setAction(if (hit.km == null) ActionReceiver.ACTION_CLOSE else ActionReceiver.ACTION_SELECT)
                        .setData(android.net.Uri.parse("upahead://stop/${hit.km ?: "close"}?view=$token"))
                    a.setOnClickPendingIntent(R.id.hit, PendingIntent.getBroadcast(context, 100 + i, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                    v.addView(R.id.poi, a)
                }
            }
            return v
        }
    }
}
