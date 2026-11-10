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
            settingsChoiceList(listOf("Next stop (any)" to HeadInfo.NEXT, "Next cut-off" to HeadInfo.CUT, "Next feed" to HeadInfo.FEED,
                "Next water" to HeadInfo.WATER, "Next toilet" to HeadInfo.TOILET, "Next mechanic" to HeadInfo.MECHANIC, "Next summit" to HeadInfo.SUMMIT,
                "Next timing" to HeadInfo.TIMING, "Finish" to HeadInfo.FINISH, "Off" to HeadInfo.OFF), p.head) { v -> change { copy(head = v) } },
            settingsHelp("What the header shows, next to the field's icon. Next stop: the next POI of any kind you show on the graph, with its icon."),
            settingsHeading(R.drawable.ic_settings_timer, "Show as"),
            settingsSegmented(listOf("Distance" to HeadAs.DISTANCE, "Time" to HeadAs.TIME), p.headAs) { v -> change { copy(headAs = v) } },
            settingsHelp("Time: when you reach a cut-off (green before it, red after), or how long to the rest. From your own speed on this ride, in a bunch too."),
            settingsDivider())

        add(settingsHeading(R.drawable.ic_settings_look, "Icon size"),
            settingsSegmented(listOf("Small" to IconSize.SMALL, "Large" to IconSize.LARGE), p.iconSize) { v -> change { copy(iconSize = v) } },
            settingsHelp("Large is easier to read on the bike. When a stop has more icons than fit, the rest show as +2."),
            settingsHeading(R.drawable.ic_settings_priority, "Priority"),
            settingsRankList(p.priority.map { g -> Triple(g, badgeView(GROUP_KIND.getValue(g)), GROUP_NAME.getValue(g)) }) { order -> change { copy(priority = order) } },
            settingsHelp("Drag to reorder. When not every stop fits, the stops ahead with your top kinds are shown; in a column, the top ones sit nearest the graph."),
            settingsDivider())

        add(settingsHeading(R.drawable.ic_settings_distance, "Start with"),
            settingsChoiceList(RANGES.map { r -> (if (r == 0) "Full route" else "$r km ahead") to r }, p.startRange) { v -> change { copy(startRange = v) } },
            settingsHelp("Like the Karoo's own graph: tap the graph to zoom out, double-tap to zoom in, through 2, 5, 10, 25, 50, 80, 150, 300 km ahead and the full route. Tap a stop for its card."),
            settingsInfoRow(R.drawable.ic_settings_info, "Or use a remote button: Karoo Settings › Controllers › UpAhead: next range."),
            settingsDivider())

        add(settingsHeading(R.drawable.ic_settings_elevation, "On the graph"),
            settingsSwitchRow(R.drawable.ic_poi_food, "Food and water", p.showFood) { v -> change { copy(showFood = v) } },
            settingsSwitchRow(R.drawable.ic_poi_restroom, "Toilets", p.showToilets) { v -> change { copy(showToilets = v) } },
            settingsSwitchRow(R.drawable.ic_poi_mechanic, "Mechanic, medical", p.showMechanic) { v -> change { copy(showMechanic = v) } },
            settingsSwitchRow(R.drawable.ic_poi_timed, "Timing", p.showTiming) { v -> change { copy(showTiming = v) } },
            settingsSwitchRow(R.drawable.ic_poi_cutoff, "Cut-offs", p.showCutoffs) { v -> change { copy(showCutoffs = v) } },
            settingsSwitchRow(R.drawable.ic_poi_summit, "Summit heights", p.showSummits) { v -> change { copy(showSummits = v) } },
            settingsSwitchRow(R.drawable.ic_poi_viewpoint, "Other POIs", p.showOther) { v -> change { copy(showOther = v) } },
            settingsSwitchRow(R.drawable.ic_settings_flag, "Hide passed stops", p.hidePassed) { v -> change { copy(hidePassed = v) } },
            settingsHelp("Timing and cut-offs come from the POI names, e.g. \"Timing start\" or \"Cut-off 16:40\"; services too, e.g. \"Feed 2: food water wc mechanic\"."),
            settingsDivider())

        add(settingsHeading(R.drawable.ic_settings_marker, "On the map"),
            settingsSwitchRow(R.drawable.ic_poi_timing_start, "Timed sections", p.mapSections) { v -> change { copy(mapSections = v) } },
            settingsSwitchRow(R.drawable.ic_poi_cutoff, "Badges on pins", p.mapBadges) { v -> change { copy(mapBadges = v) } },
            settingsHelp("The Karoo shows the route's POIs on the map itself; these add what it can't show."))

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
