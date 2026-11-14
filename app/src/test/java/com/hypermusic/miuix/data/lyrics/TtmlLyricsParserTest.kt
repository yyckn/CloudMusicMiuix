package com.hypermusic.miuix.data.lyrics

import com.hypermusic.miuix.data.model.Track
import org.junit.Assert.*
import org.junit.Test

class TtmlLyricsParserTest {
    private val track = Track(id = "1", title = "Example", artist = "Singer", artistId = "", album = "Album", albumId = "", sourceUrl = "", imgUrl = "")
    private val xml = """
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" xmlns:itunes="http://itunes.apple.com/lyric-ttml-extensions">
          <head><metadata><iTunesMetadata><translations><translation xml:lang="zh"><text for="L1">你好世界</text></translation></translations>
          <transliterations><transliteration><text for="L1">konnichiwa</text></transliteration></transliterations></iTunesMetadata></metadata></head>
          <body><div><p begin="00:01.000" end="00:03.000" itunes:key="L1"><span begin="1s" end="1.5s">Hello</span> <span begin="1.5s" end="3s">world</span><span ttm:role="x-bg">(backing)</span></p>
          <p begin="3s" end="4s">Second<span ttm:role="x-translation">第二句</span><span ttm:role="x-roman">dierju</span></p></div></body>
        </tt>
    """.trimIndent()
    @Test fun parsesTimedWordsAndBothTranslationFormats() {
        val result = TtmlLyricsParser.parse(xml, track, true)
        assertEquals(3, result.lines.size)
        assertFalse(result.isPureMusic)
        val first = result.lines[0]
        assertEquals("Hello world", first.text)
        assertEquals("你好世界", first.translation)
        assertEquals("konnichiwa", first.phonetic)
        assertEquals(2, first.words!!.size)
        assertEquals(first.text, first.words!!.joinToString("") { it.text })
        assertEquals(1500L, first.words!![1].startAt)
        assertEquals("(backing)", result.lines[1].text)
        assertEquals("第二句", result.lines[2].translation)
    }
    @Test fun romanizationRespectsSetting() {
        assertTrue(TtmlLyricsParser.parse(xml, track, false).lines.all { it.phonetic.isEmpty() })
    }
    @Test fun rejectsExternalEntities() {
        val unsafe = """<!DOCTYPE tt [<!ENTITY xxe SYSTEM "file:///nonexistent">]><tt xmlns="http://www.w3.org/ns/ttml"><body><p begin="1s">&xxe;</p></body></tt>"""
        assertTrue(runCatching { TtmlLyricsParser.parse(unsafe, track) }.isFailure)
    }
}
