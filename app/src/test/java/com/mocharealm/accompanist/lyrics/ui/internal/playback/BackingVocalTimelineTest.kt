package com.mocharealm.accompanist.lyrics.ui.internal.playback

import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.*
import com.mocharealm.accompanist.lyrics.ui.preparation.PreparedLine
import com.mocharealm.accompanist.lyrics.ui.preparation.PreparedLyrics
import org.junit.Assert.*
import org.junit.Test

class BackingVocalTimelineTest {
    private fun backing(text: String, start: Int, end: Int) = KaraokeLine.AccompanimentKaraokeLine(
        listOf(KaraokeSyllable(text, start, end)), null, KaraokeAlignment.Start, start, end)
    private fun main(text: String, start: Int, end: Int, backing: List<KaraokeLine.AccompanimentKaraokeLine>? = null) =
        KaraokeLine.MainKaraokeLine(listOf(KaraokeSyllable(text, start, end)), null, KaraokeAlignment.Start, start, end, accompanimentLines = backing)
    private fun prepared(source: KaraokeLine, after: List<PreparedLine> = emptyList()) = PreparedLine(
        source, emptyList(), emptyList(), 400f, 30f, false, emptyList(), after, null, null)

    @Test fun earlyBackingRevealsAtItsOwnCueWithoutAdvancingNormalFocus() {
        val previous = main("Previous", 0, 2500)
        val early = backing("early", 1000, 3200)
        val late = backing("late", 3500, 5000)
        val owner = main("Owner", 2000, 4000, listOf(early, late))
        val next = main("Next", 5500, 7000)
        val pe = prepared(early); val pl = prepared(late)
        val timeline = LyricsPlaybackTimeline(SyncedLyrics(listOf(previous, owner, next)),
            PreparedLyrics(listOf(prepared(previous), prepared(owner, listOf(pe, pl)), prepared(next))))
        timeline.update(999)
        assertFalse(timeline.state.line(pe).visible.value)
        timeline.update(1000)
        assertTrue(timeline.state.line(pe).visible.value)
        assertFalse(timeline.state.line(pl).visible.value)
        assertEquals(listOf(0), timeline.focus.value.allIndices)
        timeline.update(1999)
        assertEquals(listOf(0), timeline.focus.value.allIndices)
        timeline.update(2000)
        assertTrue(timeline.state.line(pl).visible.value)
        assertEquals(listOf(0, 1), timeline.focus.value.allIndices)
        timeline.update(3000)
        assertEquals(1, timeline.focus.value.firstIndex)
        timeline.update(4999)
        assertTrue(timeline.state.line(pe).visible.value)
        assertEquals(1, timeline.focus.value.firstIndex)
        timeline.update(5000)
        assertFalse(timeline.state.line(pe).visible.value)
        assertFalse(timeline.state.line(pl).visible.value)
        assertEquals(2, timeline.focus.value.firstIndex)
        timeline.update(1500)
        assertTrue(timeline.state.line(pe).visible.value)
        assertFalse(timeline.state.line(pl).visible.value)
        assertEquals(listOf(0), timeline.focus.value.allIndices)
    }

    @Test fun earlyBackingEndsIntroDotsWithoutStartingNormalFocus() {
        val child = backing("early", 7000, 11000)
        val owner = main("Owner", 10000, 13000, listOf(child))
        val pc = prepared(child)
        val timeline = LyricsPlaybackTimeline(SyncedLyrics(listOf(owner)), PreparedLyrics(listOf(prepared(owner, listOf(pc)))))
        timeline.update(6500)
        assertTrue(timeline.focus.value.activeIntro)
        timeline.update(7000)
        assertFalse(timeline.focus.value.activeIntro)
        assertTrue(timeline.state.line(pc).visible.value)
        assertTrue(timeline.focus.value.allIndices.isEmpty())
        timeline.update(10000)
        assertEquals(listOf(0), timeline.focus.value.allIndices)
    }

    @Test fun groupAppearsTogetherAndFocusWaitsForLastBackingToEnd() {
        val a = backing("yeah", 2500, 3500)
        val b = backing("again", 4000, 5500)
        val first = main("Main", 1000, 3000, listOf(a, b))
        val next = main("Next", 5000, 8000)
        val pa = prepared(a); val pb = prepared(b)
        val timeline = LyricsPlaybackTimeline(SyncedLyrics(listOf(first, next)), PreparedLyrics(listOf(prepared(first, listOf(pa, pb)), prepared(next))))
        fun check(time: Int, visible: Boolean, focused: Int) {
            timeline.update(time)
            assertEquals(visible, timeline.state.line(pa).visible.value)
            assertEquals(visible, timeline.state.line(pb).visible.value)
            assertEquals(focused, timeline.focus.value.firstIndex)
        }
        check(500, false, 0)
        check(1000, true, 0)
        check(3600, true, 0) // first backing ended; both remain until the group ends
        check(5200, true, 0) // the next normal line started, but this group's backing is still singing
        check(5499, true, 0)
        check(5500, false, 1)
        check(2000, true, 0) // backwards seeking restores the entire group
        check(7000, false, 1)
    }

    @Test fun backingDoesNotDisappearWhileItsOwnerIsStillSinging() {
        val child = backing("yeah", 1500, 2000)
        val owner = main("Main", 1000, 5000, listOf(child))
        val next = main("Next", 6000, 8000)
        val pc = prepared(child)
        val timeline = LyricsPlaybackTimeline(SyncedLyrics(listOf(owner, next)), PreparedLyrics(listOf(prepared(owner, listOf(pc)), prepared(next))))
        timeline.update(3000)
        assertTrue(timeline.state.line(pc).visible.value)
        assertEquals(0, timeline.focus.value.firstIndex)
        timeline.update(5000)
        assertFalse(timeline.state.line(pc).visible.value)
    }
}
