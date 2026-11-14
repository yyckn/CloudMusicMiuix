package com.hypermusic.miuix.data.lyrics

import com.hypermusic.miuix.data.model.SongLyrics

/** 将已按用户设置处理的歌词转换为可写入音频标签的逐行 LRC。 */
internal fun formatEmbeddedLyrics(lyrics: SongLyrics): String = buildString {
    lyrics.lines.forEach { line ->
        val millis = line.startAt.coerceAtLeast(0)
        val timestamp = "[%02d:%02d.%02d]".format(java.util.Locale.ROOT, millis / 60000, millis / 1000 % 60, millis / 10 % 100)
        val text = line.text.trim()
        if (text.isNotEmpty()) {
            val content = if (line.isArtistLine) "（${text.trim('（', '）', '(', ')').trim()}）" else text
            append(timestamp).append(content).append('\n')
        }
        val translation = line.translation.trim()
        if (translation.isNotEmpty()) append(timestamp).append(translation).append('\n')
    }
}.trimEnd()
