package com.mocharealm.accompanist.lyrics.ui.internal.layout

import com.mocharealm.accompanist.lyrics.ui.composable.list.LyricsLazyListState
import com.mocharealm.accompanist.lyrics.ui.composable.list.LyricsScrollChain
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class InterludeCollapseTest {
    @Test fun collapsingGapRestartsSettledAnimationForFollowingLines() {
        val chain = LyricsScrollChainState()
        chain.configure(4, LyricsScrollChain(), 0.0, 1000)
        chain.retain(0, 4)
        chain.focusAt(1)
        for (i in 0..3) chain.layoutAt(i, i * 100.0, 0.0)
        assertFalse(chain.active)
        chain.applyContentShift(1, -50.0)
        assertTrue(chain.active)
        assertEquals(0f, chain.offset(1), 0.01f)
        assertEquals(50f, chain.offset(2), 0.01f)
        assertEquals(50f, chain.offset(3), 0.01f)
        repeat(600) { chain.advance(1f / 60f) }
        assertTrue(abs(chain.offset(2)) < 0.1f)
        assertTrue(abs(chain.offset(3)) < 0.1f)
        assertFalse(chain.active)
    }

    @Test fun disabledChainDoesNotLeaveStaticOffsets() {
        val chain = LyricsScrollChainState()
        chain.configure(3, null, 0.0, 1000)
        chain.retain(0, 3)
        chain.applyContentShift(1, -50.0)
        assertEquals(0f, chain.offset(2), 0.01f)
        assertFalse(chain.active)
    }

    @Test fun removedInterludeMarkerSurvivesUntilItsHeightIsMeasured() {
        val state = LyricsLazyListState()
        state.setInterludeItem(1)
        state.setInterludeItem(-1)
        assertEquals(1, state.interludeItemIndex)
        state.finishInterludeMeasure()
        assertEquals(-1, state.interludeItemIndex)
    }
}
