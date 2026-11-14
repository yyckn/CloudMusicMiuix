package com.hypermusic.miuix.data.lyrics

import org.junit.Assert.*
import org.junit.Test

class InstrumentalLyricsTest {
    @Test fun filteredInstrumentalNoticeRetainsItsFlagAcrossPlainAndWordFormats() {
        val wasEnabled = LyricExclusionRules.enabled
        LyricExclusionRules.updateEnabled(true)
        try {
            val results = listOf(
                LyricParser.parse(lrc = "[00:00.00]纯音乐，请欣赏", tlyric = "", yrc = ""),
                LyricParser.parseQrc(qrc = "[0,1000]纯音乐，请欣赏(0,1000)", tlyric = ""),
                LyricParser.parseKrc(krc = "[0,1000]<0,1000,0>纯音乐，请欣赏", tlyric = "")
            )
            for (lyrics in results) {
                assertTrue(lyrics.isPureMusic)
                assertTrue(lyrics.lines.isEmpty())
            }
        } finally { LyricExclusionRules.updateEnabled(wasEnabled) }
    }

    @Test fun absentPayloadIsNotEvidenceOfInstrumentalMusic() {
        val results = listOf(
            LyricParser.parse(lrc = "", tlyric = "", yrc = ""),
            LyricParser.parseQrc(qrc = "", tlyric = ""),
            LyricParser.parseKrc(krc = "", tlyric = "")
        )
        for (lyrics in results) {
            assertFalse(lyrics.isPureMusic)
            assertTrue(lyrics.lines.isEmpty())
        }
    }
}
