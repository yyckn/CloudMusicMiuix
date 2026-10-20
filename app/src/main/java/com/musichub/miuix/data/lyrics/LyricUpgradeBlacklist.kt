package com.musichub.miuix.data.lyrics

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.Keep
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * “自动升级为逐字歌词”的黑名单：
 * 加入后该歌曲不再从其他平台升级歌词，只使用网易云原歌词。
 */
object LyricUpgradeBlacklist {

    private const val PREFS_NAME = "lyric_upgrade_blacklist"
    private const val KEY_ENTRIES = "entries"

    @Keep
    data class Entry(
        val id: String = "",
        val title: String = "",
        val artist: String = "",
        val album: String = ""
    )

    private val gson = Gson()
    private var prefs: SharedPreferences? = null

    var entries by mutableStateOf<List<Entry>>(emptyList())
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        entries = runCatching {
            val json = prefs?.getString(KEY_ENTRIES, null) ?: return@runCatching emptyList()
            val type = object : TypeToken<List<Entry>>() {}.type
            gson.fromJson<List<Entry>>(json, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun contains(id: String): Boolean = entries.any { it.id == id }

    fun add(entry: Entry) {
        if (entry.id.isEmpty() || contains(entry.id)) return
        entries = entries + entry
        persist()
    }

    fun remove(id: String) {
        val updated = entries.filterNot { it.id == id }
        if (updated.size == entries.size) return
        entries = updated
        persist()
    }

    private fun persist() {
        prefs?.edit()?.putString(KEY_ENTRIES, gson.toJson(entries))?.apply()
    }
}
