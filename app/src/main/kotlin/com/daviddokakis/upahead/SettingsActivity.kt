package com.daviddokakis.upahead

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import com.dubadai.karoo.common.*

/**
 * UpAhead's Settings, built from the settings kit (design: docs/design/mock, Settings shots): the
 * Example card (the field with these settings, on the example route), the graph, the header's info,
 * icon size and priority, the range to start with, what shows on the graph and on the map, then the
 * same last rows as every app. Opened from the Karoo's app list.
 */
class SettingsActivity : Activity() {

    private lateinit var form: LinearLayout
    private lateinit var example: ExampleCard
    private val renderer by lazy { ProfileRenderer(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), 0, px(12), px(24))
        }
        example = ExampleCard(this)
        setContentView(
            SettingsLayout(
                this,
                back = settingsBack(getString(R.string.back)) { finish() },
                barTitle = getString(R.string.settings),
                title = settingsTitle(getString(R.string.field_name)),
                card = example,
                form = form,
                pageColor = getColor(R.color.settings_bg),
                textColor = getColor(R.color.settings_title),
            ),
        )
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun change(f: Prefs.() -> Prefs) { Prefs.write(this, f); render() }

    private fun render() {
        val p = Prefs.read(this)
        showExample(p)
        form.removeAllViews()
        fun add(vararg v: android.view.View) = v.forEach { form.addView(it) }

        add(settingsHeading(R.drawable.ic_poi_cutoff, "At the top"),
            settingsIntro("What the header says. Next stop (any): the closest stop you show on the graph, with its icon, e.g. \"COMING UP IN 5.4 KM\". The others: the next one of that kind, e.g. \"NEXT FEED IN 11.9 KM\". Small tiles always show the closest stop."),
            settingsChoiceCards(listOf(
                Triple("Next stop (any)", "The closest stop on the graph", HeadInfo.NEXT),
                Triple("Next cut-off", "Time to spare, or how late", HeadInfo.CUT),
                Triple("Next feed", "The next stop with food", HeadInfo.FEED),
                Triple("Next water", "The next stop with water", HeadInfo.WATER),
                Triple("Next toilet", "The next toilet", HeadInfo.TOILET),
                Triple("Next mechanic", "A mechanic or bike shop", HeadInfo.MECHANIC),
                Triple("Next summit", "The top of the next climb", HeadInfo.SUMMIT),
                Triple("Next timing", "The next timed section", HeadInfo.TIMING),
                Triple("Finish", "The end of the route", HeadInfo.FINISH),
                Triple("Off", "No text in the header", HeadInfo.OFF)), p.head) { v -> change { copy(head = v) } },
            settingsHeading(R.drawable.ic_settings_timer, "Show as"),
            settingsIntro("Distance: how far it is. Time: how long until you get there at your speed on this ride, e.g. \"1:20 H\". For a cut-off: how much time you'll have to spare (green) or how late you'll be (red)."),
            settingsSegmented(listOf("Distance" to HeadAs.DISTANCE, "Time" to HeadAs.TIME), p.headAs) { v -> change { copy(headAs = v) } },
            settingsDivider())

        add(settingsHeading(R.drawable.ic_settings_look, "Icon size"),
            settingsIntro("Large is easier to read on the bike. If a stop has more icons than fit, the rest show as +2."),
            settingsSegmented(listOf("Small" to IconSize.SMALL, "Large" to IconSize.LARGE), p.iconSize) { v -> change { copy(iconSize = v) } },
            settingsHeading(R.drawable.ic_settings_priority, "Priority"),
            settingsIntro("Drag to reorder. When not every stop fits on the graph, your top kinds are shown first. A stop with several things shows your top one nearest the graph and in the header."),
            settingsRankList(p.priority.map { g -> Triple(g, badgeView(GROUP_KIND.getValue(g)), GROUP_NAME.getValue(g)) }) { order -> change { copy(priority = order) } },
            settingsDivider())

        add(settingsHeading(R.drawable.ic_settings_distance, "Start with"),
            settingsIntro("How far ahead the graph starts. Like the Karoo's own graph: tap to zoom out, double-tap to zoom in, through 2, 5, 10, 25, 50, 80, 150, 300 km and the full route. On a half page or bigger, tap a stop for its card."),
            settingsInfoRow(R.drawable.ic_settings_info, "Or use a remote button: Karoo Settings › Controllers › UpAhead: next range."),
            settingsChoiceCards(RANGES.map { r -> Triple(if (r == 0) "Full route" else "$r km ahead", null, r) }, p.startRange) { v -> change { copy(startRange = v) } },
            settingsDivider())

        add(settingsHeading(R.drawable.ic_settings_elevation, "On the graph"),
            settingsIntro("Which stops the graph shows. Timing and cut-offs come from the POI names, e.g. \"Timing start\" or \"Cut-off 16:40\"; services too, e.g. \"Feed 2: food water wc mechanic\"."),
            settingsSwitchRow(R.drawable.ic_poi_food, "Food and water", p.showFood) { v -> change { copy(showFood = v) } },
            settingsSwitchRow(R.drawable.ic_poi_restroom, "Toilets", p.showToilets) { v -> change { copy(showToilets = v) } },
            settingsSwitchRow(R.drawable.ic_poi_mechanic, "Mechanic, medical", p.showMechanic) { v -> change { copy(showMechanic = v) } },
            settingsSwitchRow(R.drawable.ic_poi_timed, "Timing", p.showTiming) { v -> change { copy(showTiming = v) } },
            settingsSwitchRow(R.drawable.ic_poi_cutoff, "Cut-offs", p.showCutoffs) { v -> change { copy(showCutoffs = v) } },
            settingsSwitchRow(R.drawable.ic_poi_summit, "Summit heights", p.showSummits) { v -> change { copy(showSummits = v) } },
            settingsSwitchRow(R.drawable.ic_poi_viewpoint, "Other POIs", p.showOther) { v -> change { copy(showOther = v) } },
            settingsSwitchRow(R.drawable.ic_settings_flag, "Hide passed stops", p.hidePassed) { v -> change { copy(hidePassed = v) } },
            settingsDivider())

        add(settingsHeading(R.drawable.ic_settings_marker, "On the map"),
            settingsIntro("What UpAhead adds to the Karoo's map. The Karoo shows the route's POIs itself; these add what it can't show."),
            settingsSwitchRow(R.drawable.ic_poi_timing_start, "Timed sections", p.mapSections) { v -> change { copy(mapSections = v) } },
            settingsSwitchRow(R.drawable.ic_poi_cutoff, "Badges on pins", p.mapBadges) { v -> change { copy(mapBadges = v) } })

        settingsAppRows(form, repo = "karoo-upahead") { render() }
    }

    private fun badgeView(k: Kind) = ImageView(this).apply { setImageBitmap(renderer.badgeBitmap(k, px(26))) }

    /** The real field at half a page, with these settings, on the example route. */
    private fun showExample(p: Prefs) {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val full = renderer.render(ExampleRoute.state, p, HALF_W, HALF_H, night)
        val h = px(110)
        example.show(Bitmap.createScaledBitmap(full, h * HALF_W / HALF_H, h, true), getString(R.string.field_description))
    }

    private fun px(dp: Int) = (dp * resources.displayMetrics.density).toInt()

    private companion object {
        /** A half-page field on the Karoo 3 (pixels). */
        const val HALF_W = 478
        const val HALF_H = 348
        val GROUP_KIND = mapOf(Group.TIMING to Kind.TIMING_START, Group.CUTOFF to Kind.CUTOFF, Group.FOOD to Kind.FOOD,
            Group.WATER to Kind.WATER, Group.TOILETS to Kind.TOILET, Group.MECHANIC to Kind.MECHANIC)
        val GROUP_NAME = mapOf(Group.TIMING to "Timing", Group.CUTOFF to "Cut-offs", Group.FOOD to "Food",
            Group.WATER to "Water", Group.TOILETS to "Toilets", Group.MECHANIC to "Mechanic, medical")
    }
}
