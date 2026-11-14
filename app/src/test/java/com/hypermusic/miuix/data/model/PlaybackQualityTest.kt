package com.hypermusic.miuix.data.model

import android.content.SharedPreferences
import com.hypermusic.miuix.data.api.MusicPlatform
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class PlaybackQualityTest {
    private fun prefs(vararg entries: Pair<String, Int>): SharedPreferences {
        val values = entries.toMap().toMutableMap()
        fun editor(): SharedPreferences.Editor {
            val pending = mutableMapOf<String, Int?>()
            lateinit var result: SharedPreferences.Editor
            result = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
                when (method.name) {
                    "putInt" -> { pending[args!![0] as String] = args[1] as Int; result }
                    "remove" -> { pending[args!![0] as String] = null; result }
                    "apply", "commit" -> {
                        pending.forEach { (key, value) -> if (value == null) values.remove(key) else values[key] = value }
                        if (method.name == "commit") true else null
                    }
                    else -> error("Unexpected editor call ${method.name}")
                }
            } as SharedPreferences.Editor
            return result
        }
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "contains" -> values.containsKey(args!![0])
                "getInt" -> values[args!![0]] ?: args[1]
                "edit" -> editor()
                else -> error("Unexpected preference call ${method.name}")
            }
        } as SharedPreferences
    }

    @Test fun legacyFollowValuesAreFrozenIntoIndependentPlatformIndicesForEveryOldTier() {
        val expected = mapOf(
            MusicPlatform.NETEASE to listOf(0, 1, 1, 2, 3, 3),
            MusicPlatform.QQ to listOf(0, 1, 2, 3, 3, 4),
            MusicPlatform.BODIAN to listOf(0, 0, 1, 2, 2, 2),
            MusicPlatform.KUGOU to listOf(0, 1, 2, 3, 3, 3),
            MusicPlatform.QISHUI to listOf(0, 1, 2, 3, 3, 3)
        )
        for (oldTier in 0..5) {
            val prefs = prefs("total_audio_quality_index" to oldTier, "qq_audio_quality_index" to -1)
            PlaybackQualityPreferences.migrate(prefs)
            assertFalse(prefs.contains("total_audio_quality_index"))
            for ((platform, indices) in expected) {
                assertEquals(indices[oldTier], prefs.getInt(PlaybackQualityPreferences.key(platform), -1))
                assertEquals(indices[oldTier], PlaybackQualityPreferences.resolve(prefs, platform))
            }
        }
    }

    @Test fun changingOnePlatformDoesNotAffectTheOthersOrReintroduceGlobalQuality() {
        val prefs = prefs("total_audio_quality_index" to 3)
        PlaybackQualityPreferences.migrate(prefs)
        prefs.edit().putInt("netease_audio_quality_index", 0).apply()
        assertEquals(0, PlaybackQualityPreferences.resolve(prefs, MusicPlatform.NETEASE))
        assertEquals(3, PlaybackQualityPreferences.resolve(prefs, MusicPlatform.QQ))
        assertEquals(2, PlaybackQualityPreferences.resolve(prefs, MusicPlatform.BODIAN))
        PlaybackQualityPreferences.migrate(prefs)
        assertEquals(0, PlaybackQualityPreferences.resolve(prefs, MusicPlatform.NETEASE))
        assertEquals(3, PlaybackQualityPreferences.resolve(prefs, MusicPlatform.QQ))
    }

    @Test fun explicitSelectionsRemainUnchangedDuringMigration() {
        val prefs = prefs("netease_audio_quality_index" to 2, "qq_audio_quality_index" to 1,
            "bodian_audio_quality_index" to 2, "total_audio_quality_index" to 0)
        PlaybackQualityPreferences.migrate(prefs)
        assertEquals(2, PlaybackQualityPreferences.resolve(prefs, MusicPlatform.NETEASE))
        assertEquals(1, PlaybackQualityPreferences.resolve(prefs, MusicPlatform.QQ))
        assertEquals(2, PlaybackQualityPreferences.resolve(prefs, MusicPlatform.BODIAN))
    }

    @Test fun freshSettingsOfferOnlyPlatformQualitiesAndDefaultToHighest() {
        val prefs = prefs()
        PlaybackQualityPreferences.migrate(prefs)
        for (platform in MusicPlatform.entries) {
            assertEquals(0, PlaybackQualityPreferences.resolve(prefs, platform))
            assertTrue(AudioQuality.options(platform).none { it.contains("跟随") })
        }
    }

    @Test fun olderLegacySettingsAndExplicitFollowAreBothMigratedCorrectly() {
        val prefs = prefs("audio_quality_index" to 3, "qq_audio_quality_index" to -1)
        PlaybackQualityPreferences.migrate(prefs)
        assertEquals(1, PlaybackQualityPreferences.resolve(prefs, MusicPlatform.NETEASE))
        assertEquals(0, PlaybackQualityPreferences.resolve(prefs, MusicPlatform.QQ))
        assertEquals(0, PlaybackQualityPreferences.resolve(prefs, MusicPlatform.KUGOU))
    }
}