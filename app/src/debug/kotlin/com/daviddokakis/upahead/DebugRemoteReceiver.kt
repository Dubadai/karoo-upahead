package com.daviddokakis.upahead

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * DEBUG BUILDS ONLY. Fakes a remote button press (adb can't press bonus actions):
 * `adb shell am broadcast -a com.daviddokakis.upahead.DEBUG_REMOTE -p com.daviddokakis.upahead --es id range`
 */
class DebugRemoteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getStringExtra("id")) {
            UpAheadExtension.ACTION_RANGE -> UpAheadExtension.session?.nextRange()
            "in" -> UpAheadExtension.session?.zoom(inward = true)
            "out" -> UpAheadExtension.session?.zoom(inward = false)
            "close" -> UpAheadExtension.session?.closeInfo()
            else -> intent.getStringExtra("id")?.removePrefix("stop:")?.toDoubleOrNull()?.let { UpAheadExtension.session?.select(it, null) }
        }
    }
}
