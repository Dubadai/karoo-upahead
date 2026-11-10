package com.daviddokakis.upahead

import android.util.Log
import com.dubadai.karoo.common.UpdateCheck
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.MapEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/** Entry point the Karoo system binds to: the field, the map layer and the remote button. */
class UpAheadExtension : KarooExtension(EXTENSION_ID, "1") {

    private lateinit var karoo: KarooSystemService
    private lateinit var model: UpAheadSession
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val types: List<DataTypeImpl> by lazy {
        listOf(UpAheadDataType(extension, model))
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("BuildStamp", "$packageName ${BuildConfig.VERSION_NAME} build ${BuildConfig.BUILD_STAMP}")
        if (Timber.treeCount == 0) Timber.plant(Timber.DebugTree())
        karoo = KarooSystemService(this)
        model = UpAheadSession(this, karoo)
        session = model
        karoo.connect { connected -> Timber.i("Karoo system connected: $connected") }
        model.start()
        // Not riding: tell the rider when a newer release is on GitHub (see UpdateCheck).
        UpdateCheck(this, karoo, appName = "UpAhead").start(updateScope)
    }

    override fun startMap(emitter: Emitter<MapEffect>) {
        val job = scope.launch { MapLayer(this@UpAheadExtension, model).run(emitter) }
        emitter.setCancellable { job.cancel() }
    }

    /** Remote buttons (Karoo Settings › Controllers). Always arrive, unlike field taps. */
    override fun onBonusAction(actionId: String) {
        when (actionId) {
            ACTION_RANGE -> model.nextRange()
            else -> Timber.w("Unknown action $actionId")
        }
    }

    override fun onDestroy() {
        scope.cancel()
        updateScope.cancel()
        model.stop()
        session = null
        karoo.disconnect()
        super.onDestroy()
    }

    companion object {
        const val EXTENSION_ID = "upahead"
        const val ACTION_RANGE = "range"

        /** The running session, for receivers (field taps, debug remote). */
        @Volatile var session: UpAheadSession? = null
    }
}
