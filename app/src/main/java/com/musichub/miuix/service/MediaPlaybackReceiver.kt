package com.musichub.miuix.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MediaPlaybackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, MediaPlaybackService::class.java).apply {
            action = intent.action
        }
        context.startService(serviceIntent)
    }
}
