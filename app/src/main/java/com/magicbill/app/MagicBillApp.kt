package com.magicbill.app

import android.app.Application
import android.os.StrictMode
import com.magicbill.app.cloud.SessionStore
import com.magicbill.app.di.AppScope
import com.magicbill.app.prefs.Secure
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MagicBillApp : Application() {
    @Inject lateinit var secure: Secure
    @Inject lateinit var plain: com.magicbill.app.prefs.Plain
    @Inject lateinit var sessions: SessionStore
    @Inject @AppScope lateinit var scope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // Network on the main thread is a bug that shows as a hang; the debug build dies on it.
            // Disk on the main thread is logged: the framework itself does a little of that.
            // Penalties are policy-wide, so the one that kills is the network-only one.
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectNetwork().penaltyDeathOnNetwork()
                    .detectDiskReads().detectDiskWrites().penaltyLog()
                    .build(),
            )
        }
        // Open both boxes and read the session off the main thread, before any screen asks.
        scope.launch(Dispatchers.IO) {
            plain.warm()
            secure.warm()
            sessions.load()
        }
    }
}
