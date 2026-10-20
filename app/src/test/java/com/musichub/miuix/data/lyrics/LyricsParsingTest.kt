package com.musichub.miuix.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParsingTest {

    @Test
    fun qqQrcDecryptsAndParses() {
        val hex = javaClass.classLoader!!
            .getResourceAsStream("qq-qrc-hex.txt")!!
            .bufferedReader()
            .use { it.readText() }
            .trim()

        val qrc = QqMusicQrcDecryptor.decrypt(hex)
        assertTrue(qrc.contains("LyricContent"))

        val lyrics = LyricParser.parseQrc(
            qrc = qrc,
            tlyric = "",
            songTitle = "晴天",
            songArtist = "周杰伦",
            artistNames = listOf("周杰伦")
        )

        assertTrue(lyrics.lines.isNotEmpty())
        assertTrue(lyrics.lines.any { !it.words.isNullOrEmpty() })
        assertFalse(lyrics.lines.any { it.text.contains("作词") })
        assertTrue(lyrics.lines.any { it.text.contains("晴天") })
    }

    @Test
    fun krcParsesWordsAndFiltersCredits() {
        val krc = """
            [ti:测试]
            [0,1000]<0,200,0>作<200,200,0>词<400,200,0>：<600,200,0>某<800,200,0>人
            [1000,2000]<0,500,0>你<500,500,0>好<1000,500,0>世<1500,500,0>界
        """.trimIndent()

        val lyrics = LyricParser.parseKrc(
            krc = krc,
            tlyric = "",
            songTitle = "测试",
            songArtist = "测试歌手",
            artistNames = listOf("测试歌手")
        )

        assertFalse(lyrics.lines.any { it.text.contains("作词") })
        val line = lyrics.lines.firstOrNull { it.text == "你好世界" }
        assertNotNull(line)
        assertEquals(4, line!!.words?.size)
        assertEquals(3000L, line.endAt)
    }

    @Test
    fun qrcSplitsArtistLine() {
        val qrc = "[0,1000]周杰伦：(0,200)啊(200,300)啊(500,500)"

        val lyrics = LyricParser.parseQrc(
            qrc = qrc,
            tlyric = "",
            songTitle = "测试",
            songArtist = "周杰伦",
            artistNames = listOf("周杰伦")
        )

        assertTrue(lyrics.lines.any { it.isArtistLine && it.text == "周杰伦" })
        assertTrue(lyrics.lines.any { it.text == "啊啊" })
    }
}
