package com.hypermusic.miuix.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParsingTest {
    @Test fun choirCreditsAreExcludedBeforeTheyCanBeSplitIntoSingerAndBody() {
        val enabled = LyricExclusionRules.enabled
        LyricExclusionRules.updateEnabled(true)
        try {
            val qrc = "[12868,1244]合唱团监棚：(12868,500)王慧聪(13368,744)\n" +
                "[14113,1383]合唱团录音师：(14113,500)蔡文杰(14613,883)\n" +
                "[17712,1000]你(17712,500)呀(18212,500)"
            val result = LyricParser.parseQrc(qrc, "", artistNames = listOf("龚琳娜"))
            assertEquals(listOf("你呀"), result.lines.map { it.text })
            assertEquals(17712L, result.lines.single().words!!.first().startAt)
            assertFalse(result.lines.any { it.isArtistLine })
        } finally { LyricExclusionRules.updateEnabled(enabled) }
    }

    @Test fun singerPrefixesSurviveDefaultColonExclusionWithoutKeepingCredits() {
        val wasEnabled = LyricExclusionRules.enabled
        LyricExclusionRules.updateEnabled(true)
        try {
            val lyrics = LyricParser.parseQrc(
                qrc = "[18260,1820]汪(18260,260)：(18520,260)摘(18780,260)一(19040,260)颗(19300,260)苹(19560,260)果(19820,260)\n" +
                    "[26466,2310]BY2：像(26466,560)夏(27026,370)天(27396,630)的(28026,250)可(28276,250)乐(28526,250)\n" +
                    "[30000,1000]合：你(30000,500)好(30500,500)\n" +
                    "[31000,1000]作词：汪苏泷(31000,1000)",
                tlyric = "", artistNames = listOf("汪苏泷", "BY2"))
            assertEquals(3, lyrics.lines.count { it.isArtistLine })
            val apple = lyrics.lines.first { it.text == "摘一颗苹果" }
            assertEquals(18780L, apple.words!!.first().startAt)
            assertEquals("摘一颗苹果", apple.words!!.joinToString("") { it.text })
            assertTrue(lyrics.lines.any { it.text == "像夏天的可乐" && !it.words.isNullOrEmpty() })
            assertTrue(lyrics.lines.any { it.text == "你好" && !it.words.isNullOrEmpty() })
            assertFalse(lyrics.lines.any { it.text.contains("作词") })
            assertFalse(LyricExclusionRules.shouldExclude("其他信息：正文", 1000, "", listOf("汪苏泷", "BY2"), ""))
        } finally { LyricExclusionRules.updateEnabled(wasEnabled) }
    }

    @Test fun qrcRecognitionDoesNotDependOnTimestampMetadata() {
        assertTrue(QqMusicLyricsApi.isQrcContent("<?xml version=\"1.0\"?><Lyric LyricContent=\"[0,1000]你(0,500)好(500,500)\" />"))
        assertTrue(QqMusicLyricsApi.isQrcContent("[0,1000]你(0,500)好(500,500)"))
        assertFalse(QqMusicLyricsApi.isQrcContent("[00:01.00]普通歌词"))
    }

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
        assertTrue(lyrics.lines.any { line -> !line.words.isNullOrEmpty() && line.words!!.joinToString("") { it.text } == line.text })
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

        val enabled = LyricExclusionRules.enabled
        LyricExclusionRules.updateEnabled(false)
        try {
            val lyrics = LyricParser.parseQrc(
                qrc = qrc,
                tlyric = "",
                songTitle = "测试",
                songArtist = "周杰伦",
                artistNames = listOf("周杰伦")
            )

            assertTrue(lyrics.lines.any { it.isArtistLine && it.text == "周杰伦" })
            val line = lyrics.lines.first { it.text == "啊啊" }
            assertEquals("啊啊", line.words!!.joinToString("") { it.text })
            assertEquals(200L, line.words!!.first().startAt)
        } finally {
            LyricExclusionRules.updateEnabled(enabled)
        }
    }
}
