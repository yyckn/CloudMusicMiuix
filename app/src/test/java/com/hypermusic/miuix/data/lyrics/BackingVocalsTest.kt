package com.hypermusic.miuix.data.lyrics

import com.hypermusic.miuix.data.model.*
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import org.junit.Assert.*
import org.junit.Test

class BackingVocalsTest {
    private val track = Track("1", "See You Again", "Wiz Khalifa / Charlie Puth", "", "", "", sourceUrl = "", imgUrl = "")

    @Test fun onlyCompleteAsciiWrappersQualify() {
        for (value in listOf("(yeah)", " (yeah (again)) ")) assertTrue(value, BackingVocals.isWrapped(value))
        for (value in listOf("（yeah）", "Sing (yeah)", "(yeah) again", "(yeah) (again)", "()", "( )", "(yeah")) {
            assertFalse(value, BackingVocals.isWrapped(value))
        }
    }

    @Test fun qrcAndLrcParenthesesNeverOptIntoBackingVocalRendering() {
        val lyrics = LyricParser.parse(lrc = "[00:01.00](yeah)", tlyric = "", yrc = "")
        assertTrue(lyrics.lines.isNotEmpty())
        assertTrue(lyrics.lines.none { it.isBackingVocal })
        assertTrue(AppleMusicLyricsConverter.convert(lyrics).lines.single() is SyncedLine)
    }

    @Test fun removingSeparateBracketSpansPreservesWordTimes() {
        val words = listOf(LyricWord("(", 1000, 1001), LyricWord("Yeah", 1001, 1800), LyricWord(")", 1800, 1801))
        assertEquals(listOf(LyricWord("Yeah", 1001, 1800)), BackingVocals.displayWords(words))
    }

    @Test fun consecutiveBackingLinesAttachToPreviousNormalLineEvenWithoutWordTiming() {
        val song = SongLyrics(listOf(
            LyricLine("Main", startAt = 1000, endAt = 3000),
            LyricLine("(yeah)", "(耶)", 2500, 4000, isBackingVocal = true),
            LyricLine("(again)", startAt = 4100, endAt = 5500, isBackingVocal = true),
            LyricLine("Next", startAt = 6000, endAt = 8000)
        ))
        for (wordByWord in listOf(false, true)) {
            val converted = AppleMusicLyricsConverter.convert(song, wordByWord = wordByWord)
            assertEquals(2, converted.lines.size)
            val owner = converted.lines.first() as KaraokeLine.MainKaraokeLine
            assertEquals(3000, owner.end) // owner word clocks must not stretch to the group's end
            assertEquals(listOf("yeah", "again"), owner.accompanimentLines!!.map { it.syllables.joinToString("") { word -> word.content } })
            assertEquals("耶", owner.accompanimentLines!!.first().translation)
            assertEquals(5500, owner.accompanimentLines!!.last().end)
        }
    }

    @Test fun sampleSeeYouAgainRetainsAllBackingWordsAndTheirOwner() {
        val xml = javaClass.classLoader!!.getResourceAsStream("see-you-again-ttml.xml")!!.bufferedReader().use { it.readText() }
        val parsed = TtmlLyricsParser.parse(xml, track, false)
        assertEquals(11, parsed.lines.count { it.isBackingVocal })
        val converted = AppleMusicLyricsConverter.convert(parsed)
        val allBacking = converted.lines.filterIsInstance<KaraokeLine.MainKaraokeLine>().flatMap { it.accompanimentLines.orEmpty() }
        assertEquals(11, allBacking.size)
        val first = allBacking.first()
        assertEquals(85844, first.start)
        assertEquals(86508, first.end)
        assertEquals("I'll see you again", first.syllables.joinToString("") { it.content })
        val owner = converted.lines.filterIsInstance<KaraokeLine.MainKaraokeLine>().single { first in it.accompanimentLines.orEmpty() }
        assertEquals(80710, owner.start)
        assertTrue(allBacking.none { it.syllables.joinToString("") { word -> word.content }.let { text -> text.startsWith('(') || text.endsWith(')') } })
    }

    @Test fun earlyBackgroundSpanKeepsItsDocumentOwnerAfterTimeSorting() {
        val xml = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div>
            <p begin="0s" end="1.5s">Previous</p>
            <p begin="2s" end="3s">Owner<span ttm:role="x-bg" begin="1s" end="3.5s">(early)</span></p>
            </div></body></tt>"""
        val lyrics = AppleMusicLyricsConverter.convert(TtmlLyricsParser.parse(xml, track, false))
        assertEquals(2, lyrics.lines.size)
        val owner = lyrics.lines[1] as KaraokeLine.MainKaraokeLine
        assertEquals(2000, owner.start)
        assertEquals(1000, owner.accompanimentLines!!.single().start)
        assertEquals("early", owner.accompanimentLines!!.single().syllables.single().content)
    }
}
