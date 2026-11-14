package com.hypermusic.miuix.data.lyrics

import android.content.Context
import androidx.annotation.Keep
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hypermusic.miuix.data.model.Track

/** Explicit selections, keyed by both source and ID of the song being played. */
object LyricMatches {
    @Keep data class Entry(val original: Track, val platform: String, val match: Track)
    private val gson = Gson()
    private var prefs: android.content.SharedPreferences? = null
    @Volatile private var initialized = false
    var entries by mutableStateOf<List<Entry>>(emptyList())
        private set

    @Synchronized fun init(context: Context) {
        if (initialized) return
        prefs = context.applicationContext.getSharedPreferences("lyric_matches", Context.MODE_PRIVATE)
        entries = runCatching {
            val type = object : TypeToken<List<Entry>>() {}.type
            gson.fromJson<List<Entry>>(prefs?.getString("entries", "[]"), type) ?: emptyList()
        }.getOrDefault(emptyList()).filter { it.original.id.isNotBlank() && it.match.id.isNotBlank() }
        initialized = true
    }
    fun find(track: Track): Entry? = entries.firstOrNull { it.original.source == track.source && it.original.id == track.id }
    fun save(original: Track, platform: String, match: Track) {
        require(platform in setOf("netease", "qq", "kugou", "apple"))
        require(LyricsMatch.durationMatches(original.duration, match.duration)) { "歌曲与歌词的时长相差超过6秒，无法匹配" }
        entries = entries.filterNot { it.original.source == original.source && it.original.id == original.id } + Entry(original, platform, match)
        persist()
    }
    fun remove(track: Track) {
        entries = entries.filterNot { it.original.source == track.source && it.original.id == track.id }
        persist()
    }
    private fun persist() { prefs?.edit()?.putString("entries", gson.toJson(entries))?.apply() }
}
