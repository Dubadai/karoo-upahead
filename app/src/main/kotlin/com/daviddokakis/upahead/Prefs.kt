package com.daviddokakis.upahead

import android.content.Context

/** What the header shows (Settings › At the top). */
enum class HeadInfo { NEXT, CUT, FEED, WATER, TOILET, MECHANIC, SUMMIT, TIMING, FINISH, OFF }
enum class HeadAs { DISTANCE, TIME }
enum class IconSize { SMALL, LARGE }

/** The ranges a tap cycles through, in this order: the Karoo's own Upcoming Elevation steps (read from its screen,
 *  2026-11-10), km or miles ahead; 0 = the whole route. Steps as long as the route or longer are skipped. */
val RANGES = listOf(2, 5, 10, 25, 50, 80, 150, 300, 0)

/** Everything Settings can change. Read once per redraw; a data class, so a change redraws at once. */
data class Prefs(
    val head: HeadInfo = HeadInfo.NEXT,
    val headAs: HeadAs = HeadAs.DISTANCE,
    val iconSize: IconSize = IconSize.SMALL,
    val priority: List<Group> = DEFAULT_PRIORITY,
    val startRange: Int = 0,
    val showFood: Boolean = true,
    val showToilets: Boolean = true,
    val showMechanic: Boolean = true,
    val showTiming: Boolean = true,
    val showCutoffs: Boolean = true,
    val showSummits: Boolean = true,
    val showOther: Boolean = false,
    /** Hide the stops you've passed (off by default: they stay on the graph, over the greyed part). */
    val hidePassed: Boolean = false,
    val mapSections: Boolean = true,
    val mapBadges: Boolean = true,
) {
    fun shows(g: Group): Boolean = when (g) {
        Group.FOOD, Group.WATER -> showFood
        Group.TOILETS -> showToilets
        Group.MECHANIC -> showMechanic
        Group.TIMING -> showTiming
        Group.CUTOFF -> showCutoffs
        Group.SUMMIT -> showSummits
        Group.OTHER -> showOther
    }

    /** Lower is more important; groups not in the list (summits, other) come last. */
    fun rank(g: Group): Int = priority.indexOf(g).let { if (it < 0) priority.size + g.ordinal else it }

    companion object {
        val DEFAULT_PRIORITY = listOf(Group.TIMING, Group.CUTOFF, Group.FOOD, Group.WATER, Group.TOILETS, Group.MECHANIC)
        private const val FILE = "upahead"

        fun read(context: Context): Prefs {
            val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            val d = Prefs()
            fun <E : Enum<E>> e(key: String, def: E, values: Array<E>) = values.firstOrNull { it.name == p.getString(key, null) } ?: def
            val prio = p.getString("priority", null)?.split(',')?.mapNotNull { n -> Group.entries.firstOrNull { it.name == n } }
                ?.takeIf { it.toSet() == DEFAULT_PRIORITY.toSet() } ?: d.priority
            return Prefs(
                head = e("head", d.head, HeadInfo.entries.toTypedArray()),
                headAs = e("head_as", d.headAs, HeadAs.entries.toTypedArray()),
                iconSize = e("icon_size", d.iconSize, IconSize.entries.toTypedArray()),
                priority = prio,
                startRange = p.getInt("start_range", d.startRange).takeIf { it in RANGES } ?: d.startRange,
                showFood = p.getBoolean("show_food", d.showFood),
                showToilets = p.getBoolean("show_toilets", d.showToilets),
                showMechanic = p.getBoolean("show_mechanic", d.showMechanic),
                showTiming = p.getBoolean("show_timing", d.showTiming),
                showCutoffs = p.getBoolean("show_cutoffs", d.showCutoffs),
                showSummits = p.getBoolean("show_summits", d.showSummits),
                showOther = p.getBoolean("show_other", d.showOther),
                hidePassed = p.getBoolean("hide_passed", d.hidePassed),
                mapSections = p.getBoolean("map_sections", d.mapSections),
                mapBadges = p.getBoolean("map_badges", d.mapBadges),
            )
        }

        fun write(context: Context, change: Prefs.() -> Prefs) {
            val n = read(context).change()
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putString("head", n.head.name).putString("head_as", n.headAs.name)
                .putString("icon_size", n.iconSize.name).putString("priority", n.priority.joinToString(",") { it.name })
                .putInt("start_range", n.startRange)
                .putBoolean("show_food", n.showFood).putBoolean("show_toilets", n.showToilets).putBoolean("show_mechanic", n.showMechanic)
                .putBoolean("show_timing", n.showTiming).putBoolean("show_cutoffs", n.showCutoffs).putBoolean("show_summits", n.showSummits)
                .putBoolean("show_other", n.showOther).putBoolean("hide_passed", n.hidePassed).putBoolean("map_sections", n.mapSections).putBoolean("map_badges", n.mapBadges)
                .apply()
        }
    }
}
