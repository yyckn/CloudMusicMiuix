package com.hypermusic.miuix

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Process

/** A separate process can restart the main process without waiting for a Toast or an alarm. */
class AppRestartActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Resolve the newly enabled launcher alias. Reusing MainActivity's old task
        // lets Android's disabled-alias cleanup remove the freshly restarted UI.
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, MainActivity::class.java)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        val previousPid = intent.getIntExtra("previous_pid", -1)
        if (previousPid > 0 && previousPid != Process.myPid()) Process.killProcess(previousPid)
        startActivity(launch)
        finish()
    }
}
