package com.hypermusic.miuix

import android.app.Application
import com.hypermusic.miuix.data.diagnostics.CrashDiagnostics

class HyperMusicApplication : Application() {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        CrashDiagnostics.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        if (Application.getProcessName() == packageName) {
            AppBootstrap.initialize(this)
        }
    }
}
