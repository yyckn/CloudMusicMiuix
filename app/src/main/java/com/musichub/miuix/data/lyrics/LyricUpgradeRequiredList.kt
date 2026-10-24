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
 * 「需要升级为逐字歌词」的歌曲列表：
 * 即使网易云本身已有（或没有）逐字歌词，也优先尝试从其他平台获取逐字歌词。
 * 与 [LyricUpgradeBlacklist] 互斥。
 */
object LyricUpgradeRequiredList {

    private const val PREFS_NAME = "lyric_upgrade_required"
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
