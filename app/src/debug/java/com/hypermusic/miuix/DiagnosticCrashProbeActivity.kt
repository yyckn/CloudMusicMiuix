package com.hypermusic.miuix

/** Only reachable by the isolated instrumentation app; never included in Release. */
class DiagnosticCrashProbeActivity : android.app.Activity() {
    override fun onCreate(state: android.os.Bundle?) {
        super.onCreate(state)
        if (!packageName.endsWith(".glasstest")) { finish(); return }
        val token = intent.getStringExtra("diagnostic_token") ?: run { finish(); return }
        com.hypermusic.miuix.data.diagnostics.CrashDiagnostics.update("测试现场", "lyrics-probe-$token")
        android.os.Handler(mainLooper).postDelayed({ throw IllegalStateException("diagnostic-crash-$token") }, 250)
    }
}
