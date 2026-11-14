package com.hypermusic.miuix.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodianLyricsTest {
    @Test fun partialWordLyricsKeepUntimedLines() {
        val track = com.hypermusic.miuix.data.model.Track(id = "bodian_1", title = "", artist = "",
            artistId = "", album = "", albumId = "", source = "bodian", sourceUrl = "", imgUrl = "", duration = 5000)
        val lyrics = BodianLyricsApi.parseLyrics("[00:01.000]你好\n[00:03.000]世界", "[1000,1000]<0,500,0>你<500,500,0>好", track)
        assertEquals(listOf("你好", "世界"), lyrics.lines.map { it.text })
        assertEquals(2, lyrics.lines.first().words?.size)
        assertTrue(lyrics.lines.last().words.isNullOrEmpty())
    }
    @Test fun decodesOctalFactorsAndRelativeWordTiming() {
        val (plain, word) = BodianLyricsApi.decode("[kuwo:127]\n[00:02.250]<3150,-3150>你<6750,450>好")
        assertTrue(plain.contains("[00:02.250]你好"))
        assertEquals("[2250,900]<0,450,0>你<450,450,0>好", word)
    }

    @Test fun invalidFactorsFallBackToPlainLyrics() {
        val (plain, word) = BodianLyricsApi.decode("[kuwo:0]\n[00:01.000]<10,0>字")
        assertTrue(plain.contains("字"))
        assertEquals("", word)
    }
}
