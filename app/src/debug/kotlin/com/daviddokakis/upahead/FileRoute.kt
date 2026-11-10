package com.daviddokakis.upahead

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * DEBUG ONLY: a real route pushed to the emulator for the gallery, never in git (it can be someone's
 * commute): `adb push route.json /sdcard/Android/data/com.daviddokakis.upahead/files/route.json`.
 * {"total": km, "you": km, "ele": [[km, m], …], "pois": [[km, "karoo type", "name"], …]}. Null without the file.
 */
object FileRoute {
    fun state(context: Context): UpAheadState? {
        val f = File(context.getExternalFilesDir(null), "route.json").takeIf { it.exists() } ?: return null
        val j = JSONObject(f.readText())
        val ele = j.getJSONArray("ele").let { a -> (0 until a.length()).map { a.getJSONArray(it).let { p -> p.getDouble(0) to p.getDouble(1) } } }
        val pois = j.getJSONArray("pois").let { a -> (0 until a.length()).map { a.getJSONArray(it).let { p -> RoutePoi(p.getString(1), p.getString(2), p.getDouble(0), 0.0, 0.0) } } }
        val route = RouteModel.build("file", j.getDouble("total"), ele, pois, emptyList())
        return UpAheadState(route = route, alongKm = j.getDouble("you"), range = 0, headEtaSec = 1500.0, clockMin = 10 * 60 + 24)
    }
}
