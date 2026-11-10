package com.daviddokakis.upahead

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Taps on the field: the top (header and the strip under it) steps to the next range; a stop's icons open its info box; the box closes it. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val session = UpAheadExtension.session ?: return
        when (intent.action) {
            ACTION_RANGE -> session.rangeTap()
            ACTION_SELECT -> intent.data?.let { u -> u.lastPathSegment?.toDoubleOrNull()?.let { session.select(it, u.getQueryParameter("view")) } }
            ACTION_CLOSE -> session.closeInfo()
        }
    }

    companion object {
        const val ACTION_RANGE = "com.daviddokakis.upahead.RANGE"
        const val ACTION_SELECT = "com.daviddokakis.upahead.SELECT"
        const val ACTION_CLOSE = "com.daviddokakis.upahead.CLOSE"
    }
}
