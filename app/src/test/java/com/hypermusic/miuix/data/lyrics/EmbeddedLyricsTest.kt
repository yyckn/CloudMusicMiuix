package com.hypermusic.miuix.data.lyrics

import com.hypermusic.miuix.data.model.LyricLine
import com.hypermusic.miuix.data.model.SongLyrics
import org.junit.Assert.assertEquals
import org.junit.Test

class EmbeddedLyricsTest {
    @Test fun preservesTimesAndTranslationsWithoutRomanization() {
        val lyrics = SongLyrics(listOf(
            LyricLine(text = "歌手", startAt = 1200, isArtistLine = true),
            LyricLine(text = "原文", translation = "译文", phonetic = "roman", startAt = 62530)
        ))
        assertEquals("[00:01.20]（歌手）\n[01:02.53]原文\n[01:02.53]译文", formatEmbeddedLyrics(lyrics))
    }
}
