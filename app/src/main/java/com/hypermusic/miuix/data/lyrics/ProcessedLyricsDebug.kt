package com.hypermusic.miuix.data.lyrics

import com.hypermusic.miuix.data.model.SongLyrics

/** YRC-style debug text from the final parsed model; no translations or phonetics. */
internal fun formatProcessedWordLyrics(lyrics: SongLyrics?): String {
    if (lyrics == null || lyrics.lines.none { !it.words.isNullOrEmpty() }) return ""
    return lyrics.lines.filter { it.isArtistLine || !it.words.isNullOrEmpty() }.joinToString("\n") { line ->
        val words = line.words.orEmpty()
        val end = maxOf(line.endAt, words.maxOfOrNull { it.endAt } ?: line.startAt)
        buildString {
            append('[').append(line.startAt).append(',').append((end - line.startAt).coerceAtLeast(0)).append(']')
            if (line.isArtistLine) append(line.text) else words.forEach { word ->
                append('(').append(word.startAt).append(',').append((word.endAt - word.startAt).coerceAtLeast(0))
                    .append(",0)").append(word.text)
            }
        }
    }
}
