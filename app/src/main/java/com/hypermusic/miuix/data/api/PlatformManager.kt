package com.hypermusic.miuix.data.api

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class MusicPlatform(val storageKey: String, val trackSource: String) {
    NETEASE("netease", "netease"),
    QQ("qq", "qqmusic"),
    BODIAN("bodian", "bodian"),
    KUGOU("kugou", "kugou"),
    QISHUI("qishui", "qishui");

    companion object {
        fun fromStorageKey(value: String?): MusicPlatform? = entries.firstOrNull { it.storageKey == value }
        fun fromTrackSource(value: String?): MusicPlatform? = entries.firstOrNull { it.trackSource == value }
    }
}

object PlatformManager {
    var current by mutableStateOf(MusicPlatform.NETEASE)
        private set
    private var context: Context? = null

    fun init(context: Context) {
        this.context = context.applicationContext
        current = MusicPlatform.fromStorageKey(context.getSharedPreferences("music_platform", Context.MODE_PRIVATE)
                .getString("current", "netease")) ?: MusicPlatform.NETEASE
    }

    fun select(platform: MusicPlatform) {
        current = platform
        context?.getSharedPreferences("music_platform", Context.MODE_PRIVATE)?.edit()
            ?.putString("current", platform.storageKey)?.apply()
    }
}
