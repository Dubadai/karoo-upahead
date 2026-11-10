package com.daviddokakis.upahead

/**
 * Everything the field shows. Rendering only reads this.
 * [route]: the loaded route (null: no route). [alongKm]: where the rider is on it.
 * [range]: km (or miles) ahead shown, 0 = the whole route. [headEtaSec]: seconds to the header's
 * target (Settings › At the top), null until known. [clockMin]: time of day, for a cut-off's arrival.
 */
data class UpAheadState(
    val route: RouteModel? = null,
    val alongKm: Double? = null,
    val range: Int = 0,
    val headEtaSec: Double? = null,
    val clockMin: Int? = null,
    val imperial: Boolean = false,
    /** A stop the rider tapped: its info box is open (km along the route), and the seconds to it. */
    val selectedKm: Double? = null,
    val selectedEtaSec: Double? = null,
    /** Which field the stop was tapped in (each view has its own token): only that one shows the box. */
    val selectedView: String? = null,
    /** Where the ▼ stays while riding back to the route (null: at [alongKm]). */
    val markerKm: Double? = null,
    /** When the range last changed (elapsedRealtime ms), and whether its scale bar shows now (render only). */
    val rangeChangedAt: Long = 0L,
    val rangeFlash: Boolean = false,
    /** Off route: the Karoo's way back to the route as (km along it, height m), meeting the route at [rejoinAtKm];
     *  [wayBackReal]: heights from Open-Meteo, else a straight slope from your altitude. Null when on the route. */
    val wayBack: List<Pair<Double, Double>>? = null,
    val rejoinAtKm: Double? = null,
    val wayBackReal: Boolean = false,
    /** The Karoo says you're off the route (its on-route flag), with or without a way back yet. */
    val offRoute: Boolean = false,
)
