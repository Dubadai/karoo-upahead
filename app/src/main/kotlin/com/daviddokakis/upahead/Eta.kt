package com.daviddokakis.upahead

import com.dubadai.karoo.common.Physics
import kotlin.math.abs
import kotlin.math.min

/**
 * The rider's speed for each grade, learned from this ride. Mountaineer's physics (power on a grade)
 * is right on climbs but not on the flat or downhill, where a bunch, braking and corners decide more
 * than watts. So each grade band keeps the rider's real moving speed there, following recent riding
 * (joining or losing a bunch shows within a few km). A band with little data uses the physics, which
 * blends into the learned speed as data comes in.
 */
class SpeedByGrade {
    /** Upper grade (%) of each band; the last band has no upper bound. */
    private val edges = doubleArrayOf(-4.0, -1.0, 1.5, 4.0, 7.0)
    private val speed = DoubleArray(edges.size + 1)
    private val seconds = DoubleArray(edges.size + 1)

    private fun band(gradePct: Double): Int = edges.indexOfFirst { gradePct < it }.let { if (it < 0) edges.size else it }

    /** One sample: [speedMs] while moving on [gradePct], [dtSec] long. */
    fun learn(speedMs: Double, gradePct: Double, dtSec: Double) {
        if (speedMs < MOVING_MS || dtSec <= 0 || dtSec > 10) return
        val b = band(gradePct)
        val a = dtSec / (TAU_SEC + dtSec)
        speed[b] = if (seconds[b] == 0.0) speedMs else speed[b] + (speedMs - speed[b]) * a
        seconds[b] += dtSec
    }

    /** Expected speed (m/s) on [gradePct]: learned, or the physics at [watts] for [massKg] until learned. */
    fun speedAt(gradePct: Double, watts: Double?, massKg: Double): Double {
        val b = band(gradePct)
        val model = model(gradePct, watts, massKg)
        val w = min(1.0, seconds[b] / TRUST_SEC)
        return w * speed[b] + (1 - w) * model
    }

    private fun model(gradePct: Double, watts: Double?, massKg: Double): Double {
        val p = watts?.takeIf { it > 50 } ?: DEFAULT_WATTS
        val v = Physics.speed(p, gradePct, massKg)
        // Downhill the physics knows no brakes or corners; nobody holds 70 km/h on a mountain road.
        return min(v, MAX_DOWNHILL_MS)
    }

    fun reset() { speed.fill(0.0); seconds.fill(0.0) }

    companion object {
        const val MOVING_MS = 2.0
        /** How long a band takes to follow a change (a bunch forming), in seconds ridden in that band. */
        const val TAU_SEC = 300.0
        /** Seconds in a band before its learned speed is trusted fully. */
        const val TRUST_SEC = 180.0
        const val DEFAULT_WATTS = 180.0
        const val MAX_DOWNHILL_MS = 14.0
    }
}

/** Time to ride the route from one km to another, 100 m at a time on each step's grade. */
object Eta {
    const val STEP_KM = 0.1

    fun seconds(route: RouteModel, fromKm: Double, toKm: Double, speeds: SpeedByGrade, watts: Double?, massKg: Double): Double {
        var t = 0.0
        var k = fromKm
        while (k < toKm - 1e-6) {
            val next = min(toKm, k + STEP_KM)
            t += (next - k) * 1000 / speeds.speedAt(route.grade(k, next), watts, massKg)
            k = next
        }
        return t
    }
}

/**
 * Keeps a shown time steady: a new estimate moves it a third of the way, so a stop at a light or a
 * short sprint doesn't make it jump; a big change (another target, a reroute) is taken at once.
 */
class Smoothed {
    private var shown: Double? = null
    fun next(estimate: Double?): Double? {
        val s = shown
        shown = when {
            estimate == null -> null
            s == null || abs(estimate - s) > 0.25 * s + 120 -> estimate
            else -> s + (estimate - s) / 3
        }
        return shown
    }
    fun reset() { shown = null }
}
