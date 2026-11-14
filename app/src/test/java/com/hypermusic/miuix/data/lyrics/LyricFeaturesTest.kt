package com.hypermusic.miuix.data.lyrics

import com.hypermusic.miuix.data.model.LyricLine
import com.hypermusic.miuix.data.model.LyricWord
import com.hypermusic.miuix.data.model.SongLyrics
import com.hypermusic.miuix.data.model.Track
import org.junit.Assert.*
import org.junit.Test

class LyricFeaturesTest {
    @Test fun onlySupportedFeaturesAreListedInOrder() {
        val lyrics = SongLyrics(listOf(
            LyricLine("甲", startAt = 100, words = listOf(LyricWord("甲", 100, 200)), phonetic = "ka", amllAgent = "v1"),
            LyricLine("乙", startAt = 200, amllAgent = "v2")
        ))
        assertEquals(listOf("逐字歌词", "罗马音", "对唱"), LyricFeatures.from(lyrics, "apple").labels)
        assertEquals(listOf("逐字歌词", "罗马音"), LyricFeatures.from(lyrics, "qq").labels)
    }

    @Test fun oneVoiceAndOrdinaryLyricsDoNotBecomeDuets() {
        assertEquals(emptyList<String>(), LyricFeatures.from(SongLyrics(listOf(
            LyricLine("普通歌词", startAt = 100, amllAgent = "v1")
        )), "apple").labels)
    }

    @Test fun metadataAndInvalidWordTimingDoNotAdvertiseWordLyrics() {
        val lyrics = SongLyrics(listOf(
            LyricLine("歌手", startAt = 0, isArtistLine = true, phonetic = "artist",
                words = listOf(LyricWord("歌手", 0, 100))),
            LyricLine("正文", startAt = 100, words = listOf(LyricWord("正文", 100, 100)))
        ))
        assertEquals(emptyList<String>(), LyricFeatures.from(lyrics, "netease").labels)
    }

    @Test fun realAmllDuetIsRecognizedFromParsedAgents() {
        val xml = javaClass.getResource("/duet-yidian-ttml.xml")!!.readText()
        val track = Track(id = "test", title = "一点", artist = "", artistId = "", album = "", albumId = "", sourceUrl = "", imgUrl = "")
        val features = LyricFeatures.from(TtmlLyricsParser.parse(xml, track, showRomanization = true), "apple")
        assertTrue(features.wordTimed)
        assertTrue(features.duet)
    }
}
