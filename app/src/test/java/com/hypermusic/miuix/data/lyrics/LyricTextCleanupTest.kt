package com.hypermusic.miuix.data.lyrics

import com.hypermusic.miuix.data.model.LyricLine
import com.hypermusic.miuix.data.model.LyricWord
import com.hypermusic.miuix.data.model.Track
import org.junit.Assert.*
import org.junit.Test

class LyricTextCleanupTest {
    @Test fun pairedNotesAreRemovedWithoutTouchingInternalOrUnpairedNotes() {
        fun clean(text: String) = LyricTextCleanup.removeOuterMusicNotes(LyricLine(text, startAt = 100)).text
        assertEquals("雪花是长翅膀的温柔", clean("♪ 雪花是长翅膀的温柔 ♪"))
        assertEquals("雪花 ♪ 温柔", clean("♪ 雪花 ♪ 温柔 ♪"))
        assertEquals("♪ 雪花", clean("♪ 雪花"))
        assertEquals("雪花 ♪", clean("雪花 ♪"))
        assertEquals("普通歌词", clean("普通歌词"))
    }

    @Test fun wordTextIsCleanedButWordAndLineTimingStayUnchanged() {
        val line = LyricLine("♪ 雪花是温柔 ♪", "♪ Translation ♪", startAt = 100, endAt = 1800,
            words = listOf(LyricWord("♪ ", 100, 200), LyricWord("雪花", 200, 600),
                LyricWord("是温柔 ♪", 600, 1800)), amllAgent = "v2")
        val clean = LyricTextCleanup.removeOuterMusicNotes(line)
        assertEquals("雪花是温柔", clean.text)
        assertEquals("Translation", clean.translation)
        assertEquals(listOf(LyricWord("雪花", 200, 600), LyricWord("是温柔", 600, 1800)), clean.words)
        assertEquals(100L, clean.startAt)
        assertEquals(1800L, clean.endAt)
        assertEquals("v2", clean.amllAgent)
    }

    @Test fun lrcAndTtmlBothApplyTheCleanup() {
        val lrc = LyricParser.parse("[00:01.00]♪ 雪花是长翅膀的温柔 ♪", "", "")
        assertEquals("雪花是长翅膀的温柔", lrc.lines.single().text)
        val track = Track("test", "", "", "", "", "", sourceUrl = "", imgUrl = "")
        val xml = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="1s" end="3s">♪ 雪花是长翅膀的温柔 ♪</p></div></body></tt>"""
        assertEquals("雪花是长翅膀的温柔", TtmlLyricsParser.parse(xml, track).lines.single().text)
    }

    @Test fun initialWaitEndsAt1300msAndDoesNotRestartWhenOpeningThePlayerLate() {
        assertEquals(1300L, initialLyricWaitRemaining(5000, 5000))
        assertEquals(1L, initialLyricWaitRemaining(5000, 6299))
        assertEquals(0L, initialLyricWaitRemaining(5000, 6300))
        assertEquals(0L, initialLyricWaitRemaining(5000, 10000))
        assertEquals(0L, initialLyricWaitRemaining(0, 5000))
    }
}
