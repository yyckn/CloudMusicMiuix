package com.hypermusic.miuix.data.lyrics

import com.hypermusic.miuix.data.model.Track
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import org.junit.Assert.*
import org.junit.Test

class TtmlInvalidTimingTest {
    private val track = Track("1", "反乌托邦Pt.2", "亞細亞曠世奇才 / 洛天依Official", "", "反乌托邦Pt.2", "", sourceUrl = "", imgUrl = "")
    private fun parse(content: String) = TtmlLyricsParser.parse(
        """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div>$content</div></body></tt>""", track, false)

    private fun timedLine(start: Long, end: Long, first: Long, last: Long) = parse(
        """<p begin="${start}ms" end="${end}ms"><span begin="${first}ms" end="${(first + last) / 2}ms">你</span><span begin="${(first + last) / 2}ms" end="${last}ms">好</span></p>"""
    ).lines.single()

    @Test fun apparentlyValidSongLongIntervalsOnlyTightenTheExcessiveSide() {
        val leading = timedLine(0, 180000, 175268, 177188)
        assertEquals(175268L, leading.startAt)
        assertEquals(180000L, leading.endAt) // retain the ordinary 2.8-second trailing padding
        val trailing = timedLine(0, 180000, 1000, 3000)
        assertEquals(0L, trailing.startAt) // retain ordinary leading padding
        assertEquals(3000L, trailing.endAt)
        val both = timedLine(0, 180000, 85000, 87000)
        assertEquals(85000L, both.startAt)
        assertEquals(87000L, both.endAt)
    }

    @Test fun disjointIntervalsRecoverTheirWholeSyllableInterval() {
        val early = timedLine(0, 5000, 30000, 35000)
        assertEquals(30000L, early.startAt)
        assertEquals(35000L, early.endAt)
        val late = timedLine(30000, 35000, 1000, 3000)
        assertEquals(1000L, late.startAt)
        assertEquals(3000L, late.endAt)
    }

    @Test fun normalPaddingAndExactlyFifteenSecondsOfPaddingArePreserved() {
        for ((start, end, first, last) in listOf(
            listOf(1000L, 5000L, 2000L, 3000L),
            listOf(0L, 19000L, 15000L, 17000L),
            listOf(1000L, 18000L, 1000L, 3000L)
        )) {
            val line = timedLine(start, end, first, last)
            assertEquals(start, line.startAt)
            assertEquals(end, line.endAt)
        }
        val excessive = timedLine(0, 19000, 15001, 17001)
        assertEquals(15001L, excessive.startAt)
        assertEquals(19000L, excessive.endAt)
    }

    @Test fun longPhrasesAndInternalPausesRequireTheDurationRatioToo() {
        val underRatio = timedLine(0, 79000, 20000, 40000)
        assertEquals(0L, underRatio.startAt)
        assertEquals(79000L, underRatio.endAt)
        val atRatio = timedLine(0, 80000, 20000, 40000)
        assertEquals(20000L, atRatio.startAt)
        assertEquals(40000L, atRatio.endAt)
        val pause = parse("""<p begin="0s" end="180s"><span begin="10s" end="11s">前半句</span><span begin="99s" end="100s">后半句</span></p>""").lines.single()
        assertEquals(0L, pause.startAt)
        assertEquals(180000L, pause.endAt)
    }

    @Test fun substantialClippingIsExpandedButSmallRoundingDifferencesAreNot() {
        val clippedStart = timedLine(5000, 10000, 3000, 8000)
        assertEquals(3000L, clippedStart.startAt)
        assertEquals(10000L, clippedStart.endAt)
        val clippedEnd = timedLine(1000, 5000, 2000, 8000)
        assertEquals(1000L, clippedEnd.startAt)
        assertEquals(8000L, clippedEnd.endAt)
        val tolerance = timedLine(2000, 7000, 1000, 8000)
        assertEquals(2000L, tolerance.startAt)
        assertEquals(7000L, tolerance.endAt)
    }

    @Test fun incompleteOrNonMonotonicWordTimesDoNotRewriteAValidParagraph() {
        for (spans in listOf(
            """<span begin="175s" end="177s">你</span><span begin="178s" end="176s">好</span>""",
            """<span begin="175s" end="177s">你</span><span begin="170s" end="172s">好</span>""",
            """<span begin="175s" end="177s">你</span><span>这部分没有时间</span>"""
        )) {
            val line = parse("""<p begin="0s" end="180s">$spans</p>""").lines.single()
            assertEquals(0L, line.startAt)
            assertEquals(180000L, line.endAt)
        }
    }

    @Test fun unTimedLyricsRetainTheirDeclaredInterval() {
        val line = parse("""<p begin="0s" end="180s">没有逐字时间的歌词</p>""").lines.single()
        assertEquals(0L, line.startAt)
        assertEquals(180000L, line.endAt)
    }

    @Test fun excessiveBackingPaddingIsRepairedWhileItsOwnerStaysUnchanged() {
        val lines = parse("""<p begin="174s" end="180s" ttm:agent="v2">Main<span ttm:role="x-bg" begin="0s" end="177s"><span begin="175s" end="176s">(backing)</span></span></p>""").lines
        assertEquals(174000L, lines[0].startAt)
        assertEquals(180000L, lines[0].endAt)
        assertEquals(175000L, lines[1].startAt)
        assertEquals(177000L, lines[1].endAt)
        assertTrue(lines[1].isBackingVocal)
        assertEquals("v2", lines[1].amllAgent)
    }

    @Test fun zeroAndReversedParagraphTimesRecoverFromSyllables() {
        for ((begin, end) in listOf("0s" to "0s", "3s" to "0s")) {
            val line = parse("""<p begin="$begin" end="$end"><span begin="1s" end="1.5s">你</span><span begin="1.5s" end="2s">好</span></p>""").lines.single()
            assertEquals(1000L, line.startAt)
            assertEquals(2000L, line.endAt)
            assertEquals(listOf(1000L, 1500L), line.words!!.map { it.startAt })
        }
    }

    @Test fun validParagraphPaddingIsPreservedRatherThanRecalculated() {
        val line = parse("""<p begin="1s" end="5s"><span begin="2s" end="3s">正文</span></p>""").lines.single()
        assertEquals(1000L, line.startAt)
        assertEquals(5000L, line.endAt)
    }

    @Test fun malformedBackingTimeIsRecoveredWithoutChangingItsOwnerOrAgent() {
        val lines = parse("""<p begin="3s" end="4s" ttm:agent="v2">Main<span ttm:role="x-bg" begin="0s" end="0s"><span begin="1s" end="2s">(early)</span></span></p>""").lines
        assertEquals(3000L, lines[0].startAt)
        assertEquals(1000L, lines[1].startAt)
        assertEquals(2000L, lines[1].endAt)
        assertTrue(lines[1].isBackingVocal)
        assertEquals("v2", lines[1].amllAgent)
    }

    @Test fun actualFinalDuetIsRetainedAtSongEndInsteadOfStartingAtZero() {
        val xml = javaClass.classLoader!!.getResourceAsStream("ttml-zero-paragraph-time.xml")!!.bufferedReader().use { it.readText() }
        val lyrics = TtmlLyricsParser.parse(xml, track, false)
        val duplicated = lyrics.lines.filter { it.text == "在这反乌托邦里" }
        assertEquals(2, duplicated.size)
        assertEquals(setOf("v1", "v2"), duplicated.map { it.amllAgent }.toSet())
        assertTrue(duplicated.all { it.startAt == 175268L && it.endAt == 177188L })
        assertTrue(lyrics.lines.none { it.startAt == 0L })
        val processed = formatProcessedWordLyrics(lyrics)
        assertFalse(processed.lineSequence().any { it.startsWith("[0,") })
        assertEquals(2, processed.lineSequence().count { it.startsWith("[175268,1920]") })
        val converted = AppleMusicLyricsConverter.convert(lyrics).lines
        val finalLines = converted.filter { it.start == 175268 }
        assertEquals(2, finalLines.size)
        for (line in finalLines) {
            assertEquals(177188, line.end)
            assertEquals(175268, (line as KaraokeLine.MainKaraokeLine).syllables.first().start)
            assertEquals(177188, line.syllables.last().end)
        }
    }
}
