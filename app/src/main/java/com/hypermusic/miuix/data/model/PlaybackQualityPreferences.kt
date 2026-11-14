package com.hypermusic.miuix.data.model

import android.content.SharedPreferences
import com.hypermusic.miuix.data.api.MusicPlatform

/** Shared by playback and downloads following playback, including migration of old explicit values. */
internal object PlaybackQualityPreferences {
    private const val LEGACY_TOTAL_KEY = "total_audio_quality_index"
    fun key(platform: MusicPlatform) = "${platform.storageKey}_audio_quality_index"
    private fun storedIndex(prefs: SharedPreferences, platform: MusicPlatform): Int {
        val key = key(platform)
        if (prefs.contains(key)) return prefs.getInt(key, -1).coerceIn(-1, AudioQuality.options(platform).lastIndex)
        if (prefs.contains("audio_quality_index")) {
            val legacy = prefs.getInt("audio_quality_index", 0)
            return when (platform) {
                MusicPlatform.NETEASE -> AudioQuality.migrateNeteaseIndex(legacy)
                MusicPlatform.QQ -> legacy.coerceIn(AudioQuality.qqOptions.indices)
                else -> -1
            }
        }
        return -1
    }
    fun resolve(prefs: SharedPreferences, platform: MusicPlatform): Int {
        val stored = storedIndex(prefs, platform)
        if (stored >= 0) return stored
        // Read old “follow total” values only to migrate them into independent platform settings.
        val oldIndex = prefs.getInt(LEGACY_TOTAL_KEY, 0).coerceIn(0, 5)
        val mapping = when (platform) {
            MusicPlatform.NETEASE -> listOf(0, 1, 1, 2, 3, 3)
            MusicPlatform.QQ -> listOf(0, 1, 2, 3, 3, 4)
            MusicPlatform.BODIAN -> listOf(0, 0, 1, 2, 2, 2)
            MusicPlatform.KUGOU, MusicPlatform.QISHUI -> listOf(0, 1, 2, 3, 3, 3)
        }
        return mapping[oldIndex]
    }

    fun migrate(prefs: SharedPreferences) {
        if (!prefs.contains(LEGACY_TOTAL_KEY) && MusicPlatform.entries.all {
            prefs.contains(key(it)) && prefs.getInt(key(it), -1) in AudioQuality.options(it).indices
        }) return
        val resolved = MusicPlatform.entries.associateWith { resolve(prefs, it) }
        val editor = prefs.edit().remove(LEGACY_TOTAL_KEY)
        resolved.forEach { (platform, index) -> editor.putInt(key(platform), index) }
        editor.apply()
    }
}
