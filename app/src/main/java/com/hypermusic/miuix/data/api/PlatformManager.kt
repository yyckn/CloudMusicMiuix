package com.hypermusic.miuix.data.api

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class MusicPlatform { NETEASE, QQ }

object PlatformManager {
    var current by mutableStateOf(MusicPlatform.NETEASE)
        private set
    private var context: Context? = null

    fun init(context: Context) {
        this.context = context.applicationContext
        current = if (context.getSharedPreferences("music_platform", Context.MODE_PRIVATE)
                .getString("current", "netease") == "qq") MusicPlatform.QQ else MusicPlatform.NETEASE
    }

    fun select(platform: MusicPlatform) {
        current = platform
        context?.getSharedPreferences("music_platform", Context.MODE_PRIVATE)?.edit()
            ?.putString("current", if (platform == MusicPlatform.QQ) "qq" else "netease")?.apply()
    }
}
