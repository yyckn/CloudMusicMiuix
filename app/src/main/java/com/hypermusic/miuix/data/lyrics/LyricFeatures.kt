package com.hypermusic.miuix.data.lyrics

import com.hypermusic.miuix.data.model.SongLyrics

internal data class LyricFeatures(val wordTimed: Boolean, val romanization: Boolean, val duet: Boolean) {
    val labels: List<String> get() = buildList {
        if (wordTimed) add("逐字歌词")
        if (romanization) add("罗马音")
        if (duet) add("对唱")
    }

    companion object {
        fun from(lyrics: SongLyrics, platform: String): LyricFeatures {
            val lines = lyrics.lines.filter { !it.isArtistLine && it.text.isNotBlank() }
            val agents = lines.map { it.amllAgent.lowercase() }.toSet()
            return LyricFeatures(
                wordTimed = lines.any { line -> line.words.orEmpty().any { it.text.isNotBlank() && it.endAt > it.startAt } },
                romanization = lines.any { it.phonetic.isNotBlank() },
                duet = platform == "apple" && agents.containsAll(listOf("v1", "v2"))
            )
        }
    }
}
