package com.hypermusic.miuix.data.lyrics

import com.hypermusic.miuix.data.model.*
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import org.junit.Assert.*
import org.junit.Test

class AmllDuetTest {
    private val track = Track("1", "一点", "Muyoi / Pezzi", "", "一点", "", sourceUrl = "", imgUrl = "")
    private val xml = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div>
        <p begin="1s" end="3s" ttm:agent="v1"><span begin="1s" end="3s">Left</span></p>
        <p begin="4s" end="6s" ttm:agent="v2">Right<span ttm:role="x-bg" ttm:agent="v1" begin="3.5s" end="7s">(Backing)</span></p>
        <p begin="8s" end="9s" ttm:agent="v3">Unknown</p>
        <p begin="10s" end="11s">Unmarked</p>
        </div></body></tt>"""

    @Test fun parserPreservesAgentsAndBackgroundUsesItsParagraphAgent() {
        val parsed = TtmlLyricsParser.parse(xml, track, false)
        assertEquals(listOf("v1", "v2", "v2", "v3", ""), parsed.lines.map { it.amllAgent })
        assertTrue(parsed.lines[2].isBackingVocal)
    }

    @Test fun bothTimedAndPlainLyricsKeepDuetSidesEvenWhenWordTimingIsDisabled() {
        val parsed = TtmlLyricsParser.parse(xml, track, false)
        for (wordByWord in listOf(false, true)) {
            val converted = AppleMusicLyricsConverter.convert(parsed, wordByWord = wordByWord)
            assertEquals(4, converted.lines.size)
            assertEquals(KaraokeAlignment.Start, (converted.lines[0] as KaraokeLine.MainKaraokeLine).alignment)
            val right = converted.lines[1] as KaraokeLine.MainKaraokeLine
            assertEquals(KaraokeAlignment.End, right.alignment)
            assertEquals(KaraokeAlignment.End, right.accompanimentLines!!.single().alignment)
            assertEquals(3500, right.accompanimentLines!!.single().start)
            assertTrue(converted.lines[2] is SyncedLine)
            assertTrue(converted.lines[3] is SyncedLine)
        }
    }

    @Test fun independentlyTaggedBackingParagraphStillFollowsItsNormalOwner() {
        val song = SongLyrics(listOf(
            LyricLine("Main", startAt = 1000, endAt = 3000, amllAgent = "v2"),
            LyricLine("(Backing)", startAt = 2000, endAt = 4000, isBackingVocal = true, amllAgent = "v1")
        ))
        val main = AppleMusicLyricsConverter.convert(song).lines.single() as KaraokeLine.MainKaraokeLine
        assertEquals(KaraokeAlignment.End, main.alignment)
        assertEquals(main.alignment, main.accompanimentLines!!.single().alignment)
    }

    @Test fun physicalSidesAreStableForRtlTextAndMixedLanguageBacking() {
        val song = SongLyrics(listOf(
            LyricLine("مرحبا", startAt = 1000, endAt = 3000, amllAgent = "v1"),
            LyricLine("Right", startAt = 4000, endAt = 6000, amllAgent = "v2"),
            LyricLine("(مرحبا)", startAt = 5000, endAt = 7000, isBackingVocal = true, amllAgent = "v2")
        ))
        val converted = AppleMusicLyricsConverter.convert(song)
        // The renderer flips logical Start/End for RTL; the final screen sides still stay left/right.
        assertEquals(KaraokeAlignment.End, (converted.lines[0] as KaraokeLine.MainKaraokeLine).alignment)
        val right = converted.lines[1] as KaraokeLine.MainKaraokeLine
        assertEquals(KaraokeAlignment.End, right.alignment)
        assertEquals(KaraokeAlignment.Start, right.accompanimentLines!!.single().alignment)
    }

    @Test fun otherLyricParsersDoNotAssignDuetAgents() {
        val plain = LyricParser.parse("[00:01]普通歌词", "", "")
        val qrc = LyricParser.parseQrc("[1000,1000]普通歌词(1000,1000)", "")
        val krc = LyricParser.parseKrc("[1000,1000]<0,1000,0>普通歌词", "")
        for (lyrics in listOf(plain, qrc, krc)) {
            assertTrue(lyrics.lines.isNotEmpty())
            assertTrue(lyrics.lines.all { it.amllAgent.isEmpty() })
        }
        assertTrue(AppleMusicLyricsConverter.convert(plain).lines.single() is SyncedLine)
    }

    @Test fun realYidianSampleCarriesBothVoicesThroughConversion() {
        val xml = javaClass.classLoader!!.getResourceAsStream("duet-yidian-ttml.xml")!!.bufferedReader().use { it.readText() }
        val parsed = TtmlLyricsParser.parse(xml, track, false)
        assertEquals("v2", parsed.lines.first().amllAgent)
        assertEquals("v1", parsed.lines.first { it.startAt == 19724L }.amllAgent)
        val normals = parsed.lines.filterNot { it.isBackingVocal }
        val converted = AppleMusicLyricsConverter.convert(parsed)
        for (line in converted.lines) {
            val original = normals.first { it.startAt.toInt() == line.start }
            val main = line as KaraokeLine.MainKaraokeLine
            val expected = if (original.amllAgent == "v2") KaraokeAlignment.End else KaraokeAlignment.Start
            assertEquals(expected, main.alignment)
            assertTrue(main.accompanimentLines.orEmpty().all { it.alignment == main.alignment })
        }
    }
}
